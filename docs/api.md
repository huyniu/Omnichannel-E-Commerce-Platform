# API (qua Gateway `http://localhost:8080`)

Tài liệu OpenAPI đầy đủ (thử trực tiếp được): **http://localhost:8080/swagger-ui.html**. 🔓 = không cần token, 🛠 = STAFF/ADMIN, 👑 = ADMIN.

## Identity
| Method | Path | |
|---|---|---|
| POST | `/api/auth/register` 🔓 | `{email, password, fullName, phone?}` → token pair |
| POST | `/api/auth/login` 🔓 | `{email, password}` |
| POST | `/api/auth/refresh` 🔓 | `{refreshToken}` → cặp token mới (rotation) |
| POST | `/api/auth/logout` 🔓 | `{refreshToken}` |
| GET / PUT | `/api/users/me` | Hồ sơ |
| PUT | `/api/users/me/password` | Đổi mật khẩu (thu hồi mọi phiên) |
| GET / POST | `/api/users/me/addresses` | Sổ địa chỉ |
| PUT / DELETE | `/api/users/me/addresses/{id}` | |
| GET | `/api/users?page=&size=` 👑 | Danh sách user |
| PUT | `/api/users/{id}/roles` 👑 | `{roles:["STAFF","CUSTOMER"]}` |
| PUT | `/api/users/{id}/status` 👑 | `{status:"LOCKED"}` |

## Product Catalog
| Method | Path | |
|---|---|---|
| GET | `/api/products?q=&categoryId=&brand=&minPrice=&maxPrice=&page=&size=` 🔓 | Tìm kiếm full-text (Elasticsearch, không dấu, fuzzy) |
| GET | `/api/products/{id-or-slug}` 🔓 | Chi tiết |
| GET | `/api/products/sku/{sku}` 🔓 | |
| POST / PUT / DELETE | `/api/products[/{id}]` 🛠 | Tạo / sửa / ngừng bán |
| POST | `/api/products/reindex` 👑 | Dựng lại index ES từ MongoDB |
| GET / POST | `/api/categories` 🔓 / 🛠 | |

## Order & Inventory
| Method | Path | |
|---|---|---|
| POST | `/api/orders` | Header `Idempotency-Key` (khuyến nghị). Body `{items:[{sku,quantity}], paymentMethod: COD\|MOCK\|VNPAY, shipping:{receiverName,phone,address}, note?}` |
| GET | `/api/orders` | Đơn của tôi |
| GET | `/api/orders/{id}` | Chi tiết + lịch sử trạng thái |
| POST | `/api/orders/{id}/cancel` | Hủy khi `PENDING`/`CONFIRMED` |
| GET | `/api/orders/admin?status=` 🛠 | Tất cả đơn |
| PATCH | `/api/orders/{id}/status` 🛠 | `{status: SHIPPING\|COMPLETED}` |
| GET | `/api/inventory?skus=A,B` 🔓, `/api/inventory/{sku}` 🔓 | Tồn kho |
| POST | `/api/inventory/{sku}/adjust` 🛠 | `{delta: 50}` nhập thêm / `{delta: -3}` xuất hủy |

Mã lỗi đáng chú ý: `OUT_OF_STOCK` (409), `INVENTORY_BUSY` (429), `INVALID_SKU` (400), `QUANTITY_LIMIT` (400), `PRODUCT_SERVICE_UNAVAILABLE` (503), `INVALID_STATUS_TRANSITION` (409).

## Payment
| Method | Path | |
|---|---|---|
| GET | `/api/payments/orders/{orderId}` | Trạng thái thanh toán |
| POST | `/api/payments/orders/{orderId}/checkout` | Trả `paymentUrl` (VNPay sandbox hoặc mock) |
| POST | `/api/payments/mock/{orderCode}/simulate?result=SUCCESS\|FAILED` | Giả lập khách thanh toán trên cổng mock (tạo webhook có chữ ký) |
| POST | `/api/payments/webhook/mock` 🔓 | Webhook cổng mock, chữ ký HMAC-SHA256 |
| GET | `/api/payments/vnpay/ipn` 🔓 | IPN của VNPay (chữ ký HMAC-SHA512), trả `{RspCode, Message}` |
| GET | `/api/payments/vnpay/return` 🔓 | Trang trả về sau khi thanh toán |
| GET | `/api/payments/admin/reconciliation?from=2026-10-01&to=2026-10-31` 🛠 | Đối soát: số lượng, tổng tiền theo phương thức/trạng thái |
| GET | `/api/payments/admin/{paymentId}/transactions` 🛠 | Nhật ký giao dịch với cổng |

## Notification
| Method | Path | |
|---|---|---|
| GET | `/api/notifications/me` | Hộp thư in-app |
| POST | `/api/notifications/{id}/read` | |
| GET | `/api/notifications/admin` 🛠 | Nhật ký gửi tất cả kênh |

## Ví dụ nhanh với curl

```bash
TOKEN=$(curl -s localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"a@test.vn","password":"Passw0rd!","fullName":"Nguyen Van A"}' | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')

curl -s "localhost:8080/api/products?q=iphone"

curl -s localhost:8080/api/orders -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: demo-1' \
  -d '{"items":[{"sku":"IP15-128-BLK","quantity":1}],"paymentMethod":"MOCK","shipping":{"receiverName":"A","phone":"0901234567","address":"12 Le Loi, Q1"}}'
```
