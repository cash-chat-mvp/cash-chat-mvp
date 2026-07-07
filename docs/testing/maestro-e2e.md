# FE 인수 테스트 (Maestro + hermetic mock) 가이드

프론트엔드(Android)의 **관통/인수 테스트**를, 백엔드 서버 없이 **인앱 Fake 백엔드**로 결정론적으로 수행하는 방법.
CC-391 스파이크로 도입했으며, 핵심 3개 여정이 6/6 green으로 검증되었다.

- **원본 스파이크 설계·회고**: [`docs/superpowers/specs/2026-07-07-maestro-fe-acceptance-spike-design.md`](../superpowers/specs/2026-07-07-maestro-fe-acceptance-spike-design.md)
- **유저 스토리(인수 기준)**: [`docs/domains/`](../domains/) (chat / reward)
- **flow·러너**: `apps/frontend/maestro/`, `apps/frontend/scripts/maestro-e2e-test.sh`

---

## 1. 무엇을 / 왜

- **무엇**: 사용자 여정을 실제 앱 UI로 관통하며(로그인 → 화면 이동 → 조작 → 결과 확인) 인수 기준(GWT)을 검증.
- **왜 hermetic**: 실제 백엔드·네트워크·외부 SDK(AdMob/TNK)에 의존하면 느리고 잘 깨진다. `mock` 플레이버가
  **인앱 Fake 백엔드(Ktor MockEngine)** 와 **Fake 광고/오퍼월 SDK**로 대체해 **결정론적**이고 **CI 친화적**.
- **역할 분담**: 네트워크 계약(스키마)은 기존 `shared/commonTest` MockEngine 계약 테스트가, UI 관통은 여기(Maestro)가 담당.

## 2. 아키텍처 (mock 플레이버가 무엇을 대체하나)

```
[Maestro] → 실제 앱 UI 구동 (탭/입력/스크롤/assert)
   │
   ▼
com.nomadclub.cashchat (mock 플레이버 APK)
   ├─ 로그인 우회:   부팅 시 TokenDataStore 에 role=MEMBER 세션을 심어 곧장 홈 진입
   ├─ HttpClient:    Koin override → Ktor MockEngine (fakeBackendEngine)
   │                  → 모든 *Api/Store 가 무변경으로 인앱 Fake 백엔드 호출
   │                  (직렬화·SSE 파서·에러 매핑·HUD·잔액·quota 실제 경로 관통)
   ├─ 광고 SDK:      RewardedAdPresenter → FakeRewardedAdPresenter (즉시 보상 → usedToday++)
   ├─ 오퍼월 SDK:    OfferwallLauncher   → FakeOfferwallLauncher   (즉시 완료 → 잔액 += )
   └─ 시나리오:      launch intent extra "scenario" → MockBackendState 반영
```

관련 소스(모두 `apps/frontend/app/src/mock/`):
- `mock/MockBackendState.kt` — 인메모리 상태(잔액·에너지·quota·시나리오)
- `mock/FakeBackendEngine.kt` — 경로별 canned JSON/SSE
- `mock/FakeRewardedAdPresenter.kt`, `mock/FakeOfferwallLauncher.kt` — 외부 SDK Fake
- `mock/MockModule.kt` — Koin override(HttpClient/Presenter/Launcher/State)
- `flavor/FlavorModules.kt`(mock/real) — override 로드·세션 심기·시나리오 주입
- `app/src/mock/AndroidManifest.xml` — AdMob APPLICATION_ID 를 공개 테스트 ID 로 대체(크래시 회피)

seam 인터페이스(main): `ads/RewardedAdPresenter.kt`, `offerwall/OfferwallLauncher.kt`.

## 3. 유저 스토리 ↔ flow 구조 (`<verb>-<object>`)

유저 스토리는 **`<verb>-<object>` 여정 식별자**로 명명하고 flow 디렉터리와 1:1 대응한다.

| 여정(verb-object) | 유저 스토리 | flow | 시나리오 |
| ----------------- | ---------- | ---- | -------- |
| **send-message** | [US-CHAT-001](../domains/chat/US-CHAT-001-send-message.md) | `flows/send-message/{happy,stream-error}.yaml` | `happy`, `chat_error` |
| **watch-rewarded-ad** | [US-REWARD-002](../domains/reward/US-REWARD-002-rewarded-ad.md) (FE AC) | `flows/watch-rewarded-ad/{happy,quota-exceeded}.yaml` | `happy`, `ad_quota_exceeded` |
| **complete-offerwall** | [US-REWARD-003](../domains/reward/US-REWARD-003-tnk-offerwall.md) (FE AC) | `flows/complete-offerwall/{happy,token-fail}.yaml` | `happy`, `offerwall_fail` |

각 flow 는 상단 주석에 `US-*/AC-FE-*` 를 역참조하고, 각 US 의 "검증 매핑"이 flow 경로를 역참조한다(양방향).

## 4. 테스트 작성 방법

### 4.1 새 유저 스토리(여정) 추가
1. `docs/domains/<domain>/US-*.md` 에 스토리 + `## FE 관통 인수 기준`(GWT, `AC-FE-NN`)을 쓴다. 여정 식별자는 `<verb>-<object>`.
2. `apps/frontend/maestro/flows/<verb-object>/` 폴더에 `happy.yaml` + 실패/경계 flow 를 만든다.
3. flow 상단 주석에 US/AC ID, 화면 조작, 시나리오를 적는다.

### 4.2 flow 작성 규칙
- **시작**: `launchApp: { clearState: true, arguments: { scenario: "<시나리오>" } }`.
- **셀렉터**: 표시 텍스트/`contentDescription` 우선(예: `tapOn: "리워드"`, `tapOn: "전송"`). 취약하면 `maestro studio` 로 확인.
- **한글 입력 금지**: `inputText` 는 유니코드 미지원 → ASCII 사용(mock 응답은 입력과 무관).
- **상태 변화 지점마다 `takeScreenshot: <이름>`** 을 남긴다 → 리포트에서 스크린샷 확인(딜레이 불필요).
- **시스템 다이얼로그**는 `runFlow: { when: { visible: "..." }, commands: [...] }` 로 조건부 dismiss(이식성).

### 4.3 새 시나리오/응답 추가
- `MockBackendState` 에 상태 필드/기본값 추가 → `applyScenarioDefaults()` 에 분기.
- `FakeBackendEngine` 의 `when` 에 엔드포인트 경로 분기 추가(**JSON 필드명은 실제 DTO와 일치**해야 역직렬화됨).
- 외부 SDK 결과가 필요하면 Fake presenter/launcher 에서 상태를 갱신.
- mock 유닛테스트(`app/src/testMock/`)로 엔진/Fake 를 먼저 검증(TDD).

## 5. 수행 방법

### 5.1 스크립트(권장) — 부팅+설치+실행+리포트 일괄
```bash
apps/frontend/scripts/maestro-e2e-test.sh                 # 전체
apps/frontend/scripts/maestro-e2e-test.sh send-message    # 단일 유저 스토리(개발 중 빠른 확인)
apps/frontend/scripts/maestro-e2e-test.sh --report        # 전체 + HTML 리포트
apps/frontend/scripts/maestro-e2e-test.sh --no-install watch-rewarded-ad   # 재설치 없이
apps/frontend/scripts/maestro-e2e-test.sh -h              # 옵션/도움말
```
스크립트가 하는 일: 에뮬레이터 부팅(꺼져 있으면) → `installMockDebug` → 대상 flow 실행 → (`--report`) 리포트 생성.
> **단일 유저 스토리 실행**으로 한 기능 개발 시 전체(≈3분)를 기다리지 않는다.

### 5.2 maestro 직접
```bash
cd apps/frontend
maestro test maestro                               # 전체 (config.yaml 의 flows/** 포함)
maestro test maestro/flows/send-message            # 단일 유저 스토리(폴더)
maestro test maestro/flows/send-message/happy.yaml # 단일 flow
```

### 5.3 데모(사람이 화면 보며 확인)
`maestro/demo/` 는 단계마다 `_pause.yaml`(≈1.3s)로 느리게 진행 + 스크린샷. CI 미포함.
```bash
maestro test maestro/demo/send-message.yaml
```

## 6. 결과 확인

### 6.1 리포트 + 스크린샷 (딜레이 없이)
```bash
maestro test maestro --format HTML-DETAILED --output report.html --debug-output ./maestro-debug
```
- `report.html`: 사람용 리포트(스텝별 pass/fail).
- `--debug-output`: flow별 **스크린샷**(flow 의 `takeScreenshot` + 실패 시 자동 캡처), `maestro.log`, `commands-*.json`.
- 포맷: `JUNIT`(CI 집계) · `HTML` · `HTML-DETAILED` · `NOOP`.
- 영상: `maestro record <flow>` → mp4.

### 6.2 실패 진단
`--debug-output`의 **실패 스크린샷 + maestro.log** 로 원인 확인(요소 미발견/크래시/다이얼로그 가림 등).
`maestro studio` 로 실제 UI 계층을 짚어 셀렉터를 보정한다.

## 7. CI (GitHub Actions)

**언제**: PR/`dev` 머지 시 UI 회귀 자동 차단. 처음엔 비차단→안정화 후 필수 체크. 무거우면 nightly.

```yaml
name: fe-acceptance
on: { pull_request: { paths: ['apps/frontend/**'] } }
jobs:
  maestro:
    runs-on: ubuntu-latest            # KVM 가속(에뮬레이터 필수)
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21' }
      - name: Install Maestro
        run: |
          curl -Ls "https://get.maestro.mobile.dev" | bash
          echo "$HOME/.maestro/bin" >> "$GITHUB_PATH"
      - name: Build mock APK
        working-directory: apps/frontend
        run: ./gradlew :app:assembleMockDebug
      - uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 34                # 안정 이미지(프리뷰 X → 시스템 노이즈 없음)
          arch: x86_64
          target: google_apis
          working-directory: apps/frontend
          script: |
            adb install -r app/build/outputs/apk/mock/debug/app-mock-debug.apk
            maestro test maestro --format JUNIT --output result.xml --debug-output maestro-debug
      - uses: actions/upload-artifact@v4
        if: always()
        with: { name: maestro-artifacts, path: "apps/frontend/result.xml\napps/frontend/maestro-debug" }
```
**대안**: CI 에뮬레이터 관리가 부담이면 Maestro Cloud/Robin(클라우드 디바이스·병렬·영상·대시보드).

## 8. 에뮬레이터 주의 (안정 이미지 권장)

**안정 API 34/35 + 기본 Gboard** 를 권장한다. Android 17 프리뷰에서 다음 노이즈가 관측되어 flow 에 방어 로직을 넣었다(안정 이미지에선 대부분 불필요):
- **16 KB 호환성 다이얼로그** → `Don't Show Again` 조건부 dismiss.
- **Gboard 스타일러스 온보딩("Try out your stylus")** → 입력창 포커스 시 전송 버튼 가림 → `Cancel` 조건부 dismiss.

## 9. 트러블슈팅 (스파이크에서 실제로 겪은 것)

| 증상 | 원인 | 해결 |
| ---- | ---- | ---- |
| 시작 즉시 크래시(홈으로) | AdMob `MobileAdsInitProvider` 가 `ADMOB_APP_ID` 무효로 프로세스 시작 시 크래시 | `app/src/mock/AndroidManifest.xml` 이 공개 테스트 App ID 로 대체 |
| 입력창 탭 후 전송 버튼 못 찾음 | 스타일러스 IME 팝업이 버튼 가림 | flow 가 `Try out your stylus` → `Cancel` 조건부 dismiss |
| `Unicode ... not supported` | Maestro `inputText` 한글 미지원(issue #146) | ASCII 입력(mock 응답 무관) |
| `hideKeyboard` 후 앱이 홈으로 | hideKeyboard 가 BACK → 앱 종료 | hideKeyboard 대신 스타일러스 dismiss 사용 |
| 오퍼월 카드 못 찾음 | 혜택존 하단이라 화면 밖 | `scrollUntilVisible` 로 노출 |
| 스크립트 "AVD 가 없음" | AVD 가 표준 위치(`~/.android/avd`)에 없음 | Android Studio 에서 에뮬레이터를 먼저 실행 후 `--no-boot`, 또는 `ANDROID_AVD_HOME`/`AVD` 설정 |

## 10. 현업 패턴 요약

- **로컬**: `maestro studio` 로 셀렉터 작성 → `maestro test <flow>` 빠른 확인.
- **이원화**: hermetic(이 방식)로 PR 게이팅 + 소규모 real/staging 스모크(nightly)로 계약 드리프트 감지.
- **태깅**: flow `tags` + `--include-tags/--exclude-tags` 로 smoke/full 분리.
- **재사용**: `runFlow` 로 공통 서브플로우(로그인·다이얼로그 dismiss·`_pause`) DRY, `config.yaml` 훅.
- **셀렉터 안정화**: 확대 시 `Modifier.testTag` + `testTagsAsResourceId=true`.
- **스케일**: `--shard-split N` 병렬 또는 Maestro Cloud.
