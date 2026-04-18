# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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
- `domain/auth/` — OAuth2(Google) 로그인, JWT access/refresh 토큰 발급 및 갱신
- `domain/user/` — 사용자 관리
- `common/security/` — JWT 필터, Spring Security 설정
- `common/entity/` — 공통 BaseEntity

개발 환경은 H2 인메모리 DB, 프로덕션은 MySQL 8 사용.
Spring AI를 통해 Gemini(dev: `gemini-2.0-flash`) 또는 OpenAI 연동.
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
- `app/src/main/.../feature/` — 기능별 화면 + ViewModel (auth, chat, main, rewards, shop, mypage, onboarding)
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

**커밋 메시지 형식**:
```
[ISSUE-#] type : description
```
- `feat` / `fix` / `refactor` / `docs` / `chore`

**PR 제목**: `[ISSUE-#] Summary`
GitHub Issue는 사용하지 않으며, **Jira로 작업 관리**. PR 생성 시 Jira Issue가 자동으로 `In Review`로 전환됨.

## Infrastructure

- Docker Compose로 OCI ARM 서버에 배포 (`infra/deploy/backend/`)
- 컨테이너 이미지: GHCR (`ghcr.io/<owner>/cash-chat-backend:latest`)
- Terraform으로 OCI 리소스 프로비저닝 (`infra/terraform/`)
- 배포는 GitHub Actions (`backend-cicd.yml`)가 SSH를 통해 자동 수행
