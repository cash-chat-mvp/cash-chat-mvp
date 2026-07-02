# AGENTS.md

This file is the single source of truth for coding-agent guidance in this repository.
Codex reads it directly; Claude Code reads it via the `@AGENTS.md` import in `CLAUDE.md`.
공용 지침은 이 파일에만 두고, 도구 전용 지침만 각 도구 파일에 둡니다.

## Project Overview

Cash Chat MVP는 Kotlin 기반의 모바일 핀테크 앱으로, 다음 구조로 이루어진 모노레포입니다:
- `apps/backend/` — Spring Boot 3.5.11 (Kotlin) REST API
- `apps/frontend/` — Android + iOS Kotlin Multiplatform (KMM) 앱
- `infra/` — Docker Compose 배포 설정 및 Terraform (OCI) 인프라
- `docs/adr/` — Architecture Decision Records

## Backend (`apps/backend/`)

**Stack**: Kotlin 1.9.25, Spring Boot 3.5.11, Java 21, Gradle (Kotlin DSL)
**Package**: `com.wnl.cashchat.api`

### Commands
```bash
cd apps/backend
./gradlew clean build          # 전체 빌드 + 테스트
./gradlew test                 # 테스트만 실행
./gradlew bootRun              # 로컬 서버 실행 (H2 DB)
./gradlew bootJar -x test      # JAR만 빌드 (테스트 제외)
```

### Architecture
도메인은 `domain/<name>/` 아래에 위치하며, 각 도메인은 동일한 레이어링을 따릅니다:
`persistence/{entity,repository}` · `service` · `web/{controller,response,exception}` · (필요 시 `properties`).

도메인 목록: `auth`, `user`, `chat`, `ad`, `point`, `ledger`, `shop`, `inventory`,
`attendance`, `energy`, `evolution`, `offerwall`, `invite`, `quality`.
- `domain/auth/` — OAuth2(Google) 로그인, JWT access/refresh 토큰 발급 및 갱신
- `domain/user/` — 사용자 관리
- `domain/chat/` — Gemini 연동 채팅 (SSE 스트리밍 응답)
- `domain/ad/` — 광고/리워드, Google Ad SSV(Server-Side Verification) 콜백
- `domain/point/`, `domain/ledger/` — 포인트 잔액·거래 원장
- `domain/shop/`, `domain/inventory/` — 상점 아이템·보유 인벤토리
- `domain/attendance/`, `domain/energy/`, `domain/evolution/`, `domain/offerwall/` — 리텐션/게이미피케이션 기능
- `domain/invite/` — 친구 초대 코드 발급/사용(redeem), 초대자 보상 한도(동시성 락) 처리
- `common/security/` — JWT 필터, Spring Security 설정
- `common/web/response/` — 공통 API 응답 래퍼
- `common/entity/` — 공통 BaseEntity

개발 환경은 H2 인메모리 DB, 프로덕션은 MySQL 8 사용.
Spring AI를 통해 Gemini(`gemini-3.1-flash-lite`, dev/prod 모두 OpenAI 호환 엔드포인트 사용) 연동.
테스트는 Kotest + TestContainers (MySQL) 조합 사용.

## Frontend (`apps/frontend/`)

**Stack**: Kotlin 2.0.21, AGP 9.0.1, Jetpack Compose (Material3), Java 21, KMM
**App ID**: `com.nomadclub.cashchat`, Min SDK: 24, Target SDK: 36

### Commands
```bash
cd apps/frontend
./gradlew :app:assembleDebug          # Android 디버그 APK 빌드
./gradlew :app:assembleRelease        # Android 릴리즈 APK 빌드 (key.properties 필요)
./gradlew :shared:assembleDebug       # KMM 공유 모듈 빌드
```

iOS는 Xcode에서 `CashChatIOS/CashChatIOS.xcodeproj`를 열어 빌드.
iOS 빌드 시 `Build Phases > Embed Shared Framework` 스크립트가 `gradlew :shared:embedAndSignAppleFrameworkForXcode`를 호출하며, **반드시 JAVA_HOME을 JDK 21로 설정**해야 합니다:
```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
```

### Architecture
- `app/src/main/.../feature/` — 기능별 화면 + ViewModel (auth, chat, gate, main, rewards, shop, settings, mypage, onboarding)
- `shared/src/commonMain/` — KMM 공유 비즈니스 로직 (Android/iOS 공통)
- `shared/src/androidMain/`, `shared/src/iosMain/` — 플랫폼별 구현체
- iOS는 `CashChatShared.framework` (static) 를 통해 공유 코드 사용

릴리즈 서명은 `key.properties` 또는 환경변수 `KEYSTORE_*`로 설정.

### 주의사항
다음 파일은 커밋 금지:
- `.gradle-local/`, `shared/build/`, `build/`, `local.properties`, `.idea`
- `CashChatIOS/` 안의 중첩 git 저장소

## Git Workflow

**브랜치 전략**:
- `main` — 안정 배포 브랜치
- `dev` — 통합 브랜치 (PR 대상)
- `feature/*` — 기능 개발 (개인 Fork에서 작업)
- `hot-fix/*` — 긴급 수정

**작업 시작 전 dev 동기화 (필수)**:
다른 작업자가 통합 `dev`에 먼저 머지하면, 나중에 PR을 올릴 때
`This branch is out-of-date with the base branch` 가 뜹니다. 이를 줄이려면
**브랜치를 항상 최신 upstream `dev`에서 분기**합니다. (origin은 fork라 origin/dev 는 뒤처져 있을 수 있으니
반드시 **upstream(parent) 저장소의 dev**를 기준으로 합니다.)
```bash
# upstream 리모트가 없으면 1회 등록 (gh repo view --json parent 로 parent 확인)
git remote add upstream https://github.com/<upstream-owner>/<repo>.git

# 작업 시작 전: upstream 의 최신 dev 를 받아 그 위에서 새 브랜치 분기
git fetch upstream

# 로컬 브랜치가 upstream/dev를 추적하지 않도록 --no-track 옵션 사용
git switch -c <branch> upstream/dev --no-track

# 이미 진행 중인 브랜치가 뒤처졌다면(PR out-of-date): 최신 dev 를 병합
git fetch upstream
git merge upstream/dev    # 충돌 해결 후 커밋·push
```

**커밋 메시지 형식**:
```
type(scope?): description
```
- Conventional Commits 형식을 사용합니다.
- **언어 규칙**: `type(scope):` 는 영어 키워드를 유지하고, **콜론 뒤 한 칸 띄운 설명(description)과 본문은 한국어로 작성**합니다.
  - `type`/`scope` 를 한글로 바꾸면 commitlint(`config-conventional`)가 커밋을 거부하니 영어를 유지하세요.
  - **콜론 뒤 공백 필수**: `feat:채팅` 처럼 붙여 쓰면 commitlint 이 거부합니다 (`feat: 채팅` 처럼 한 칸 띄우세요).
  - **설명은 한글 단어로 시작**: 영어 대문자 약어로 시작하면(`CLAUDE.md …`, `iOS …`, `API …`) commitlint `subject-case` 규칙에 걸립니다. 약어는 문장 중간에 두세요. 예 ✗ `docs: CLAUDE.md 통합` → ✓ `docs: 지침을 통합 (CLAUDE.md)`.
- 예: `feat: 채팅 스트리밍 추가`
- 예: `fix(auth): 만료된 토큰 갱신`
- 예: `chore: husky로 commitlint 설정`
- 주요 타입: `feat` / `fix` / `refactor` / `docs` / `chore`

**PR 제목**: `[ISSUE-#] Summary`
GitHub Issue는 사용하지 않으며, **Jira로 작업 관리**. PR 생성 시 Jira Issue가 자동으로 `In Review`로 전환됨.

**Fork 환경에서 PR 생성 (중요)**:
작업용 origin이 통합 저장소의 **fork**인 경우(개인 fork에서 작업하는 경우),
PR은 **fork 브랜치 → upstream의 통합 브랜치(`dev`)** 로 올립니다.
`gh pr create`를 인자 없이 쓰면 base 저장소/head ref를 잘못 잡아
`No commits between ...` / `Head ref must be a branch` 오류가 납니다. **반드시 base 저장소와 fork head를 명시**하세요:
```bash
# 저장소 관계 확인 (isFork, parent 로 upstream 식별)
gh repo view --json name,isFork,parent

# 1) fork(origin)에 먼저 푸시
git push -u origin <branch>
# 2) base 저장소·base 브랜치·fork head 를 모두 명시해 PR 생성
gh pr create --repo <upstream-owner>/<repo> \
  --base dev --head <fork-owner>:<branch> \
  --title "[ISSUE-#] Summary" --body "..."
```
- `--repo`에는 **upstream(parent) 저장소**, `--head`에는 **`<fork-owner>:<branch>` 형식**을 넣습니다.
- 워크트리 **디렉터리 이름과 브랜치 이름은 일치할 필요 없습니다**(독립적). 브랜치만 `git branch -m`으로 바꾸면 됩니다.

## Infrastructure

- Docker Compose로 OCI ARM 서버에 배포 (`infra/deploy/backend/`)
- 컨테이너 이미지: GHCR (`ghcr.io/<owner>/cash-chat-backend:latest`)
- Terraform으로 OCI 리소스 프로비저닝 (`infra/terraform/`)
- 배포는 GitHub Actions (`backend-cicd.yml`)가 SSH를 통해 자동 수행

## Imported Claude Cowork project instructions
