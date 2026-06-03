# Cash Chat MVP

Kotlin 기반의 모바일 핀테크 앱입니다. 사용자는 AI 채팅으로 대화하고, 출석·광고 시청 등으로 **포인트**를 적립하며, 포인트를 리워드로 사용합니다. Android / iOS 앱(KMM)과 Spring Boot REST API, OCI 배포 인프라로 구성된 **모노레포**입니다.

> 이 문서는 새로 합류한 팀원이 프로젝트 전반 구조와 업무 흐름을 빠르게 익히도록 작성된 온보딩 가이드입니다.

---

## 1. 시스템 아키텍처 한눈에

```
                ┌───────────────────────────┐
                │   Frontend (KMM)          │
                │   Android / iOS           │
                │   :app  +  :shared        │
                └────────────┬──────────────┘
                             │  REST (JSON)
                             │  SSE (채팅 스트리밍)
                             ▼
                ┌───────────────────────────┐        ┌─────────────────────┐
                │   Backend (Spring Boot)   │──────▶ │  Spring AI           │
                │   com.wnl.cashchat.api    │        │  Gemini / OpenAI     │
                │   JWT 인증 / 도메인 API    │        └─────────────────────┘
                └────────┬─────────┬────────┘
                         │         │
              ┌──────────▼──┐  ┌───▼──────────────────────┐
              │  MySQL 8    │  │  외부 연동                 │
              │  (Flyway)   │  │  Google/Apple OAuth        │
              │  dev: H2    │  │  Google AdMob SSV 콜백      │
              └─────────────┘  └───────────────────────────┘
```

- **앱**은 백엔드 REST API를 호출하고, 채팅 응답은 **SSE 스트리밍**으로 받습니다.
- **백엔드**는 JWT로 인증된 요청을 도메인별로 처리하고, LLM 호출은 Spring AI를 통해 추상화합니다.
- **DB 스키마**는 Flyway 마이그레이션으로 버전 관리됩니다 (dev는 H2 인메모리, prod는 MySQL 8).
- **외부 연동**: 로그인은 Google/Apple OAuth, 광고 보상은 Google AdMob의 SSV(Server-Side Verification) 콜백을 검증해 포인트를 지급합니다.

---

## 2. 모노레포 구조

```
cash-chat-mvp/
├── apps/
│   ├── backend/     # Spring Boot (Kotlin) REST API
│   └── frontend/    # Android + iOS (KMM)
│       ├── app/       # Android 앱 (:app) — Jetpack Compose
│       ├── shared/    # KMM 공유 모듈 (:shared) — Android/iOS 공통
│       └── CashChatIOS/  # iOS 앱 (Xcode 프로젝트)
├── infra/
│   ├── deploy/      # Docker Compose 배포 설정 (backend, nginx)
│   └── terraform/   # OCI 인프라 프로비저닝
└── docs/
    ├── adr/         # Architecture Decision Records
    └── ...          # specs, features, planning 등
```

---

## 3. 기술 스택

| 영역 | 스택 |
|------|------|
| **Backend** | Kotlin 1.9.25, Spring Boot 3.5.11, Java 21, Gradle 9.2.1 (Kotlin DSL) |
| | Spring Security + OAuth2 Resource Server, Spring Data JPA, Flyway |
| | Spring AI 1.0.0 (Gemini / OpenAI), springdoc-openapi (Swagger UI) |
| | DB: H2 (dev) / MySQL 8 (prod) · 테스트: Kotest 5.9.1 |
| **Frontend** | Kotlin 2.0.21, AGP 9.0.1, Gradle 8.14.4, Java 21, KMM |
| | Jetpack Compose (Material3), Navigation Compose, Koin |
| | Retrofit / OkHttp, DataStore, Google Play Services Auth, AdMob, Sentry |
| | App ID: `com.nomadclub.cashchat` · minSdk 24 / targetSdk 36 |
| **Infra** | Docker Compose, GHCR, OCI (ARM), Terraform, GitHub Actions |

---

## 4. 로컬 개발 환경 셋업

### 사전 요구사항
- **JDK 21** (필수) — `java -version`으로 확인
- **Android Studio** (프론트엔드 Android 빌드/실행)
- **Xcode** (iOS 빌드) — iOS 빌드 시 JAVA_HOME을 JDK 21로 지정해야 함:
  ```bash
  export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
  ```

### 환경 변수 / 시크릿
| 위치 | 키 | 용도 |
|------|----|----|
| `apps/backend/.env` | `GEMINI_API_KEY` | Spring AI(Gemini) 호출 키 |
| (참고) | `infra/deploy/backend/.env.example` | 프로덕션 배포용 환경변수 예시 |
| `apps/frontend/local.properties` | `BASE_URL` | 백엔드 API 주소 (기본 `https://cashchat.duckdns.org/`) |
| | `GOOGLE_WEB_CLIENT_ID` | Google 로그인 |
| | `ADMOB_*` | AdMob 앱/광고 단위 ID (banner/interstitial/native/rewarded) |
| | `SENTRY_DSN` | Sentry 에러 리포팅 |

> `.env`, `local.properties`, `key.properties` 등 시크릿 파일은 **커밋 금지**입니다.

### 백엔드 실행 (H2 인메모리 DB)
```bash
cd apps/backend
./gradlew bootRun              # 로컬 서버 실행 (dev 프로파일, H2)
./gradlew test                 # 테스트만 실행
./gradlew clean build          # 전체 빌드 + 테스트
./gradlew bootJar -x test      # JAR만 빌드 (테스트 제외)
```
실행 후 Swagger UI: `http://localhost:8080/swagger-ui.html`, H2 콘솔: `http://localhost:8080/h2-console`

### 프론트엔드 빌드
```bash
cd apps/frontend
./gradlew :app:assembleDebug          # Android 디버그 APK
./gradlew :app:assembleRelease        # Android 릴리즈 APK (key.properties 필요)
./gradlew :shared:assembleDebug       # KMM 공유 모듈
```
iOS는 `apps/frontend/CashChatIOS/CashChatIOS.xcodeproj`를 Xcode로 열어 빌드합니다.
(`Build Phases > Embed Shared Framework`가 `:shared:embedAndSignAppleFrameworkForXcode`를 호출하므로 JAVA_HOME 21 필수)

---

## 5. 도메인 & 핵심 플로우

백엔드는 `com.wnl.cashchat.api.domain.*` 아래 도메인별로 분리되어 있고, 각 도메인은 `web`(컨트롤러/요청·응답/예외핸들러) · `service` · `persistence`(엔티티/리포지토리) 계층을 가집니다.

### 인증 (`domain/auth` · `/api/auth`)
앱 실행 시 인증을 거쳐 **JWT access/refresh 토큰**을 발급받고, 이후 모든 API는 `Authorization` 헤더로 호출합니다.

| 엔드포인트 | 설명 |
|-----------|------|
| `POST /api/auth/guest` | 게스트 로그인 (deviceToken 기반) |
| `POST /api/auth/callback/google` | Google OAuth 콜백 → 로그인/가입 |
| `POST /api/auth/callback/apple` | Apple OAuth 콜백 → 로그인/가입 |
| `POST /api/auth/refresh` | refresh 토큰으로 access 토큰 재발급 |
| `POST /api/auth/logout` | 로그아웃 (refresh 토큰 폐기) |

요청은 `common/security`의 `JwtAuthenticationFilter`가 가로채 토큰을 검증하고, refresh 토큰은 DB(`RefreshToken`)에 저장·관리됩니다.

### 채팅 (`domain/chat` · `/api/v1/chat`)
앱은 보통 대화방을 먼저 생성한 뒤, 스트리밍으로 AI 응답을 받습니다.

| 엔드포인트 | 설명 |
|-----------|------|
| `POST /conversations` | 대화방 생성 |
| `GET  /conversations` | 내 대화방 목록 |
| `GET  /conversations/{id}/messages` | 대화방 메시지 조회 |
| `POST /stream` | **SSE 스트리밍** 채팅 응답 (`text/event-stream`) |

LLM 호출은 `service/llm`의 `LlmProvider` 인터페이스로 추상화되어 있어 **Gemini / OpenAI 구현체를 교체**할 수 있습니다. 메시지는 `Conversation`/`ChatMessage` 엔티티로 영속화됩니다.

> 💰 **비용 주의**: 채팅 스트리밍은 매 요청마다 LLM API를 호출하므로 토큰·호출 비용이 발생합니다. dev는 `gemini-3.1-flash-lite-preview` 모델을 사용합니다.

### 포인트 (`domain/point`)
- `UserPoint` — 사용자별 잔액
- `PointTransaction` — 적립/차감 **원장(ledger)**, 사유(`PointTransactionReason`) 기록
- 신규 가입 시 초기 잔액 지급 (`app.points.initial-balance`)

### 광고 보상 (`domain/ad` · `/api/ads`)
보상형 광고 시청 → 포인트 적립 흐름을 **위변조 없이** 처리합니다.

1. `POST /api/ads/reward/issue-nonce` — 보상 nonce 발급
2. 앱이 AdMob 보상형 광고를 노출
3. Google이 `GET /api/ads/google/ssv` 로 **SSV 콜백** 호출 → 서명 검증 후 포인트 적립
4. `GET /api/ads/reward/quota` — 일일 적립 한도 조회

### 출석 (`domain/attendance` · `/api/attendance`)
- `POST /api/attendance/check-in` — 출석 체크인 (중복 방지)
- `GET  /api/attendance/me` — 월별 출석 현황 + 보너스 리워드 조회

### DB 스키마 관리 (Flyway)
스키마는 `apps/backend/src/main/resources/db/migration`의 마이그레이션으로 관리됩니다. **기존 마이그레이션은 수정하지 말고 새 버전을 추가**하세요.

```
V1__baseline_schema.sql        # 기본 스키마 (user, auth 등)
V2__point_transaction.sql      # 포인트 원장
V3__attendance.sql             # 출석
V4__google_ad_ssv_events.sql   # 광고 SSV 이벤트
V5__ad_reward.sql              # 광고 보상
```

---

## 6. API 레퍼런스

| 도메인 | 베이스 경로 |
|--------|------------|
| 인증 | `/api/auth` |
| 사용자 | `/api/users` (`GET /me`) |
| 채팅 | `/api/v1/chat` |
| 광고 보상 | `/api/ads` |
| 출석 | `/api/attendance` |

전체 스펙은 서버 실행 후 **Swagger UI**(`/swagger-ui.html`)에서 확인할 수 있습니다.

---

## 7. 프론트엔드 구조

```
apps/frontend/app/src/main/java/com/nomadclub/cashchat/
├── feature/      # 화면 + ViewModel (auth, chat, main, rewards, shop, mypage, onboarding, settings)
├── core/
│   ├── network/  # ApiService (Retrofit)
│   └── storage/  # DataStore 등
├── data/         # viewmodel, repository, model
├── di/           # AppModule (Koin DI)
├── config/       # 앱 설정
└── ads/          # AdMob 연동
```
- `shared/src/commonMain` — KMM 공유 비즈니스 로직, `androidMain`/`iosMain` — 플랫폼별 구현체
- iOS는 `CashChatShared.framework`(static)로 공유 코드를 사용

---

## 8. 개발 워크플로우 (Fork 기반)

이 프로젝트는 **Fork 기반 협업**을 사용합니다. 메인 레포에 직접 push 하지 않고, **개인 Fork에서 작업 → 메인 레포의 `dev` 브랜치로 MR(PR)** 하는 방식입니다.

### 브랜치 전략
- `main` — 안정 배포 브랜치
- `dev` — **통합 브랜치 (모든 MR의 대상)**
- 개인 Fork의 작업 브랜치 — 아래 prefix로 시작해야 함 (CI가 검증):
  `feature/` · `feat/` · `fix/` · `bugfix/` · `refactor/` · `chore/` · `docs/` · `test/` · `hotfix/` · `release/` · `revert/`

### 작업 흐름
```
1. 메인 레포(upstream)를 Fork
2. Fork에서 작업 브랜치 생성     (예: feature/CC-123-chat-stream)
3. 커밋                          (Conventional Commits + commitlint/husky 검증)
4. Fork → 메인 레포 dev 로 MR(PR) 생성
5. CI 검사 + AI 리뷰 통과 → 리뷰 승인 → dev 머지
```

### 커밋 메시지 (Conventional Commits)
```
type(scope?): description
```
주요 타입: `feat` / `fix` / `refactor` / `docs` / `chore`
예: `feat: add chat streaming`, `fix(auth): refresh expired token`
> 로컬 커밋 시 **commitlint + husky**가 형식을 검증합니다.

### MR(PR) 생성 시 자동으로 일어나는 일

`dev` 브랜치로 MR을 올리면 GitHub Actions가 다음을 **자동 실행**합니다:

| 자동화 | 동작 |
|--------|------|
| **PR Title & Branch Check** | 제목이 `[CC-###] 제목` 형식인지, 브랜치명이 허용 prefix인지 검증 (실패 시 ❌) |
| **PR Description Auto Fill** | AI가 PR 요약·walkthrough를 **별도 코멘트로** 게시 (PR 템플릿 본문은 보존) |
| **PR Code Review (Gemini)** | `dev` 대상 MR에 Gemini가 한국어로 자동 코드 리뷰 (보안 포함, 최대 7개 지적, 커밋 push마다 재실행) |
| **Auto Assign / Labeler** | 리뷰어·담당자 자동 배정 및 라벨 부착 |
| **Android / iOS Build Check** | `apps/frontend/` 관련 경로가 바뀐 경우에만 빌드 검증 |
| **Extract Issue → Jira Transition** | PR 제목의 Jira 이슈를 추출해 상태를 자동 전환 (예: `In Review`) |
| **Discord 알림** | PR open/reopen/close 및 빌드 결과를 Discord로 통지 |

머지 후에는 백엔드 변경 시 **Backend CI/CD**가 이미지 빌드·배포까지 자동 수행합니다 (→ [9. 배포](#9-배포-개요)).

### 수동 AI 리뷰 명령어 (PR 코멘트로 입력)
| 명령어 | 설명 |
|--------|------|
| `/gemini-review` | Gemini 재리뷰 (자동 실행되지만 재요청 시) |
| `/openai-review` | OpenAI 심층 리뷰 (수동, **비용 발생**) |
| `/ask "질문"` | AI 코멘트에 답글로 후속 질문 |
| `@coderabbitai review` | CodeRabbit 리뷰 (수동, **비용 발생**) |

### ⚠️ MR 시 주의할 점
- **PR 제목에 Jira 이슈 ID 필수**: `[CC-123] 요약` 형식이 아니면 CI가 실패합니다.
- **브랜치명 prefix 준수**: 위 허용 prefix로 시작하지 않으면 CI가 실패합니다.
- **로컬에서 빌드·테스트를 먼저 통과**시키고 올리세요 (PR 템플릿 체크리스트 항목).
- **자동 코드 리뷰는 `dev` 대상 MR에서만** 동작합니다 (Fork PR의 시크릿 접근을 위해 `pull_request_target` 사용).
- `/openai-review`, `@coderabbitai` 등 **수동 심층 리뷰는 LLM 비용이 발생**하므로 필요할 때만 사용하세요.
- 빌드 체크는 변경 경로 기준으로 동작합니다 — 프론트엔드 빌드 검증은 `apps/frontend/` 변경 시에만 실행됩니다.

> 상세 규칙은 [`.github/CONTRIBUTING.md`](./.github/CONTRIBUTING.md)와 [`.github/PULL_REQUEST_TEMPLATE.md`](./.github/PULL_REQUEST_TEMPLATE.md)를 참고하세요.

---

## 9. 배포 개요

- 백엔드 컨테이너 이미지는 **GHCR**(`ghcr.io/<owner>/cash-chat-backend:latest`)로 배포
- **GitHub Actions**(`backend-cicd.yml`)가 SSH로 OCI(ARM) 서버에 접속해 **Docker Compose**(`infra/deploy/backend/`)로 자동 배포
- OCI 리소스는 **Terraform**(`infra/terraform/`)으로 프로비저닝
- nginx 리버스 프록시 설정: `infra/deploy/nginx/`

---

## 10. 추가 문서

- [`.github/CONTRIBUTING.md`](./.github/CONTRIBUTING.md) — Fork 협업·커밋·MR 규칙 상세
- [`.github/PULL_REQUEST_TEMPLATE.md`](./.github/PULL_REQUEST_TEMPLATE.md) — PR 체크리스트 & AI 리뷰 명령어
- [`CLAUDE.md`](./CLAUDE.md) / [`AGENTS.md`](./AGENTS.md) — AI 어시스턴트용 작업 가이드
- [`docs/adr/`](./docs/adr/) — Architecture Decision Records
- [`infra/deploy/backend/README.md`](./infra/deploy/backend/README.md) — 백엔드 배포 가이드
- [`infra/deploy/nginx/README.md`](./infra/deploy/nginx/README.md) — nginx 설정 가이드
