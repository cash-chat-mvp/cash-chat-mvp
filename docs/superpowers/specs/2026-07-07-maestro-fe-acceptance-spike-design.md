# Maestro 기반 FE 인수/관통 테스트 도입 스파이크 — 설계

- **Jira**: [CC-391](https://moneyfactoryslave.atlassian.net/browse/CC-391) (Spike)
- **브랜치**: `spike/cc-391-maestro-fe-acceptance` (upstream/dev 기반)
- **날짜**: 2026-07-07
- **플랫폼**: Android 우선 (iOS 범위 외)

## 1. 가설 · 목표

> Maestro + **hermetic Fake**(`mock` product flavor)로 **백엔드 서버 없이** 핵심 3개 여정을
> GWT 인수 기준(Acceptance Criteria)에 따라 **관통/인수 검증**할 수 있는가?
> 도입 비용·한계·확대 권고를 규명한다.

스파이크의 산출물은 **동작하는 증거(green flow)** 와 **회고(비용·한계·권고)** 다.
전면 커버리지가 아니라 **도입 타당성 판단**이 목적이다.

## 2. 범위 (In / Out)

**In**
- Android 에뮬레이터 대상
- 대상 여정 3개: ① AI 채팅(SSE 스트리밍) ② 구글 보상형 광고(AdMob) ③ TNK 오퍼월
- 여정당 happy-path 1개 + 실패/경계 1개
- `mock` product flavor + Fake(네트워크 API·외부 SDK)
- GWT 인수 기준 문서(`docs/domains`)
- Maestro flow(`apps/frontend/maestro/`) + 로컬 러너/README

**Out**
- iOS (Maestro는 지원하나 Xcode/시뮬레이터 필요 → 스파이크 범위 외)
- 전체 여정 커버리지
- **실제 네트워크 계약 검증** — 기존 `shared/src/commonTest`의 KMM MockEngine 계약 테스트가 담당
- 실제 광고 노출/실제 SSV 콜백
- CI 파이프라인 완전 배선 (문서로 권고만)

## 3. 대상 여정의 기술적 제약 (핵심)

선정된 세 여정 중 **광고·오퍼월은 백엔드 HTTP만 목킹해서는 안 된다.**

| 여정 | 외부 경계 | 목킹해야 할 지점 |
| ---- | -------- | -------------- |
| AI 채팅 | 없음 (백엔드 SSE 스트림만) | `ChatApi`(SSE 토큰 스트림) |
| 구글 보상형 광고 | **AdMob 전체화면 광고 UI** + Google→BE **SSV 콜백** | 광고 presenter SDK 경계 + 잔액/quota API |
| TNK 오퍼월 | **TNK `AdWallActivity`** 외부 화면 + TNK→BE 콜백 | 오퍼월 launcher SDK 경계 + 토큰/잔액 API |

→ 광고·오퍼월은 Maestro가 실제 광고를 결정론적으로 "시청"할 수 없으므로,
**목킹 방식과 무관하게 앱 내부에서 SDK 경계를 Fake로 대체하는 것이 불가피**하다.
이 제약이 목킹 전략을 hermetic Fake로 결정한 근거다.

## 4. 목킹 전략 결정

**채택: `mock` product flavor + Fake (hermetic)**

- 외부 프로세스 0, 완전 결정론, CI 궁합 최적
- 세 여정(채팅·광고·오퍼월)을 하나의 일관된 메커니즘으로 커버
- 사용자 요구("백엔드 **서버를 사용하지 않고**")에 문자 그대로 부합
- 관통 깊이 손실(실제 네트워크/직렬화 계층 우회)은 기존 KMM MockEngine 계약 테스트로 보완

기각한 대안:
- **로컬 목 HTTP 서버(WireMock)**: 앱 코드 무변경 + 실제 네트워크 관통이 장점이나,
  광고/오퍼월 SDK를 못 막아 in-app Fake와 혼재하고 외부 프로세스/cleartext 설정이 필요.
- **Maestro 내장 네트워크 목킹**: 성숙도·SSE·SDK 제약으로 스파이크 실현성 리스크.

## 5. 아키텍처 · 컴포넌트

### 5.1 `mock` product flavor (`apps/frontend/app`)
- 신규 `flavorDimensions("backend")` + `real`(기본, 실서버) / `mock` 두 flavor
- `mock`은 `applicationIdSuffix = ".mock"`로 실앱과 단말 공존
- `src/mock/` 소스셋에서 Fake DI 모듈을 로드 (실 flavor는 기존 배선 유지)
- BuildConfig 플래그(`IS_MOCK`)로 앱 진입 시 Fake 모듈 주입 분기

### 5.2 외부 SDK Seam 추출 (최소 침습)
현재 두 매니저는 인터페이스 없는 구체 클래스이며 외부 SDK를 직접 호출한다. 각각 인터페이스 뒤로 분리:

| 대상 | 신규 인터페이스 | real 구현 | mock 구현 |
| ---- | ------------- | -------- | -------- |
| `RewardedAdManager` (AdMob) | `RewardedAdPresenter` | 기존 AdMob 로직 | 즉시 "보상 완료" 콜백 → Fake 적립 트리거 |
| `TnkOfferwallManager` (TNK) | `OfferwallLauncher` | 기존 TNK 로직 | 즉시 완료 → Fake 적립 트리거 |

- Koin `single` 등록을 인터페이스 기준으로 변경(real/mock flavor에서 각 구현 바인딩)
- 채팅은 SDK가 없으므로 `ChatApi`/repository 레벨에서 Fake(정해진 SSE 토큰 스트림 방출)

### 5.3 Fake 데이터 계층 (mock DI override)
- `ChatApi` · `AdsApi`(quota/nonce) · `OfferwallApi`(토큰) · 잔액(`PointsApi`)을 인메모리 Fake로 교체
- 보상 SSV는 "Fake presenter 완료 → Fake가 인메모리 잔액 증가"로 시뮬레이션 (실제 콜백 없음)
- 상태는 인메모리로 시나리오 시작 시 결정론적으로 초기화

### 5.4 시나리오 제어
- launch intent extra / deep link로 시나리오 선택 (예: `--es scenario ad_quota_exceeded`)
- 기본은 happy-path. 실패/경계 케이스(quota 소진, 토큰 발급 실패, 스트림 에러)를 구동

### 5.5 테스트 훅
- Maestro가 선택하는 Compose 노드에 `testTag`/`contentDescription` 최소 보강
- 텍스트로 안정적으로 선택 가능한 곳은 그대로 사용

### 5.6 Maestro flow 구조
```
apps/frontend/maestro/
  config.yaml                # 공통 설정(appId 등)
  flows/
    chat/ai-response.yaml            # US-CHAT-001 AC-FE-01
    chat/stream-error.yaml           # US-CHAT-001 AC-FE-02 (실패)
    rewarded-ad/watch-reward.yaml    # US-REWARD-002 AC-FE-01
    rewarded-ad/quota-exceeded.yaml  # US-REWARD-002 AC-FE-02 (경계)
    offerwall/complete-reward.yaml   # US-REWARD-003 AC-FE-01
    offerwall/token-fail.yaml        # US-REWARD-003 AC-FE-02 (실패)
  README.md                  # 빌드→설치→maestro test 러너 안내
```
- 각 flow 상단 주석에 대응 US/AC ID 역참조

## 6. 데이터 플로우 예시 (보상형 광고 happy-path)

```
홈(혜택존) → [지금 시청] 탭
  → Fake RewardedAdPresenter.show() 즉시 성공 콜백
  → Fake가 인메모리 잔액 +N & quota usedCount++
  → 앱이 잔액 갱신 UI 표시
  → Maestro: 잔액 텍스트/완료 표시 assert
```
실제 AdMob·SSV 없이 결정론적으로 관통.

## 7. 유저 스토리 / 인수 기준 (docs/domains)

기존 BE AC와 충돌하지 않도록 **FE 관통 AC는 `AC-FE-*` 접두어**로 추가한다.
GWT는 **UI 관측 가능**하게 작성한다(Given 앱 상태 / When UI 조작 / Then 화면 관측).

| 여정 | 문서 | 작업 |
| ---- | ---- | ---- |
| 보상형 광고 | `docs/domains/reward/US-REWARD-002-rewarded-ad.md` | `## FE 관통 인수 기준` 섹션 추가 (`AC-FE-01` happy, `AC-FE-02` quota 초과) |
| TNK 오퍼월 | `docs/domains/reward/US-REWARD-003-tnk-offerwall.md` | `## FE 관통 인수 기준` 섹션 추가 (`AC-FE-01` happy, `AC-FE-02` 토큰 실패) |
| AI 채팅 | 신규 `docs/domains/chat/US-CHAT-001-ai-chat-response.md` + `chat/README.md` + `chat/_glossary.md` | FE 여정 GWT AC (`AC-FE-01` 응답, `AC-FE-02` 스트림 에러) |

- 신규 `chat` 도메인은 `docs/domains/README.md` 도메인 인덱스 표에도 추가
- 각 US의 "검증 매핑(Verification)"에 FE: Maestro flow 경로를 역참조

## 8. 검증 · 완료 기준 (스파이크 Done)

- [ ] `./gradlew :app:assembleMockDebug` 빌드 성공
- [ ] 에뮬레이터에서 3개 여정 Maestro flow **green** (여정당 happy + 실패/경계 각 1)
- [ ] 각 flow가 US/AC ID 역참조
- [ ] 회고 기록(본 문서 §9 갱신): 도입 비용·한계·확대 권고

## 9. 회고 (스파이크 종료 시 작성)

- 도입 비용 (seam 추출·flavor·Fake 유지보수 부담):
- 한계 (관통 깊이, 실제 계약 드리프트 위험, Compose testTag 부채):
- 확대 권고 (다음 대상 여정, CI 배선 여부, real 서버 스모크 병행 여부):

## 10. 리스크 · 가정

- **가정**: 세 매니저(`RewardedAdManager`/`TnkOfferwallManager`)의 seam 추출이 기존 호출부에 파급이 적다.
- **리스크**: Compose 화면에 안정적 선택자(testTag)가 부족하면 flow가 취약 → 최소 보강 필요.
- **리스크**: SSE Fake가 실제 `ChatApi.streamMessage`의 done/에러 처리 경로를 충분히 재현하지 못할 수 있음 → 스트림 에러 시나리오로 최소 검증.
- **가정**: `mock` flavor의 applicationIdSuffix가 AdMob/기타 SDK 초기화와 충돌하지 않는다(mock에선 SDK 미초기화).
