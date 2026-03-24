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
- `nginx/conf.d/http.conf.template`: HTTP bootstrap template
- `nginx/conf.d/https.conf.template`: HTTPS reverse proxy template

## Quick Start

Use GitHub Actions (`nginx-deploy.yml`) with configured Variables/Secrets.
Manual run on server is possible, but primary flow is CI deployment.

```bash
cd /path/to/cash-chat-mvp/infra/deploy/nginx
docker compose up -d nginx
```

### Optional behavior (via Repository Variables)

- Add SAN for `www`: `NGINX_ENABLE_WWW=true`
- Test with staging CA first: `NGINX_CERTBOT_STAGING=true`
- Configure HTTP only: `NGINX_SKIP_CERTBOT=true`
- Force reissue certificate: `NGINX_FORCE_RENEWAL=true`

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
- If backend host/port changes, update Variables and rerun workflow.

## GitHub Actions Setup

Workflow file:
- `.github/workflows/nginx-deploy.yml`

Required repository Variables:
- `NGINX_DEPLOY_PATH` (example: `/home/ubuntu/cash-chat-mvp/infra/deploy/nginx`)
- `NGINX_DOMAIN` (example: `api.example.com`)
- `NGINX_LE_EMAIL` (example: `devops@example.com`)
- `NGINX_BACKEND_HOST` (default recommended: `host.docker.internal`)
- `NGINX_BACKEND_PORT` (default: `8080`)

Optional repository Variables:
- `NGINX_ENABLE_WWW` (`true` or `false`)
- `NGINX_CERTBOT_STAGING` (`true` or `false`)
- `NGINX_SKIP_CERTBOT` (`true` or `false`)
- `NGINX_FORCE_RENEWAL` (`true` or `false`)

Required repository Secrets:
- `DEPLOY_HOST` (server public IP or DNS)
- `DEPLOY_USER` (SSH user, usually `ubuntu`)
- `DEPLOY_SSH_KEY` (private key content)
