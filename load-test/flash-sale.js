// Flash-sale load test: hundreds of buyers hit the same SKU at 500-1000 req/s.
// Goal: prove the system stays up AND never oversells.
//
//   docker run --rm --network backend-network -v "$PWD/load-test:/scripts" grafana/k6:0.53.0 \
//     run -e BASE_URL=http://api-gateway:8080 -e RATE=1000 /scripts/flash-sale.js
//
// Env: BASE_URL, SKU (FLASH-001), STOCK (100), USERS (200), RATE (500 req/s), DURATION (30s),
//      ADMIN_EMAIL / ADMIN_PASSWORD (to reset the stock before the run).
import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const SKU = __ENV.SKU || 'FLASH-001';
const STOCK = parseInt(__ENV.STOCK || '100', 10);
const USERS = parseInt(__ENV.USERS || '200', 10);
const RATE = parseInt(__ENV.RATE || '500', 10);
const DURATION = __ENV.DURATION || '30s';
const JSON_HEADERS = { 'Content-Type': 'application/json' };

const ordersCreated = new Counter('orders_created');
const ordersSoldOut = new Counter('orders_sold_out');
const ordersBusy = new Counter('orders_busy_429');
const ordersUnexpected = new Counter('orders_unexpected');

export const options = {
  setupTimeout: '600s',
  scenarios: {
    flash_sale: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: 300,
      maxVUs: 1500,
    },
  },
  thresholds: {
    // 201 created, 409 sold out and 429 busy are all *correct* answers under a flash sale.
    checks: ['rate>0.99'],
    http_req_duration: ['p(95)<2000'],
    orders_unexpected: ['count<10'],
  },
};

http.setResponseCallback(http.expectedStatuses(200, 201, 409, 429));

function post(path, body, token) {
  const headers = Object.assign({}, JSON_HEADERS, token ? { Authorization: `Bearer ${token}` } : {});
  return http.post(`${BASE}${path}`, JSON.stringify(body), { headers });
}

function stock() {
  const res = http.get(`${BASE}/api/inventory/${SKU}`);
  return res.json('data');
}

export function setup() {
  // 1. Reset the flash-sale stock to exactly STOCK available units.
  const admin = post('/api/auth/login', {
    email: __ENV.ADMIN_EMAIL || 'admin@omni.local',
    password: __ENV.ADMIN_PASSWORD || 'Admin@123',
  });
  check(admin, { 'admin login': (r) => r.status === 200 });
  const adminToken = admin.json('data.accessToken');
  const current = stock();
  const delta = STOCK - current.available;
  if (delta !== 0) {
    post(`/api/inventory/${SKU}/adjust`, { delta }, adminToken);
  }

  // 2. Register the buyers (in small batches, backing off when the anonymous rate limit answers 429).
  const runId = Date.now();
  const tokens = [];
  for (let i = 0; i < USERS; i += 10) {
    const batch = [];
    for (let j = i; j < Math.min(i + 10, USERS); j++) {
      batch.push(['POST', `${BASE}/api/auth/register`, JSON.stringify({
        email: `k6-${runId}-${j}@load.test`, password: 'Passw0rd!', fullName: `Buyer ${j}`,
      }), { headers: JSON_HEADERS }]);
    }
    for (const res of http.batch(batch)) {
      if (res.status === 201) {
        tokens.push(res.json('data.accessToken'));
      }
    }
    sleep(0.3);
  }
  console.log(`Registered ${tokens.length} buyers`);

  const before = stock();
  console.log(`Stock before: ${JSON.stringify(before)}`);
  return { tokens, before };
}

export default function (data) {
  const token = data.tokens[exec.scenario.iterationInTest % data.tokens.length];
  const res = http.post(`${BASE}/api/orders`, JSON.stringify({
    items: [{ sku: SKU, quantity: 1 }],
    paymentMethod: 'MOCK',
    shipping: { receiverName: 'K6 Buyer', phone: '0900000000', address: '1 Load Test St, HCMC' },
  }), {
    headers: Object.assign({}, JSON_HEADERS, {
      Authorization: `Bearer ${token}`,
      'Idempotency-Key': `k6-${exec.scenario.iterationInTest}-${Date.now()}`,
    }),
    tags: { name: 'POST /api/orders' },
  });

  if (res.status === 201) ordersCreated.add(1);
  else if (res.status === 409) ordersSoldOut.add(1);
  else if (res.status === 429) ordersBusy.add(1);
  else ordersUnexpected.add(1);

  check(res, { 'order accepted, sold out or throttled': (r) => [201, 409, 429].includes(r.status) });
}

export function teardown(data) {
  const after = stock();
  const before = data.before;
  const taken = (after.reserved + after.sold) - (before.reserved + before.sold);
  const totalBefore = before.available + before.reserved + before.sold;
  const totalAfter = after.available + after.reserved + after.sold;

  console.log(`Stock after:  ${JSON.stringify(after)}`);
  console.log(`Units taken by orders during the test: ${taken} (must equal the orders_created counter)`);
  check(after, {
    'no negative stock': (s) => s.available >= 0,
    'no oversell (taken <= stock offered)': () => taken <= before.available,
    'units conserved (available+reserved+sold unchanged)': () => totalAfter === totalBefore,
  });
}
