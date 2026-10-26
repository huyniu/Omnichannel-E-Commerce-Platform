# Kịch bản demo

Chuẩn bị: `docker compose -f docker-compose.yml -f docker-compose.monitoring.yml up -d --build`, đợi `docker compose ps` báo các service `healthy`.
Trên VPS, thay `BASE_URL=http://api-gateway:8080` bằng domain HTTPS nếu chạy k6 từ máy khác.

## 1. Luồng nghiệp vụ end-to-end

```bash
docker run --rm --network backend-network -v "$PWD/load-test:/scripts" grafana/k6:0.53.0 run -e BASE_URL=http://api-gateway:8080 /scripts/smoke.js
```

Script kiểm tra: đăng ký/đăng nhập/refresh, RBAC (403), tìm kiếm có dấu/không dấu, đặt hàng → thanh toán thành công → CONFIRMED → SHIPPING → COMPLETED, thanh toán thất bại → hủy + trả kho, hủy đơn đã trả tiền → hoàn tiền, COD, Idempotency-Key, thông báo in-app. Mở http://localhost:8025 để xem email.

## 2. Flash sale: 500–1000 req/s, không sai lệch kho

```bash
docker run --rm --network backend-network -v "$PWD/load-test:/scripts" grafana/k6:0.53.0 run -e BASE_URL=http://api-gateway:8080 -e RATE=1000 -e DURATION=30s -e STOCK=100 /scripts/flash-sale.js
```

- `setup` đặt lại `FLASH-001` còn đúng 100 cái và đăng ký 200 người mua.
- Mỗi giây 1000 request `POST /api/orders` cho cùng một SKU.
- Kết quả mong đợi trong phần tóm tắt của k6:
  - `orders_created` = **100** (đúng bằng số hàng), phần còn lại là `orders_sold_out` (409) hoặc `orders_busy_429`.
  - `checks` 100%: `no negative stock`, `no oversell`, `units conserved`.
- Đối chiếu thêm trực tiếp trong DB:

```bash
docker compose exec postgres psql -U postgres -d order_db -c "SELECT sku, available, reserved, sold FROM inventory WHERE sku='FLASH-001';"
```

```bash
docker compose exec postgres psql -U postgres -d order_db -c "SELECT count(*) AS orders, sum(quantity) AS units FROM stock_reservations WHERE sku='FLASH-001' AND status IN ('RESERVED','COMMITTED');"
```

- Trên Grafana (dashboard *Omnichannel - Overview*): req/s, p95, số đơn tạo/bị từ chối, outbox pending.

Để so sánh, có thể tắt Redis lock bằng cách dừng Redis (`docker compose stop redis`): hệ thống chuyển sang khóa dòng PostgreSQL, vẫn **không bán âm**, nhưng p95 tăng (gateway cũng mất rate limiter nên chỉ dùng cho thí nghiệm).

## 3. Tính sẵn sàng: tắt Notification vẫn đặt hàng được

```bash
docker compose stop notification-service
```

Chạy lại `smoke.js` phần đặt hàng (hoặc đặt hàng qua Swagger): đơn vẫn được tạo, thanh toán, xác nhận. Kafka giữ các event chưa xử lý.

```bash
docker compose start notification-service
```

Sau vài giây, các email bị "nợ" xuất hiện trong Mailpit; không email nào bị gửi trùng (unique index `eventId + channel`).

Các thí nghiệm khác:

| Lệnh | Quan sát |
|---|---|
| `docker compose stop elasticsearch` | `GET /api/products?q=...` vẫn trả kết quả (fallback MongoDB) |
| `docker compose stop kafka` | Đặt hàng vẫn 201; `outbox_pending` tăng; bật lại Kafka → event được gửi hết |
| `docker compose stop payment-service` | Đặt hàng vẫn 201 (PENDING); bật lại → Payment xử lý `OrderCreated` còn tồn |

## 4. Tracing một request

Mở Jaeger http://localhost:16686, chọn service `api-gateway`, xem trace của `POST /api/orders`: Gateway → order-service → gRPC `ProductCatalog/GetSkus` → product-service → MongoDB.
