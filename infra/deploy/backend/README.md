# Backend CI/CD Deployment Notes

This directory contains deploy assets used by `.github/workflows/backend-cicd.yml`.

## Trigger
- CI/CD runs only when backend-related files change:
  - `apps/backend/**`
  - `infra/deploy/backend/**`
  - `.github/workflows/backend-cicd.yml`

## Required GitHub Secrets
- `DEPLOY_HOST`: ARM server public IP or DNS
- `DEPLOY_USER`: SSH user (for OCI Ubuntu image, usually `ubuntu`)
- `DEPLOY_SSH_KEY`: private key content for SSH
- `DEPLOY_PATH`: remote directory for compose files (example: `/home/ubuntu/cash-chat`)
- `GHCR_USERNAME`: GitHub username for pulling GHCR image on server
- `GHCR_TOKEN`: GitHub PAT with `read:packages` scope
- `BACKEND_SPRING_PROFILES_ACTIVE`: optional, defaults to `prod`

## First-time Server Setup
Run once on server:

```bash
mkdir -p /home/ubuntu/cash-chat
```

Docker and Docker Compose must already be installed.
