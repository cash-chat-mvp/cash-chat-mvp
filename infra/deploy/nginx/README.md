# Nginx Reverse Proxy + Let's Encrypt (CC-146)

This directory provides deployment assets to configure:
- Nginx reverse proxy (`80`/`443`)
- HTTPS certificate issuance with Let's Encrypt
- Automatic certificate renewal with `certbot.timer`

## Prerequisites

- Ubuntu server (ARM instance) with sudo access
- Backend service running on host-reachable endpoint (default `127.0.0.1:8080`)
- Domain A/AAAA record already pointing to the server public IP
- OCI Security List open for inbound `80` and `443`

## Files

- `setup-nginx-ssl.sh`: Idempotent bootstrap script for Nginx + Certbot

## Quick Start

Run on the server:

```bash
cd /tmp
curl -fsSL https://raw.githubusercontent.com/Jeonj95/cash-chat-mvp/feature/cc-146-nginx-ssl/infra/deploy/nginx/setup-nginx-ssl.sh -o setup-nginx-ssl.sh
chmod +x setup-nginx-ssl.sh
sudo ./setup-nginx-ssl.sh \
  --domain api.example.com \
  --email you@example.com \
  --backend-host 127.0.0.1 \
  --backend-port 8080
```

### Optional flags

- Add SAN for `www`: `--enable-www`
- Test against staging CA first: `--staging`
- Configure HTTP proxy only (no cert yet): `--skip-certbot`

## Verification

```bash
sudo nginx -t
systemctl is-active nginx
systemctl is-enabled certbot.timer
curl -I http://api.example.com
curl -I https://api.example.com
sudo certbot certificates
```

Expected:
- HTTP returns `301/308` redirect to HTTPS
- HTTPS returns `200`/`401` depending on backend auth
- `certbot.timer` is `enabled` and `active`

## Notes

- The script writes `/etc/nginx/sites-available/cash-chat.conf` by default.
- Re-run script safely after backend port/domain changes.
- For rate-limit safety, test first with `--staging`, then run again without it.
