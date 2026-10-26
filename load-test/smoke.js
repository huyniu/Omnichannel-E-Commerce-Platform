// End-to-end functional check of every saga path through the API Gateway (1 virtual user).
//
//   docker run --rm --network backend-network -v "$PWD/load-test:/scripts" grafana/k6:0.53.0 \
//     run -e BASE_URL=http://api-gateway:8080 /scripts/smoke.js
import http from 'k6/http';
import { check, fail, group, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const SKU = 'TSHIRT-BASIC-M-WHT';

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: { checks: ['rate==1.0'] },
};

function req(method, path, body, token, extraHeaders) {
  const headers = Object.assign({ 'Content-Type': 'application/json' }, extraHeaders || {});
  if (token) headers.Authorization = `Bearer ${token}`;
  return http.request(method, `${BASE}${path}`, body === undefined ? null : JSON.stringify(body), { headers });
}

function waitFor(description, fn, timeoutSec = 20) {
  for (let i = 0; i < timeoutSec * 2; i++) {
    const value = fn();
    if (value) return value;
    sleep(0.5);
  }
  fail(`Timed out waiting for: ${description}`);
}

function available(sku) {
  return req('GET', `/api/inventory/${sku}`).json('data.available');
}

function placeOrder(token, paymentMethod, quantity = 1) {
  const res = req('POST', '/api/orders', {
    items: [{ sku: SKU, quantity }],
    paymentMethod,
    shipping: { receiverName: 'Nguyen Van A', phone: '0901234567', address: '12 Le Loi, Q1, TP.HCM' },
  }, token);
  check(res, { [`place ${paymentMethod} order -> 201`]: (r) => r.status === 201 });
  return res.json('data');
}

function orderStatus(token, id) {
  return req('GET', `/api/orders/${id}`, undefined, token).json('data.status');
}

export default function () {
  const email = `smoke-${Date.now()}@test.vn`;
  let token;
  let adminToken;

  group('identity', () => {
    const reg = req('POST', '/api/auth/register', { email, password: 'Passw0rd!', fullName: 'Smoke Tester', phone: '0901234567' });
    check(reg, { 'register -> 201': (r) => r.status === 201 });
    const login = req('POST', '/api/auth/login', { email, password: 'Passw0rd!' });
    check(login, { 'login -> 200': (r) => r.status === 200 });
    token = login.json('data.accessToken');
    const refreshed = req('POST', '/api/auth/refresh', { refreshToken: login.json('data.refreshToken') });
    check(refreshed, { 'refresh rotates token -> 200': (r) => r.status === 200 });
    check(req('GET', '/api/users/me'), { 'no token -> 401': (r) => r.status === 401 });
    check(req('GET', '/api/users/me', undefined, token), { 'me -> 200': (r) => r.json('data.email') === email });
    check(req('POST', '/api/users/me/addresses', {
      receiverName: 'Nguyen Van A', phone: '0901234567', line: '12 Le Loi', district: 'Q1', city: 'TP.HCM', isDefault: true,
    }, token), { 'add address -> 201': (r) => r.status === 201 });
    check(req('GET', '/api/users', undefined, token), { 'customer cannot list users -> 403': (r) => r.status === 403 });
    adminToken = req('POST', '/api/auth/login', { email: 'admin@omni.local', password: __ENV.ADMIN_PASSWORD || 'Admin@123' })
      .json('data.accessToken');
  });

  group('catalog', () => {
    check(req('GET', '/api/products?q=iphone'), { 'search iphone finds results': (r) => r.json('data.totalElements') > 0 });
    check(req('GET', '/api/products?q=ao%20thun'), { 'accent-insensitive search': (r) => r.json('data.totalElements') > 0 });
    check(req('GET', `/api/products/sku/${SKU}`), { 'product by sku -> 200': (r) => r.status === 200 });
  });

  group('online payment succeeds -> CONFIRMED', () => {
    const before = available(SKU);
    const order = placeOrder(token, 'MOCK');
    check(order, { 'new order is PENDING': (o) => o.status === 'PENDING' });
    check(null, { 'stock reserved': () => available(SKU) === before - 1 });

    const checkout = waitFor('payment opened', () => {
      const r = req('POST', `/api/payments/orders/${order.id}/checkout`, undefined, token);
      return r.status === 200 ? r : null;
    });
    check(checkout, { 'checkout returns payment url': (r) => !!r.json('data.paymentUrl') });
    const pay = req('POST', `/api/payments/mock/${order.code}/simulate?result=SUCCESS`, undefined, token);
    check(pay, { 'gateway webhook applied': (r) => r.json('data.outcome') === 'APPLIED' });
    waitFor('order confirmed', () => orderStatus(token, order.id) === 'CONFIRMED');
    check(null, { 'order CONFIRMED after payment': () => true });

    check(req('PATCH', `/api/orders/${order.id}/status`, { status: 'SHIPPING' }, adminToken), { 'staff ships -> 200': (r) => r.status === 200 });
    check(req('PATCH', `/api/orders/${order.id}/status`, { status: 'COMPLETED' }, adminToken), { 'staff completes -> 200': (r) => r.status === 200 });
    check(req('PATCH', `/api/orders/${order.id}/status`, { status: 'SHIPPING' }, adminToken), { 'invalid transition -> 409': (r) => r.status === 409 });
  });

  group('payment fails -> compensation releases stock', () => {
    const before = available(SKU);
    const order = placeOrder(token, 'MOCK');
    waitFor('payment opened', () => req('GET', `/api/payments/orders/${order.id}`, undefined, token).status === 200);
    req('POST', `/api/payments/mock/${order.code}/simulate?result=FAILED`, undefined, token);
    waitFor('order cancelled', () => orderStatus(token, order.id) === 'CANCELLED');
    check(null, { 'stock restored after failed payment': () => available(SKU) === before });
  });

  group('cancel a paid order -> refund', () => {
    const order = placeOrder(token, 'MOCK');
    waitFor('payment opened', () => req('GET', `/api/payments/orders/${order.id}`, undefined, token).status === 200);
    req('POST', `/api/payments/mock/${order.code}/simulate?result=SUCCESS`, undefined, token);
    waitFor('order confirmed', () => orderStatus(token, order.id) === 'CONFIRMED');
    check(req('POST', `/api/orders/${order.id}/cancel`, { reason: 'Changed my mind' }, token), { 'cancel -> 200': (r) => r.status === 200 });
    waitFor('payment refunded', () => req('GET', `/api/payments/orders/${order.id}`, undefined, token).json('data.status') === 'REFUNDED');
    check(null, { 'payment REFUNDED': () => true });
  });

  group('cash on delivery is confirmed immediately', () => {
    const order = placeOrder(token, 'COD');
    check(order, { 'COD order CONFIRMED': (o) => o.status === 'CONFIRMED' });
  });

  group('idempotency', () => {
    const body = { items: [{ sku: SKU, quantity: 1 }], paymentMethod: 'COD',
      shipping: { receiverName: 'A', phone: '0901234567', address: 'x' } };
    const key = `smoke-${Date.now()}`;
    const a = req('POST', '/api/orders', body, token, { 'Idempotency-Key': key });
    const b = req('POST', '/api/orders', body, token, { 'Idempotency-Key': key });
    check(null, { 'same key -> same order': () => a.json('data.id') === b.json('data.id') });
  });

  group('notifications (async via Kafka)', () => {
    waitFor('in-app notifications', () => req('GET', '/api/notifications/me', undefined, token).json('data.totalElements') >= 5);
    check(null, { 'customer received notifications': () => true });
  });
}
