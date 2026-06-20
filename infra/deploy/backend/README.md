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
- `SPRING_DATASOURCE_URL`: MySQL JDBC URL, for example `jdbc:mysql://<mysql-host>:3306/cashchat?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true`
- `SPRING_DATASOURCE_USERNAME`: MySQL username with privileges on the `cashchat` database
- `SPRING_DATASOURCE_PASSWORD`: MySQL password
- `GEMINI_API_KEY`: Gemini API key (used via the OpenAI-compatible endpoint for chat), required when `BACKEND_SPRING_PROFILES_ACTIVE=prod`
- `GOOGLE_CLIENT_ID`: Google OAuth client ID, required when `BACKEND_SPRING_PROFILES_ACTIVE=prod`
- `GOOGLE_CLIENT_SECRET`: Google OAuth client secret, required when `BACKEND_SPRING_PROFILES_ACTIVE=prod`
- `GOOGLE_REDIRECT_URI`: Google OAuth redirect URI, required when `BACKEND_SPRING_PROFILES_ACTIVE=prod`
- `APPLE_CLIENT_ID`: Apple Services ID, required when `BACKEND_SPRING_PROFILES_ACTIVE=prod`
- `APPLE_TEAM_ID`: Apple Developer Team ID, required when `BACKEND_SPRING_PROFILES_ACTIVE=prod`
- `APPLE_KEY_ID`: Sign in with Apple key ID, required when `BACKEND_SPRING_PROFILES_ACTIVE=prod`
- `APPLE_PRIVATE_KEY`: Sign in with Apple P8 private key, required when `BACKEND_SPRING_PROFILES_ACTIVE=prod`
- `APP_ADS_GOOGLE_REWARDED_AD_UNIT_ID`: Google AdMob rewarded ad unit ID, required when `BACKEND_SPRING_PROFILES_ACTIVE=prod`

## Optional GitHub Secrets

- `BACKEND_SPRING_PROFILES_ACTIVE`: backend Spring profile, defaults to `prod`
- `SPRING_DATASOURCE_DRIVER_CLASS_NAME`: JDBC driver class, defaults to `com.mysql.cj.jdbc.Driver`
- `APPLE_REDIRECT_URI`: Apple redirect URI; native iOS Sign in with Apple can leave this empty

Keep production deployments on `BACKEND_SPRING_PROFILES_ACTIVE=prod`. Swagger/OpenAPI is disabled in the `prod` profile and cannot be enabled on the production server.

Secret values are written into a Docker Compose env file as single-quoted values. Newlines and single quotes are rejected during deployment.
Store `APPLE_PRIVATE_KEY` as one line by replacing real PEM newlines with the two-character `\n` sequence, for example:

```text
-----BEGIN PRIVATE KEY-----\nMIGTAgEAMBMGByqGSM49...\n-----END PRIVATE KEY-----
```

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
- `APPLE_CLIENT_ID`: Apple Services ID for local iOS callback testing
- `APPLE_TEAM_ID`: Apple Developer Team ID for local iOS callback testing
- `APPLE_KEY_ID`: Sign in with Apple key ID for local iOS callback testing
- `APPLE_PRIVATE_KEY`: escaped one-line P8 private key for local iOS callback testing
- `APPLE_REDIRECT_URI`: optional Apple redirect URI, defaults to empty
- `APP_ADS_GOOGLE_REWARDED_AD_UNIT_ID`: Google AdMob rewarded ad unit ID for local SSV configuration examples

Notes:

- This compose builds backend image locally from `apps/backend/Dockerfile`.
- This compose runs backend with `SPRING_PROFILES_ACTIVE=dev`.
- H2 is embedded in the backend process (`jdbc:h2:mem:...`), so there is no separate DB container.
- API base URL for frontend test is `http://localhost:8080`.
- Swagger UI is available at `http://localhost:8080/swagger-ui/index.html`.
- H2 console is available at `http://localhost:8080/h2-console`.
