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
- `OPENAI_API_KEY`: OpenAI API key, required when `BACKEND_SPRING_PROFILES_ACTIVE=prod`
- `GOOGLE_CLIENT_ID`: Google OAuth client ID, required when `BACKEND_SPRING_PROFILES_ACTIVE=prod`
- `GOOGLE_CLIENT_SECRET`: Google OAuth client secret, required when `BACKEND_SPRING_PROFILES_ACTIVE=prod`
- `GOOGLE_REDIRECT_URI`: Google OAuth redirect URI, required when `BACKEND_SPRING_PROFILES_ACTIVE=prod`

## Optional GitHub Secrets

- `BACKEND_SPRING_PROFILES_ACTIVE`: backend Spring profile, defaults to `prod`
- `APP_SWAGGER_ENABLED`: set to `true` to expose Swagger in `prod`; missing or any other value keeps Swagger blocked

Keep production deployments on `BACKEND_SPRING_PROFILES_ACTIVE=prod`. Use `APP_SWAGGER_ENABLED=true` only when production Swagger access is intentionally needed.

Secret values are written into a Docker Compose env file as single-quoted values. Newlines and single quotes are rejected during deployment.

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

Local env values:

- `GEMINI_API_KEY`: Gemini API key used by the `dev` profile
- `GOOGLE_CLIENT_ID`: Google OAuth client ID for local callback testing
- `GOOGLE_CLIENT_SECRET`: Google OAuth client secret for local callback testing
- `GOOGLE_REDIRECT_URI`: local Google OAuth callback URI, defaults to `http://localhost:8080/api/auth/callback/google`
- `APP_SWAGGER_ENABLED`: local Swagger toggle, defaults to `true`

Notes:

- This compose builds backend image locally from `apps/backend/Dockerfile`.
- This compose runs backend with `SPRING_PROFILES_ACTIVE=dev`.
- H2 is embedded in the backend process (`jdbc:h2:mem:...`), so there is no separate DB container.
- API base URL for frontend test is `http://localhost:8080`.
- Swagger UI is available at `http://localhost:8080/swagger-ui/index.html`.
- H2 console is available at `http://localhost:8080/h2-console`.
