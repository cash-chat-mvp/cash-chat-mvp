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
- `OPENAI_API_KEY`: OpenAI API key (required for prod profile)

## First-time Server Setup
Run once on server:

```bash
mkdir -p /home/ubuntu/cash-chat
```

Docker and Docker Compose must already be installed.

## Local Frontend Test Compose (Backend + H2)

For local frontend integration testing, use:

- `infra/deploy/backend/docker-compose.frontend-local.yml`

Run:

```bash
cd infra/deploy/backend
cp .env.example .env
docker compose -f docker-compose.frontend-local.yml up -d
```

Notes:

- This compose builds backend image locally from `apps/backend/Dockerfile`.
- This compose runs backend with `SPRING_PROFILES_ACTIVE=dev`.
- H2 is embedded in the backend process (`jdbc:h2:mem:...`), so there is no separate DB container.
- API base URL for frontend test is `http://localhost:8080`.
- H2 console is available at `http://localhost:8080/h2-console`.
