# Firebase Remote Config + Analytics 운영 가이드

Cash Chat 앱(Android·iOS)의 Firebase Remote Config / Analytics 사용·운영 방법을 정리한 문서입니다.
설계 배경은 [docs/superpowers/specs/2026-06-22-firebase-remote-config-analytics-design.md](../superpowers/specs/2026-06-22-firebase-remote-config-analytics-design.md),
AdMob 실 광고 전환은 [docs/admob-production-setup.md](../admob-production-setup.md)를 함께 보세요.

> 핵심: **AdMob 자체는 Firebase가 필요 없습니다.** Firebase는 (1) 코드 재배포 없이 광고/정책/기능을 런타임 제어(Remote Config),
> (2) 광고·채팅 이벤트 계측 + AdMob↔Firebase 수익 분석 연동(Analytics)을 위해 추가했습니다.

---

## 1. 동작 요약

값은 **계층형 폴백**으로 해석됩니다:

```
Remote Config(활성값)  →  빌드타임(BuildConfig / Secrets.swift)  →  Google 테스트 ID
```

- Remote Config 값이 있으면 그것을, 없거나 fetch 실패면 빌드타임 값을, 그것도 없으면 테스트 ID를 사용 → **첫 실행/오프라인/Firebase 장애에도 앱은 항상 동작**합니다.
- **AdMob 앱 ID**는 SDK 초기화에 필요해 빌드타임 전용입니다(RC로 교체 불가). 광고 단위 ID·on/off는 RC로 교체 가능합니다.

fetch/activate 전략(혼합):
- 앱 시작 시 **캐시된 값을 즉시 적용** → 이번 세션은 안정적으로 직전 값 사용(광고/정책이 세션 도중 안 바뀜).
- 동시에 최신 값을 받아 **긴급 키(점검/강제업데이트)만 즉시 재평가** → 점검 모드를 켜면 수 초 내 게이트 화면 표시.
- `minimumFetchInterval`: **dev=0**(즉시 테스트), **prod=12시간**.

---

## 2. Firebase 콘솔 준비 (1회)

[Firebase 콘솔](https://console.firebase.google.com)에서 프로젝트(`cash-chat-79ef7`)에 **Android 앱과 iOS 앱을 각각** 등록합니다.

| 플랫폼 | 등록 식별자 | 받는 파일 |
|---|---|---|
| Android | 패키지명 `com.nomadclub.cashchat` | `google-services.json` |
| iOS | 번들 ID `com.nomadlab.cashchat` | `GoogleService-Info.plist` |

> dev/prod를 별도 Firebase 프로젝트로 나눠 쓰려면 각 환경용 설정 파일을 따로 받아 환경별 시크릿으로 관리하세요(현재는 단일 프로젝트 기준).

Analytics는 콘솔에서 별도 활성화가 필요 없습니다(SDK 포함 시 자동). **AdMob↔Firebase 연동**은 [AdMob 콘솔](https://apps.admob.com) → 앱 설정 → "Firebase에 연결"에서 같은 Firebase 앱을 연결하면 됩니다.

---

## 3. 로컬 개발 세팅

두 설정 파일은 **gitignore 처리**되어 있어 커밋되지 않습니다. 콘솔에서 받아 아래 경로에 두세요:

| 플랫폼 | 경로 |
|---|---|
| Android | `apps/frontend/app/google-services.json` |
| iOS | `apps/frontend/CashChatIOS/CashChatIOS/GoogleService-Info.plist` |

- iOS는 동기화 그룹이라 파일만 폴더에 두면 Xcode가 자동으로 빌드에 포함합니다(프로젝트에 수동 추가 불필요).
- ⚠️ **파일이 없으면**: Android는 빌드 시 `google-services` 플러그인이 실패, iOS는 실행 시 `FirebaseApp.configure()`에서 크래시합니다. 반드시 배치하세요.

iOS Firebase SDK는 SPM으로 추가돼 있습니다. Xcode에서 프로젝트를 처음 열면 패키지 자동 resolve가 일어납니다(네트워크 필요). 수동: `File ▸ Packages ▸ Resolve Package Versions`.

---

## 4. GitHub Secrets 등록

CI는 설정 파일을 시크릿으로 주입합니다. **저장소 → Settings → Secrets and variables → Actions**.

| Secret | 값 | 사용 워크플로 |
|---|---|---|
| `GOOGLE_SERVICES_JSON` | `google-services.json` **원문 그대로** | Android (이미 사용 중) |
| `IOS_GOOGLE_SERVICE_INFO_PLIST` | `GoogleService-Info.plist`의 **base64** | iOS (신규) |

iOS plist는 base64로 인코딩해 등록합니다:

```bash
# macOS
base64 -i GoogleService-Info.plist | pbcopy   # 클립보드에 복사 → 시크릿 값으로 붙여넣기
# 또는 gh CLI
base64 -i GoogleService-Info.plist | gh secret set IOS_GOOGLE_SERVICE_INFO_PLIST --repo <owner>/cash-chat-mvp
```

> 시크릿이 없으면 빌드는 **더미 설정으로 통과**하지만 Firebase 기능(RC/Analytics)은 동작하지 않습니다.
> 실제로 쓰려면 반드시 등록하세요.

---

## 5. Remote Config 키 등록 (콘솔)

[Firebase 콘솔 → Remote Config](https://console.firebase.google.com)에서 아래 키를 추가합니다. 앱에는 동일한 인앱 기본값이 들어 있어, **콘솔에 키가 없어도 기본값으로 동작**합니다.

| 분류 | 키 | 타입 | 기본값 | 설명 |
|---|---|---|---|---|
| A | `ads_enabled` | Boolean | `true` | 광고 전체 on/off |
| A | `admob_banner_ad_unit_id` | String | (빈값) | 배너 단위 ID 오버라이드 |
| A | `admob_interstitial_ad_unit_id` | String | (빈값) | 전면 |
| A | `admob_native_ad_unit_id` | String | (빈값) | 네이티브 |
| A | `admob_rewarded_ad_unit_id` | String | (빈값) | 리워드 |
| B | `offerwall_enabled` | Boolean | `true` | 오퍼월 노출 |
| B | `maintenance_mode` | Boolean | `false` | 점검 모드(전체 차단) |
| B | `maintenance_message` | String | (빈값) | 점검 안내 문구 |
| B | `force_update_min_version` | String | (빈값) | 최소 요구 버전(빈값=비활성) |
| B | `force_update_message` | String | (빈값) | 강제 업데이트 안내 문구 |
| C | `ad_chat_interval` | Number | `1` | 채팅 N회마다 네이티브 광고 |
| C | `reward_chat_interval` | Number | `3` | 채팅 N회마다 리워드 광고 |
| C | `reward_required` | Boolean | `true` | 리워드 시청 필수 여부 |
| C | `interstitial_trigger_action` | String | `new_chat` | 전면 광고 트리거 액션 |

### 광고 단위 ID — 플랫폼 분리 (중요)

iOS·Android는 광고 단위 ID가 다릅니다. 코드의 키 이름은 같으므로, 콘솔에서 **조건(Condition)**으로 플랫폼별 값을 내려보냅니다:

1. Remote Config → **조건 만들기** → `Platform == Android`, `Platform == iOS` 두 개 생성.
2. 각 `admob_*_ad_unit_id` 키에서 조건별 값을 따로 지정(Android 값 / iOS 값).
3. 기본값은 비워 두면 앱이 빌드타임 ID로 폴백합니다.

> 비워 두면 빌드타임 시크릿(`ADMOB_*` / `IOS_ADMOB_*`)이 그대로 쓰입니다. RC로 굳이 안 덮어도 됩니다. RC는 "재배포 없이 바꾸고 싶을 때"만 채우세요.

값 변경 후 콘솔에서 **게시(Publish)** 해야 적용됩니다.

---

## 6. 코드에서 사용하기

### 설정값 읽기 (광고/정책)
양 플랫폼 모두 `AppConfig`가 폴백을 처리합니다.

```kotlin
// Android — Koin 주입
val appConfig: AppConfig = get()
appConfig.admobBannerAdUnitId   // RC→BuildConfig→테스트ID
appConfig.adsEnabled            // 광고 전체 on/off
appConfig.adChatInterval        // 정책 값
```
```swift
// iOS
AppConfig.admobBannerAdUnitId
AppConfig.adsEnabled
AppConfig.adChatInterval
```

### Analytics 이벤트
```kotlin
// Android — Koin 주입
val analytics: AnalyticsManager = get()
analytics.logAdView(adType = "banner", adUnitId = appConfig.admobBannerAdUnitId)
analytics.logRewardEarned(rewardType = "chat_unlock", amount = 10)
```
```swift
// iOS — 정적 호출
AnalyticsManager.logAdView(adType: "banner", adUnitId: AppConfig.admobBannerAdUnitId)
AnalyticsManager.logRewardEarned(rewardType: "chat_unlock", amount: 10)
```

지원 이벤트: `chat_start`, `chat_end`, `ad_view`, `ad_failed`, `reward_earned`, `chat_blocked` (양 플랫폼 동일).

---

## 7. 운영 시나리오

### 점검 모드 켜기
1. 콘솔에서 `maintenance_mode = true` (+ 필요 시 `maintenance_message`) → 게시.
2. 앱은 다음 fetch(긴급 키는 시작 시 즉시 평가) 때 **전체 차단 화면**을 띄웁니다.
3. 점검 종료: `maintenance_mode = false` → 게시.

### 강제 업데이트
1. `force_update_min_version`에 최소 요구 버전(예: `1.4.0`) 입력 → 게시.
2. 현재 앱 버전이 그보다 낮으면 업데이트 안내 게이트가 표시됩니다(버전 비교는 숫자 파트 기준).
3. 비활성화: 값을 비웁니다.

> **iOS 주의**: 강제 업데이트 버튼의 App Store 링크는 출시 후 채워야 합니다.
> [CashChatIOSApp.swift](../../apps/frontend/CashChatIOS/CashChatIOS/CashChatIOSApp.swift)의 `appStoreId` 상수에 App Store Connect에서 발급된 숫자 ID를 입력하세요.

### 광고 끄기 / 단위 교체 (재배포 없이)
- 전체 끄기: `ads_enabled = false`.
- 단위 교체: `admob_*_ad_unit_id`에 새 단위 ID 입력(플랫폼 조건 사용). 다음 실행부터 반영.

---

## 8. 배포 (재적용)

시크릿/RC는 워크플로 실행 시점 또는 콘솔 게시 시점에 적용됩니다.

- **설정 파일/SDK 변경** → 릴리즈 브랜치 푸시로 재빌드:
  - iOS: `release/ios` → `release-ios-distribute.yml`
  - Android: `release/android` → `release-android-distribute.yml`
- **RC 값만 변경** → 콘솔 게시만으로 적용(재배포 불필요).

---

## 9. 트러블슈팅

| 증상 | 원인 / 해결 |
|---|---|
| iOS 실행 즉시 크래시 (`FirebaseApp.configure`) | `GoogleService-Info.plist` 누락. 콘솔에서 받아 `CashChatIOS/CashChatIOS/`에 배치. |
| Android 빌드 실패 (`google-services` 플러그인) | `app/google-services.json` 누락 또는 패키지명 불일치(`com.nomadclub.cashchat`). |
| Manifest merger: `AD_SERVICES_CONFIG` 충돌 | AdMob/Analytics가 같은 프로퍼티 선언 — 매니페스트의 `tools:replace="android:resource"`로 해결됨(이미 적용). |
| RC 값이 안 바뀜 | 콘솔에서 **게시** 했는지, prod는 12h fetch 간격 때문에 캐시 사용 중일 수 있음(앱 재시작/재설치로 확인). dev 빌드는 즉시. |
| iOS SPM 패키지 못 찾음 | Xcode에서 `File ▸ Packages ▸ Resolve Package Versions` (네트워크 필요). |
| 광고가 테스트 광고로만 나옴 | RC·빌드타임 시크릿이 모두 비어 테스트 ID로 폴백 중. 시크릿/RC 값 확인. |

---

## 10. 관련 파일

| 항목 | 위치 |
|---|---|
| Android RC 매니저 | [RemoteConfigManager.kt](../../apps/frontend/app/src/main/java/com/nomadclub/cashchat/config/RemoteConfigManager.kt) |
| Android 키/기본값 | [RemoteConfigKeys.kt](../../apps/frontend/app/src/main/java/com/nomadclub/cashchat/config/RemoteConfigKeys.kt) |
| Android Analytics | [AnalyticsManager.kt](../../apps/frontend/app/src/main/java/com/nomadclub/cashchat/config/AnalyticsManager.kt) |
| Android AppConfig 폴백 | [AppConfig.kt](../../apps/frontend/app/src/main/java/com/nomadclub/cashchat/config/AppConfig.kt) |
| iOS RC 매니저 | [RemoteConfigManager.swift](../../apps/frontend/CashChatIOS/CashChatIOS/RemoteConfig/RemoteConfigManager.swift) |
| iOS 키/기본값 | [RemoteConfigKeys.swift](../../apps/frontend/CashChatIOS/CashChatIOS/RemoteConfig/RemoteConfigKeys.swift) |
| iOS Analytics | [AnalyticsManager.swift](../../apps/frontend/CashChatIOS/CashChatIOS/Analytics/AnalyticsManager.swift) |
| iOS AppConfig 폴백 | [AppConfig.swift](../../apps/frontend/CashChatIOS/CashChatIOS/AppConfig.swift) |
| 긴급 게이트 화면 | [AppGateScreen.kt](../../apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/gate/AppGateScreen.kt) · [AppGateView.swift](../../apps/frontend/CashChatIOS/CashChatIOS/Gate/AppGateView.swift) |
| CI 주입 (Android) | [release-android-distribute.yml](../../.github/workflows/release-android-distribute.yml) |
| CI 주입 (iOS) | [release-ios-distribute.yml](../../.github/workflows/release-ios-distribute.yml) · [ios-build-check.yml](../../.github/workflows/ios-build-check.yml) |
