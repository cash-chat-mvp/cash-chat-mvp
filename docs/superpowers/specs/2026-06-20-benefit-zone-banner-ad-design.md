# 배너 광고 연동 설계 (혜택존 추가 컨텐츠 슬라이스 1)

- 작성일: 2026-06-20
- 브랜치: feature/CC-355 후속
- 상태: 설계 확정 대기

## 배경

혜택존에 오퍼월(TNK) 외 추가 적립/수익 컨텐츠를 확장하는 작업의 첫 슬라이스다.
전체 로드맵은 ROI·의존성 순으로 4개 슬라이스로 분해하여 각각 독립 spec → plan → 구현 사이클로 진행한다.

| 순서 | 슬라이스 | 백엔드 | 본 문서 |
|---|---|---|---|
| **1** | **배너 광고** (채팅 헤더 아래 + 혜택존 출석 아래) | 불필요 | ✅ 본 문서 |
| 2 | 리워드 광고 혜택존 카드 | 기존 quota API 재사용 | 별도 spec |
| 3 | 행운 룰렛/복권 | 신규 도메인 | 별도 spec |
| 4 | 친구 초대(리퍼럴) | 신규 도메인 | 별도 spec |

본 슬라이스를 첫 번째로 둔 이유: 백엔드 의존이 없고, AdMob SDK·배너 광고 단위 ID가
이미 코드베이스에 들어와 있어 가장 독립적이며 즉시 수익화 가능하기 때문이다.

## 현재 상태 (조사 결과)

- Android `AppConfig.admobBannerAdUnitId` 가 이미 존재(`BuildConfig` 경로로 flavor별 주입). **Android 신규 키 불필요.**
- iOS `AppConfig.swift`/`Secrets.swift` 에는 **배너 단위 ID 없음**(rewarded만 존재) → iOS는 `admobBannerAdUnitId` 키 신규 추가 필요.
- 플래그 기구는 Firebase Remote Config가 아니라 컴파일타임 `FeatureFlags` object(`shared/.../core/config/FeatureFlags.kt`, `const val`). 배너 플래그도 여기에 맞춘다.
- 리워드 광고용 `RewardedAdManager`(Android, SSV 포함)·`AdRewardStore`(KMM, quota/nonce/폴링)는 존재하나
  배너 인프라는 **0%**. 배너 Composable / `expect-actual` / iOS 뷰 모두 신규.
- iOS는 GoogleMobileAds SDK 이미 벤더링됨(파리티 작업으로 빌드에 포함).
- 적립 정책: 본 슬라이스는 **적립 없음**(배너는 노출형 수익이므로 코인 지급/서버 권위 흐름 무관).

## 비기능 전제

- **개발 빌드는 Google 공식 테스트 배너 ID 강제.** 실광고를 dev에서 클릭하면 AdMob 계정 정지 위험.
- 실광고 전환은 코드가 아닌 운영/심사 영역(실 광고 단위 생성, prod ID 주입, 스토어 심사, app-ads.txt,
  iOS SKAdNetwork ID 등). 본 spec은 **코드 작업만** 다루며 실 ID 전환은 릴리즈 체크리스트로 분리한다.

## 1. 아키텍처

광고 뷰 자체는 플랫폼 UI라 KMM commonMain에 두지 않고 각 플랫폼 UI 레이어에 둔다.
공통 로직(슬롯 식별·플래그)만 shared에 둔다.

```
shared/commonMain/.../ads/
  BannerAdSlot.kt        // enum class: CHAT_TOP, BENEFIT_TOP — Analytics/플래그 식별용
                         // (광고 단위 ID는 슬롯 공통, 슬롯은 추적 구분 목적)

app/.../ads/
  BannerAd.kt            // @Composable, AndroidView(AdView) 래핑, adaptive anchored size

CashChatIOS/.../Ads/
  BannerAdView.swift     // UIViewRepresentable(GADBannerView), adaptive size
```

광고 단위 ID는 채팅/혜택존 공통으로 `AppConfig.admobBannerAdUnitId` 재사용.
슬롯 enum 은 Analytics 이벤트 파라미터(`ad_slot`)와 위치별 플래그 분기에만 쓴다.

## 2. 컴포넌트 동작

- **Adaptive anchored banner** 사용(고정 320x50 대신 화면폭 기반 적응형 높이).
- 로딩 중: 배너 높이만큼 placeholder 공간 확보 → 로드 완료 시 레이아웃 점프 방지.
- `onAdFailedToLoad`: 슬롯 자체를 숨김(높이 0). 광고 실패가 화면 레이아웃을 깨지 않게 한다.
  실패는 Sentry + Firebase Analytics 로깅(기존 광고 실패 로깅 패턴 준수).
- Compose 재구성 시 `AdView` 재생성 방지: `remember` 로 인스턴스 보존.
- `onDispose` 에서 `adView.destroy()`(Android) / iOS는 뷰 해제 시 정리 — 메모리 누수 방지.

## 3. 노출 위치

- 채팅: 슬림 톱바 `Row`(`ChatScreen.kt:121`~194) 아래 `HorizontalDivider`(`ChatScreen.kt:195`)
  **바로 아래**, 메시지 리스트 `Box`(198) **위**에 `BannerAd(CHAT_TOP)` 삽입.
- 혜택존: `AttendanceWidget` item(`BenefitZoneScreen.kt:111`) **바로 아래** 신규 `item { BannerAd(BENEFIT_TOP) }`.

## 4. 정책 (FeatureFlags)

코드베이스는 Firebase Remote Config가 아닌 컴파일타임 `FeatureFlags` object를 쓴다. 이 패턴을 따른다.

`shared/.../core/config/FeatureFlags.kt` 에 추가:

```kotlin
const val BANNER_ADS = true   // 배너 광고 전역 on/off
```

배너 Composable/뷰는 `FeatureFlags.BANNER_ADS == false` 면 슬롯을 렌더하지 않는다.
런타임 Remote Config 전환·위치별 분리 플래그(`banner_chat` 등)는 YAGNI로 보류(후속).

## 5. iOS 파리티

iOS 파리티 프로젝트 원칙에 따라 본 슬라이스에 iOS(`BannerAdView.swift`) 포함.
채팅 화면 헤더 아래 / 혜택존 출석 아래 동일 위치에 배치.

## 6. 테스트 / 검증

- 개발 빌드에서 Google 테스트 배너 단위 ID 적용 확인.
- `BannerAdSlot` enum 및 플래그 분기(`FeatureFlags.BANNER_ADS` false → 슬롯 미노출) commonTest 단위 테스트.
- 실패 시 슬롯 숨김(높이 0) 동작은 수동/계측 QA.
- 레이아웃 점프 없음(placeholder) 시각 확인.

## 7. 범위 밖 (Out of scope)

- 실제 광고 단위 ID 생성·prod 주입·스토어 심사·app-ads.txt·결제 정보 등록 (운영 영역, 릴리즈 체크리스트).
- AppLovin MAX 미디에이션 (트래픽 적재 후 별도 검토).
- 리워드 광고 혜택존 카드 / 룰렛 / 리퍼럴 (후속 슬라이스).

## 릴리즈 체크리스트 (실광고 전환 시)

- [ ] AdMob 콘솔에서 실제 배너 광고 단위 생성 → 단위 ID 발급
- [ ] prod flavor 에 실 ID 주입(`local.properties` 또는 CI secret)
- [ ] iOS `Info.plist` SKAdNetwork ID 추가
- [ ] app-ads.txt / 결제·세금 정보 등록(AdMob 지급 조건)
- [ ] 스토어 심사 통과 후 실광고 노출 확인
