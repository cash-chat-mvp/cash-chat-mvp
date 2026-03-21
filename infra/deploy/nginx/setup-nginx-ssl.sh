#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage:
  sudo ./setup-nginx-ssl.sh \
    --domain api.example.com \
    --email devops@example.com \
    [--backend-host 127.0.0.1] \
    [--backend-port 8080] \
    [--site-name cash-chat] \
    [--enable-www] \
    [--staging] \
    [--skip-certbot]

Options:
  --domain        Primary domain for backend API (required).
  --email         Email for Let's Encrypt expiry notices (required unless --skip-certbot).
  --backend-host  Upstream backend host. Default: 127.0.0.1
  --backend-port  Upstream backend port. Default: 8080
  --site-name     Nginx site config file name. Default: cash-chat
  --enable-www    Also request cert for www.<domain>.
  --staging       Use Let's Encrypt staging CA to avoid rate limits while testing.
  --skip-certbot  Configure Nginx only (HTTP), skip certificate issuance.
  -h, --help      Show help.
USAGE
}

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run as root (or with sudo)." >&2
  exit 1
fi

DOMAIN=""
EMAIL=""
BACKEND_HOST="127.0.0.1"
BACKEND_PORT="8080"
SITE_NAME="cash-chat"
ENABLE_WWW=0
STAGING=0
SKIP_CERTBOT=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --domain)
      DOMAIN="${2:-}"
      shift 2
      ;;
    --email)
      EMAIL="${2:-}"
      shift 2
      ;;
    --backend-host)
      BACKEND_HOST="${2:-}"
      shift 2
      ;;
    --backend-port)
      BACKEND_PORT="${2:-}"
      shift 2
      ;;
    --site-name)
      SITE_NAME="${2:-}"
      shift 2
      ;;
    --enable-www)
      ENABLE_WWW=1
      shift 1
      ;;
    --staging)
      STAGING=1
      shift 1
      ;;
    --skip-certbot)
      SKIP_CERTBOT=1
      shift 1
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      exit 1
      ;;
  esac
done

if [[ -z "${DOMAIN}" ]]; then
  echo "--domain is required." >&2
  exit 1
fi

if [[ "${SKIP_CERTBOT}" -eq 0 && -z "${EMAIL}" ]]; then
  echo "--email is required unless --skip-certbot is set." >&2
  exit 1
fi

echo "==> Installing packages (nginx, certbot, python3-certbot-nginx)"
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y nginx certbot python3-certbot-nginx

echo "==> Writing Nginx reverse proxy config"
SERVER_NAMES="${DOMAIN}"
if [[ "${ENABLE_WWW}" -eq 1 ]]; then
  SERVER_NAMES="${SERVER_NAMES} www.${DOMAIN}"
fi

SITE_FILE="/etc/nginx/sites-available/${SITE_NAME}.conf"
cat > "${SITE_FILE}" <<EOF
server {
    listen 80;
    listen [::]:80;
    server_name ${SERVER_NAMES};

    client_max_body_size 20m;

    location / {
        proxy_pass http://${BACKEND_HOST}:${BACKEND_PORT};
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 300s;
    }
}
EOF

ln -sfn "${SITE_FILE}" "/etc/nginx/sites-enabled/${SITE_NAME}.conf"
if [[ -L /etc/nginx/sites-enabled/default ]]; then
  rm -f /etc/nginx/sites-enabled/default
fi

nginx -t
systemctl enable --now nginx
systemctl reload nginx

if [[ "${SKIP_CERTBOT}" -eq 1 ]]; then
  echo "==> --skip-certbot set. Nginx HTTP reverse proxy configured only."
  echo "Done."
  exit 0
fi

echo "==> Requesting/renewing Let's Encrypt certificate"
CERTBOT_ARGS=(
  --nginx
  --non-interactive
  --agree-tos
  --email "${EMAIL}"
  --keep-until-expiring
  --redirect
  -d "${DOMAIN}"
)

if [[ "${ENABLE_WWW}" -eq 1 ]]; then
  CERTBOT_ARGS+=(-d "www.${DOMAIN}")
fi

if [[ "${STAGING}" -eq 1 ]]; then
  CERTBOT_ARGS+=(--staging)
fi

certbot "${CERTBOT_ARGS[@]}"

echo "==> Ensuring auto-renewal hook (nginx reload)"
install -d -m 0755 /etc/letsencrypt/renewal-hooks/deploy
cat > /etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
systemctl reload nginx
EOF
chmod 0755 /etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh

echo "==> Enabling certbot timer"
systemctl enable --now certbot.timer

echo "==> Validating certificate auto-renewal (dry run)"
certbot renew --dry-run

echo "Done."
