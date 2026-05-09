# Backend and Nginx Discord Notifications Design

## Goal

Add Discord notifications for backend CI/CD and nginx deployment workflows, matching the existing frontend notification split:

- `DISCORD_WEBHOOK` is used for build-check style notifications.
- `DEPLOY_BOT_DISCORD` is used for deployment notifications.

API docs deployment is out of scope for this change.

## Scope

Update these workflows:

- `.github/workflows/discord-notify.yml`
- `.github/workflows/backend-cicd.yml`
- `.github/workflows/nginx-deploy.yml`

Do not change application code, deployment scripts, or GitHub Pages API docs behavior.

## Workflow Behavior

### Backend CI Build Check

`backend-cicd.yml` already runs the `ci` job for pull requests that touch backend or backend deploy files. The existing `discord-notify.yml` workflow will be extended so its `workflow_run` trigger also listens for `Backend CI/CD`.

When a backend PR build completes, `discord-notify.yml` sends a success or failure embed to `DISCORD_WEBHOOK`. The message includes the branch, actor, commit message, and Actions run URL.

### Backend Deployment

`backend-cicd.yml` already deploys on `dev` pushes after CI passes. Add success and failure notification steps to the `cd` job.

The deployment notification goes to `DEPLOY_BOT_DISCORD` and includes the branch, actor, commit, commit message, image tag, and Actions run URL. Success reports that the backend image was pushed and the Docker Compose deployment completed. Failure reports that the backend build, push, or server deployment failed.

### Nginx Deployment

`nginx-deploy.yml` deploys on `dev` pushes that touch nginx deploy files and on manual dispatch. Add success and failure notification steps to the `deploy` job.

The nginx notification goes to `DEPLOY_BOT_DISCORD` and includes the domain when available, branch, actor, commit, commit message, trigger type, and Actions run URL. Failure reports that nginx asset upload, configuration, certificate handling, or stack deployment failed.

## Error Handling

Each notification step skips cleanly when the relevant Discord secret is missing. Notification failures should not mask the original deployment result, so notification steps should be placed at the end of the job and use the existing success/failure conditions.

## Testing

Validate workflow YAML structure locally with a parser. Review changed workflow triggers and expressions for GitHub Actions compatibility.
