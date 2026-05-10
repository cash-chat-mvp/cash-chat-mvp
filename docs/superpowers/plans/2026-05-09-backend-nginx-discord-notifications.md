# Backend and Nginx Discord Notifications Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Discord notifications for backend CI build checks, backend deployments, and nginx deployments.

**Architecture:** Keep the existing Android notification workflow unchanged and add a separate backend build notification workflow. Backend and nginx deployment notifications live at the end of their deployment jobs so they can include deployment-specific context.

**Tech Stack:** GitHub Actions YAML, bash, `jq`, Discord webhooks.

---

## File Structure

- Add `.github/workflows/backend-build-discord-notify.yml`: listen for `Backend CI/CD` workflow-run completion and report backend PR build checks to `DISCORD_WEBHOOK`.
- Modify `.github/workflows/backend-cicd.yml`: append success and failure Discord notification steps to the `cd` job using `DEPLOY_BOT_DISCORD`.
- Modify `.github/workflows/nginx-deploy.yml`: append success and failure Discord notification steps to the `deploy` job using `DEPLOY_BOT_DISCORD`.

### Task 1: Backend CI build-check notification

**Files:**
- Add: `.github/workflows/backend-build-discord-notify.yml`

- [ ] **Step 1: Add backend build workflow-run trigger**

Create a backend-only notification workflow:

```yaml
name: 백엔드 빌드 알림

on:
  workflow_run:
    workflows: ["Backend CI/CD"]
    types: [completed]
```

- [ ] **Step 2: Add backend build result notification**

Add a `notify` job that only runs for pull request workflow runs and posts the backend build result to `DISCORD_WEBHOOK`:

```bash
if [ "$CONCLUSION" = "success" ]; then
  COLOR=5763719
  TITLE="✅ 백엔드 CI 빌드 체크 통과"
else
  COLOR=15548997
  TITLE="🚨 백엔드 CI 빌드 체크 실패"
fi
```

The message includes branch, actor, commit message, Actions log link, and footer `Cash Chat • 백엔드 빌드 체크`.

- [ ] **Step 3: Validate YAML**

Run: parse all changed workflow files with a YAML parser.

Expected: parser succeeds for `.github/workflows/backend-build-discord-notify.yml`.

### Task 2: Backend deployment notification

**Files:**
- Modify: `.github/workflows/backend-cicd.yml`

- [ ] **Step 1: Add backend deploy success notification**

Append a step at the end of the `cd` job:

```yaml
      - name: Discord backend deploy success notification
        if: success()
        env:
          DISCORD_WEBHOOK: ${{ secrets.DEPLOY_BOT_DISCORD }}
          COMMIT_MESSAGE: ${{ github.event.head_commit.message }}
          BRANCH: ${{ github.ref_name }}
          ACTOR: ${{ github.actor }}
          SHA: ${{ github.sha }}
          REPO: ${{ github.repository }}
          RUN_URL: ${{ github.server_url }}/${{ github.repository }}/actions/runs/${{ github.run_id }}
          IMAGE_TAG: ${{ steps.backend-image.outputs.name }}:latest
        run: |
          if [ -z "$DISCORD_WEBHOOK" ]; then
            echo "DISCORD_WEBHOOK is missing - skip notification"
            exit 0
          fi
          SHORT_SHA="${SHA:0:7}"
          COMMIT_URL="https://github.com/${REPO}/commit/${SHA}"
          PAYLOAD=$(jq -n \
            --arg msg "$COMMIT_MESSAGE" \
            --arg branch "$BRANCH" \
            --arg actor "$ACTOR" \
            --arg sha "$SHORT_SHA" \
            --arg commit_url "$COMMIT_URL" \
            --arg run_url "$RUN_URL" \
            --arg image "$IMAGE_TAG" \
            '{embeds: [{
              color: 5763719,
              title: "✅ Backend deployment complete",
              description: "**Backend image push and Docker Compose deployment completed**",
              fields: [
                {name: "Branch", value: ("`" + $branch + "`"), inline: true},
                {name: "Deployer", value: ("`" + $actor + "`"), inline: true},
                {name: "Commit", value: ("[`" + $sha + "`](" + $commit_url + ")"), inline: true},
                {name: "Image", value: ("`" + $image + "`"), inline: false},
                {name: "Commit message", value: ("`" + $msg + "`"), inline: false},
                {name: "Actions log", value: ("[Open run](" + $run_url + ")"), inline: false}
              ],
              footer: {text: "Cash Chat - Backend Deploy"}
            }]}')
          curl -sS -X POST "$DISCORD_WEBHOOK" -H "Content-Type: application/json" -d "$PAYLOAD"
```

The embed title should be `✅ Backend deployment complete`, with fields for branch, deployer, commit, image, commit message, and Actions log.

- [ ] **Step 2: Add backend deploy failure notification**

Append a failure step after the success step:

```yaml
      - name: Discord backend deploy failure notification
        if: failure()
        env:
          DISCORD_WEBHOOK: ${{ secrets.DEPLOY_BOT_DISCORD }}
          COMMIT_MESSAGE: ${{ github.event.head_commit.message }}
          BRANCH: ${{ github.ref_name }}
          ACTOR: ${{ github.actor }}
          SHA: ${{ github.sha }}
          REPO: ${{ github.repository }}
          RUN_URL: ${{ github.server_url }}/${{ github.repository }}/actions/runs/${{ github.run_id }}
        run: |
          if [ -z "$DISCORD_WEBHOOK" ]; then
            echo "DISCORD_WEBHOOK is missing - skip notification"
            exit 0
          fi
          SHORT_SHA="${SHA:0:7}"
          COMMIT_URL="https://github.com/${REPO}/commit/${SHA}"
          PAYLOAD=$(jq -n \
            --arg msg "$COMMIT_MESSAGE" \
            --arg branch "$BRANCH" \
            --arg actor "$ACTOR" \
            --arg sha "$SHORT_SHA" \
            --arg commit_url "$COMMIT_URL" \
            --arg run_url "$RUN_URL" \
            '{embeds: [{
              color: 15548997,
              title: "❌ Backend deployment failed",
              description: "**Backend image build, push, or server deployment failed**",
              fields: [
                {name: "Branch", value: ("`" + $branch + "`"), inline: true},
                {name: "Actor", value: ("`" + $actor + "`"), inline: true},
                {name: "Commit", value: ("[`" + $sha + "`](" + $commit_url + ")"), inline: true},
                {name: "Commit message", value: ("`" + $msg + "`"), inline: false},
                {name: "Actions log", value: ("[Open run](" + $run_url + ")"), inline: false}
              ],
              footer: {text: "Cash Chat - Backend Deploy"}
            }]}')
          curl -sS -X POST "$DISCORD_WEBHOOK" -H "Content-Type: application/json" -d "$PAYLOAD"
```

The embed title should be `❌ Backend deployment failed`, with fields for branch, actor, commit, commit message, and Actions log.

- [ ] **Step 3: Validate YAML**

Run: parse all changed workflow files with a YAML parser.

Expected: parser succeeds for `.github/workflows/backend-cicd.yml`.

### Task 3: Nginx deployment notification

**Files:**
- Modify: `.github/workflows/nginx-deploy.yml`

- [ ] **Step 1: Add nginx deploy success notification**

Append a step at the end of the `deploy` job:

```yaml
      - name: Discord nginx deploy success notification
        if: success()
        env:
          DISCORD_WEBHOOK: ${{ secrets.DEPLOY_BOT_DISCORD }}
          COMMIT_MESSAGE: ${{ github.event.head_commit.message }}
          BRANCH: ${{ github.ref_name }}
          ACTOR: ${{ github.actor }}
          SHA: ${{ github.sha }}
          REPO: ${{ github.repository }}
          RUN_URL: ${{ github.server_url }}/${{ github.repository }}/actions/runs/${{ github.run_id }}
          EVENT_NAME: ${{ github.event_name }}
          NGINX_DOMAIN: ${{ vars.NGINX_DOMAIN }}
        run: |
          if [ -z "$DISCORD_WEBHOOK" ]; then
            echo "DISCORD_WEBHOOK is missing - skip notification"
            exit 0
          fi
          SHORT_SHA="${SHA:0:7}"
          COMMIT_URL="https://github.com/${REPO}/commit/${SHA}"
          DOMAIN_VALUE=$([ -n "$NGINX_DOMAIN" ] && echo "\`$NGINX_DOMAIN\`" || echo "not configured")
          PAYLOAD=$(jq -n \
            --arg msg "$COMMIT_MESSAGE" \
            --arg branch "$BRANCH" \
            --arg actor "$ACTOR" \
            --arg sha "$SHORT_SHA" \
            --arg commit_url "$COMMIT_URL" \
            --arg run_url "$RUN_URL" \
            --arg event "$EVENT_NAME" \
            --arg domain "$DOMAIN_VALUE" \
            '{embeds: [{
              color: 5763719,
              title: "✅ Nginx deployment complete",
              description: "**Nginx stack deployment completed**",
              fields: [
                {name: "Domain", value: $domain, inline: true},
                {name: "Trigger", value: ("`" + $event + "`"), inline: true},
                {name: "Branch", value: ("`" + $branch + "`"), inline: true},
                {name: "Actor", value: ("`" + $actor + "`"), inline: true},
                {name: "Commit", value: ("[`" + $sha + "`](" + $commit_url + ")"), inline: true},
                {name: "Commit message", value: ("`" + $msg + "`"), inline: false},
                {name: "Actions log", value: ("[Open run](" + $run_url + ")"), inline: false}
              ],
              footer: {text: "Cash Chat - Nginx Deploy"}
            }]}')
          curl -sS -X POST "$DISCORD_WEBHOOK" -H "Content-Type: application/json" -d "$PAYLOAD"
```

The embed title should be `✅ Nginx deployment complete`, with fields for domain, trigger, branch, actor, commit, commit message, and Actions log.

- [ ] **Step 2: Add nginx deploy failure notification**

Append a failure step after the success step with the same context and title `❌ Nginx deployment failed`.

- [ ] **Step 3: Validate YAML**

Run: parse all changed workflow files with a YAML parser.

Expected: parser succeeds for `.github/workflows/nginx-deploy.yml`.

### Task 4: Final verification

**Files:**
- Verify: `.github/workflows/backend-build-discord-notify.yml`
- Verify: `.github/workflows/backend-cicd.yml`
- Verify: `.github/workflows/nginx-deploy.yml`

- [ ] **Step 1: Search notification secrets**

Run: `rg -n "DISCORD_WEBHOOK|DEPLOY_BOT_DISCORD|Backend CI/CD|백엔드 CI|Nginx 배포|백엔드 배포" .github/workflows`

Expected:
- Build-check notification uses `DISCORD_WEBHOOK`.
- Backend and nginx deployment notifications use `DEPLOY_BOT_DISCORD`.
- `deploy-api-docs.yaml` remains unchanged.

- [ ] **Step 2: Check git diff**

Run: `git -c safe.directory=D:/Work/cash-chat-mvp diff -- .github/workflows/backend-build-discord-notify.yml .github/workflows/backend-cicd.yml .github/workflows/nginx-deploy.yml`

Expected: Diff only contains notification changes for backend CI/CD and nginx deployment.
