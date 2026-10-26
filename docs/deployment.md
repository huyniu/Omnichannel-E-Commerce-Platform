# Triển khai lên VPS

Cấu hình đề xuất: Ubuntu 22.04 LTS, 4 vCPU, 8 GB RAM, SSD 50 GB. Toàn bộ stack (kể cả monitoring) dùng khoảng 5–6 GB RAM.

## Step 1 – Chuẩn bị VPS

Copy thư mục `deploy/` lên VPS rồi chạy (thay domain):

```bash
sudo DOMAIN=api.example.com DEPLOY_USER=deploy bash deploy/scripts/setup-vps.sh
```

Script cài Docker Engine + Compose plugin, Nginx, Certbot, fail2ban; tạo user `deploy` thuộc nhóm docker; bật UFW chỉ mở `22, 80, 443`; đặt `vm.max_map_count` cho Elasticsearch; cài site Nginx từ `deploy/nginx/omni-api.conf`.

> Docker tự thêm luật iptables và **bỏ qua UFW** với các port được publish. Vì vậy `docker-compose.production.yml` không publish bất kỳ port DB/broker nào (5432, 27017, 6379, 9092, 9200), và chỉ publish gateway ở `127.0.0.1:8080` để chỉ Nginx trên host gọi được. Các UI monitoring cũng chỉ bind `127.0.0.1`; truy cập bằng SSH tunnel: `ssh -L 3000:localhost:3000 deploy@vps`.

## Step 2 – DNS và HTTPS

1. Tạo bản ghi A `api.example.com` → IP VPS.
2. Cấp chứng chỉ Let's Encrypt (Certbot tự sửa cấu hình Nginx và đặt lịch gia hạn):

```bash
sudo certbot --nginx -d api.example.com --redirect -m you@example.com --agree-tos
```

## Step 3 – Cấu hình bí mật

Trên VPS tạo `/opt/omni/.env` từ `.env.example`, đổi **toàn bộ** mật khẩu, `JWT_SECRET` (`openssl rand -base64 48`), `PUBLIC_BASE_URL=https://api.example.com`, SMTP thật, thông tin VNPay sandbox. File này không bao giờ commit.

## Step 4 – CI/CD (GitHub Actions)

- `ci.yml`: mỗi PR / nhánh khác `main` → `mvn verify`.
- `deploy.yml`: push `main` → test → build 6 image song song (cache GHA) → push `ghcr.io/<owner>/omni-<service>:<sha>` và `:latest` → SCP các file compose + `deploy/` lên VPS → SSH chạy `docker compose pull && docker compose up -d --remove-orphans`.

Secrets cần tạo trong repo (Settings → Secrets and variables → Actions): `VPS_HOST`, `VPS_USER` (`deploy`), `VPS_SSH_KEY` (private key; public key thêm vào `~deploy/.ssh/authorized_keys`), tùy chọn `VPS_PORT`. Nên tạo Environment `production` để bật duyệt thủ công trước khi deploy.

Chạy tay (không qua CI):

```bash
cd /opt/omni && docker compose -f docker-compose.production.yml -f docker-compose.monitoring.yml --env-file .env up -d
```

## Step 5 – Kiểm tra sau triển khai

```bash
curl -s https://api.example.com/actuator/health
```

```bash
docker compose -f docker-compose.production.yml ps
```

```bash
sudo ufw status verbose
```

Từ một máy bên ngoài, xác nhận các port DB bị đóng: `nc -zv <ip> 5432` phải thất bại.

## Step 6 – Monitoring

`docker-compose.monitoring.yml` thêm Prometheus (metrics), Loki + Promtail (log tập trung), Jaeger (distributed tracing, OTLP) và Grafana (đã provision sẵn datasource + dashboard). Trên production đặt `TRACING_SAMPLING=0.1` để chỉ lấy mẫu 10% trace.

## Sao lưu

```bash
docker compose -f docker-compose.production.yml exec -T postgres pg_dumpall -U postgres | gzip > backup-$(date +%F).sql.gz
```

```bash
docker compose -f docker-compose.production.yml exec -T mongo mongodump --archive --gzip > mongo-$(date +%F).archive.gz
```
