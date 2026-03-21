#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage:
  sudo ./setup-nginx-ssl.sh \
    --domain api.example.com \
    --email devops@example.com \
    [--backend-host host.docker.internal] \
    [--backend-port 8080] \
    [--enable-www] \
    [--staging] \
    [--skip-certbot] \
    [--force-renewal]

Options:
  --domain         Primary domain for backend API (required)
  --email          Email for Let's Encrypt notices (required unless --skip-certbot)
  --backend-host   Upstream backend host. Default: host.docker.internal
  --backend-port   Upstream backend port. Default: 8080
  --enable-www     Also request cert for www.<domain>
  --staging        Use Let's Encrypt staging CA while testing
  --skip-certbot   Configure HTTP reverse proxy only
  --force-renewal  Force certbot to reissue/renew certificate
  -h, --help       Show help
USAGE
}

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run as root (or with sudo)." >&2
  exit 1
fi

DOMAIN=""
EMAIL=""
BACKEND_HOST="host.docker.internal"
BACKEND_PORT="8080"
ENABLE_WWW=0
STAGING=0
SKIP_CERTBOT=0
FORCE_RENEWAL=0

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
    --force-renewal)
      FORCE_RENEWAL=1
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

if ! command -v docker >/dev/null 2>&1; then
  echo "docker command not found. Install Docker first." >&2
  exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "docker compose command not found. Install Docker Compose plugin first." >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="${SCRIPT_DIR}/docker-compose.yml"
HTTP_TEMPLATE="${SCRIPT_DIR}/nginx/conf.d/http.conf.template"
HTTPS_TEMPLATE="${SCRIPT_DIR}/nginx/conf.d/https.conf.template"
ACTIVE_CONF="${SCRIPT_DIR}/nginx/conf.d/default.conf"
LETSENCRYPT_DIR="${SCRIPT_DIR}/letsencrypt"
WEBROOT_DIR="${SCRIPT_DIR}/certbot-www"

install -d -m 0755 "${SCRIPT_DIR}/nginx/conf.d" "${LETSENCRYPT_DIR}" "${WEBROOT_DIR}"

BACKEND_UPSTREAM="http://${BACKEND_HOST}:${BACKEND_PORT}"
SERVER_NAMES="${DOMAIN}"
if [[ "${ENABLE_WWW}" -eq 1 ]]; then
  SERVER_NAMES="${SERVER_NAMES} www.${DOMAIN}"
fi

render_template() {
  local src="$1"
  local dst="$2"

  sed \
    -e "s|__SERVER_NAMES__|${SERVER_NAMES}|g" \
    -e "s|__BACKEND_UPSTREAM__|${BACKEND_UPSTREAM}|g" \
    -e "s|__CERT_NAME__|${DOMAIN}|g" \
    "${src}" > "${dst}"
}

echo "==> Preparing Nginx HTTP config (ACME challenge + reverse proxy)"
render_template "${HTTP_TEMPLATE}" "${ACTIVE_CONF}"

echo "==> Starting Nginx container"
docker compose -f "${COMPOSE_FILE}" up -d nginx

if [[ "${SKIP_CERTBOT}" -eq 1 ]]; then
  echo "==> --skip-certbot set. HTTP reverse proxy configured."
  echo "Done."
  exit 0
fi

echo "==> Issuing/Renewing Let's Encrypt certificate"
CERTBOT_ARGS=(
  certonly
  --webroot
  -w /var/www/certbot
  --non-interactive
  --agree-tos
  --email "${EMAIL}"
  --cert-name "${DOMAIN}"
  -d "${DOMAIN}"
)

if [[ "${ENABLE_WWW}" -eq 1 ]]; then
  CERTBOT_ARGS+=(-d "www.${DOMAIN}")
fi

if [[ "${STAGING}" -eq 1 ]]; then
  CERTBOT_ARGS+=(--staging)
fi

if [[ "${FORCE_RENEWAL}" -eq 1 ]]; then
  CERTBOT_ARGS+=(--force-renewal)
fi

if [[ -f "${LETSENCRYPT_DIR}/live/${DOMAIN}/fullchain.pem" && "${FORCE_RENEWAL}" -eq 0 ]]; then
  echo "==> Existing certificate found. Skipping initial issue (use --force-renewal to reissue)."
else
  docker compose -f "${COMPOSE_FILE}" run --rm certbot "${CERTBOT_ARGS[@]}"
fi

echo "==> Switching Nginx config to HTTPS mode"
render_template "${HTTPS_TEMPLATE}" "${ACTIVE_CONF}"
docker compose -f "${COMPOSE_FILE}" up -d nginx
docker compose -f "${COMPOSE_FILE}" exec -T nginx nginx -t
docker compose -f "${COMPOSE_FILE}" exec -T nginx nginx -s reload

echo "==> Starting background auto-renew container"
docker compose -f "${COMPOSE_FILE}" up -d certbot
docker compose -f "${COMPOSE_FILE}" run --rm certbot renew --dry-run

echo "==> Current container status"
docker compose -f "${COMPOSE_FILE}" ps
echo "Done."
