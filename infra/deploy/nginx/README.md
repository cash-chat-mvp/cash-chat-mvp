# Nginx Reverse Proxy + Let's Encrypt (Docker) (CC-146)

This directory provides Docker-based assets to configure:
- Nginx reverse proxy (`80`/`443`)
- HTTPS certificate issuance with Let's Encrypt (`certbot`)
- Automatic certificate renewal in a long-running `certbot` container

## Prerequisites

- Ubuntu server (ARM instance) with sudo access
- Docker + Docker Compose plugin installed
- Backend reachable from Docker Nginx container
  - Default upstream: `http://host.docker.internal:8080`
- Domain A/AAAA record already pointing to server public IP
- OCI Security List open for inbound `80` and `443`

## Files

- `docker-compose.yml`: Nginx + Certbot services
- `setup-nginx-ssl.sh`: bootstrap script (render config, issue cert, start renew loop)
- `nginx/conf.d/http.conf.template`: HTTP bootstrap template
- `nginx/conf.d/https.conf.template`: HTTPS reverse proxy template

## Quick Start

Run on the server (from this repository path):

```bash
cd /path/to/cash-chat-mvp/infra/deploy/nginx
chmod +x setup-nginx-ssl.sh
sudo ./setup-nginx-ssl.sh \
  --domain api.example.com \
  --email you@example.com \
  --backend-host host.docker.internal \
  --backend-port 8080
```

### Optional flags

- Add SAN for `www`: `--enable-www`
- Test with staging CA first: `--staging`
- Configure HTTP only: `--skip-certbot`
- Force reissue certificate: `--force-renewal`

## Verification

```bash
cd /path/to/cash-chat-mvp/infra/deploy/nginx
docker compose ps
docker compose logs --tail=100 nginx
docker compose logs --tail=100 certbot
curl -I http://api.example.com
curl -I https://api.example.com
```

Expected:
- HTTP returns `301/308` redirect to HTTPS (except ACME path)
- HTTPS returns `200`/`401` depending on backend auth
- `certbot` container stays up and runs periodic renew

## Notes

- Runtime files are generated under:
  - `letsencrypt/`
  - `certbot-www/`
  - `nginx/conf.d/default.conf`
- These runtime files are git-ignored.
- If backend runs in another host/IP, rerun script with new `--backend-host/--backend-port`.
