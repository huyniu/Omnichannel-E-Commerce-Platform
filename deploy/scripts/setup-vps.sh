#!/usr/bin/env bash
# One-time preparation of a fresh Ubuntu 22.04 VPS (run as root or with sudo).
#   curl -fsSL https://raw.githubusercontent.com/<you>/<repo>/main/deploy/scripts/setup-vps.sh | sudo DOMAIN=api.example.com DEPLOY_USER=deploy bash
set -euo pipefail

DOMAIN="${DOMAIN:?set DOMAIN, e.g. api.example.com}"
DEPLOY_USER="${DEPLOY_USER:-deploy}"
APP_DIR="${APP_DIR:-/opt/omni}"

echo "==> System update"
apt-get update -y && apt-get upgrade -y
apt-get install -y ca-certificates curl gnupg ufw nginx certbot python3-certbot-nginx fail2ban

echo "==> Docker Engine + Compose plugin (official repository)"
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
chmod a+r /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  > /etc/apt/sources.list.d/docker.list
apt-get update -y
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
systemctl enable --now docker

echo "==> Deploy user '$DEPLOY_USER' (used by GitHub Actions over SSH)"
id "$DEPLOY_USER" >/dev/null 2>&1 || adduser --disabled-password --gecos "" "$DEPLOY_USER"
usermod -aG docker "$DEPLOY_USER"
mkdir -p "$APP_DIR" && chown "$DEPLOY_USER":"$DEPLOY_USER" "$APP_DIR"

echo "==> Firewall: only SSH, HTTP, HTTPS are reachable from the Internet"
ufw default deny incoming
ufw default allow outgoing
ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable
# Note: PostgreSQL 5432, MongoDB 27017, Redis 6379, Kafka 9092 are never published by
# docker-compose.production.yml, so they are unreachable even though Docker bypasses UFW.

echo "==> Kernel setting required by Elasticsearch"
sysctl -w vm.max_map_count=262144
grep -q vm.max_map_count /etc/sysctl.conf || echo "vm.max_map_count=262144" >> /etc/sysctl.conf

echo "==> Nginx reverse proxy for $DOMAIN"
sed "s/api.example.com/$DOMAIN/g" "$(dirname "$0")/../nginx/omni-api.conf" > /etc/nginx/sites-available/omni-api.conf 2>/dev/null \
  || echo "Copy deploy/nginx/omni-api.conf to /etc/nginx/sites-available/ and set server_name manually"
ln -sf /etc/nginx/sites-available/omni-api.conf /etc/nginx/sites-enabled/omni-api.conf
rm -f /etc/nginx/sites-enabled/default
nginx -t && systemctl reload nginx

cat <<MSG

Done. Next steps:
  1. Point the DNS A record of $DOMAIN to this server's IP.
  2. sudo certbot --nginx -d $DOMAIN --redirect -m you@example.com --agree-tos
  3. Put the production .env into $APP_DIR (see .env.example) and push to main to deploy.
MSG
