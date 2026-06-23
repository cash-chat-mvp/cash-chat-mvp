# Firebase Remote Config + Analytics 연동 설계

- 작성일: 2026-06-22
- 대상: `apps/frontend/` (Android `:app`, iOS `CashChatIOS`)
- 관련 문서: [docs/admob-production-setup.md](../../admob-production-setup.md), [apps/frontend/CLAUDE.md](../../../apps/frontend/CLAUDE.md) (Epic C)

## 1. 배경 / 목표

현재 AdMob 광고 ID·기타 설정은 **빌드타임 주입**(Android `BuildConfig`, iOS `Secrets.swift`)으로만 동작한다. 코드 재배포 없이 광고/정책/기능을 런타임에 조정하고(Remote Config), 광고·채팅 핵심 이벤트를 계측(Analytics)하며 AdMob↔Firebase를 연동하는 것이 목표.

확인된 현재 상태:
- `google-services.json`은 이미 `.gitignore` 처리 + CI에서 `GOOGLE_SERVICES_JSON` 시크릿으로 주입 중. **그러나 `google-services` Gradle 플러그인과 Firebase SDK가 미적용**이라 소비되지 않음.
- iOS에는 `GoogleService-Info.plist`도, 주입 파이프라인도, Firebase SDK도 없음.
- iOS ATT(`NSUserTrackingUsageDescription` + `TrackingAuthorization.requestAtLaunchIfNeeded()`)는 이미 구현돼 있어 Analytics가 재활용.

## 2. 핵심 설계 결정

| # | 결정 | 내용 |
|---|---|---|
| Q2 | 정답 소스 | **계층형 폴백**: Remote Config(활성값) → 빌드타임(BuildConfig/Secrets) → Google 테스트 ID. 별도 RC defaults 파일 없이 AppConfig 체인으로 폴백 단일화. |
| Q3 | Analytics | **포함**. AdMob↔Firebase 연동 + RC 타게팅/A·B 테스트. iOS는 기존 ATT 재활용. |
| Q4 | fetch/activate | **혼합(C)**: 긴급 키(점검/강제업데이트)는 시작 시 동기 fetch→즉시 적용+게이트 화면; 일반 키는 백그라운드 fetch→다음 실행 적용. dev `minimumFetchInterval=0`, prod=12h. |
| Q5 | 구현 레이어 | **플랫폼별 네이티브(A)**. Android 코틀린 Firebase SDK(`:app`), iOS Swift Firebase SDK(SPM). 키 상수/문서는 공유. |

> 제약: AdMob **앱 ID**는 SDK 초기화에 필요해 Manifest/Info.plist 빌드타임 전용 — RC로 교체 불가.

## 3. Remote Config 키 스키마

플랫폼이 다른 광고 단위 ID는 Firebase 콘솔의 **Platform 조건**으로 같은 키가 플랫폼별 값으로 풀리게 한다(코드 키 상수는 양 플랫폼 동일).

| 분류 | 키 | 타입 | 기본 폴백 |
|---|---|---|---|
| A | `ads_enabled` | bool | `true` |
| A | `admob_banner_ad_unit_id` | string | 빌드타임→테스트ID |
| A | `admob_interstitial_ad_unit_id` | string | 빌드타임→테스트ID |
| A | `admob_native_ad_unit_id` | string | 빌드타임→테스트ID |
| A | `admob_rewarded_ad_unit_id` | string | 빌드타임→테스트ID |
| B | `offerwall_enabled` | bool | `true` |
| B | `maintenance_mode` | bool | `false` |
| B | `maintenance_message` | string | "" |
| B | `force_update_min_version` | string | "" (빈 값=비활성) |
| B | `force_update_message` | string | "" |
| C | `ad_chat_interval` | long | `1` |
| C | `reward_chat_interval` | long | `3` |
| C | `reward_required` | bool | `true` |
| C | `interstitial_trigger_action` | string | `new_chat` |

(C 항목은 기존 `apps/frontend/CLAUDE.md` Epic C-2 키와 정합.)

## 4. 컴포넌트

### Android (`:app`)
- `gradle/libs.versions.toml`: `google-services` 플러그인, Firebase BoM, `firebase-config`, `firebase-analytics` 추가.
- 루트/앱 `build.gradle.kts`: `google-services` 플러그인 적용.
- `config/RemoteConfigManager.kt`: fetch/activate 전략, 키 접근, 긴급 게이트 상태 산출.
- `config/AppConfig.kt`: RC→BuildConfig→테스트ID 폴백으로 확장.
- `config/AnalyticsManager.kt`: 이벤트 로깅 래퍼(Epic C-1 이벤트).
- `CashChatApplication.kt`: Firebase 초기화 + RC 초기 fetch 트리거.

### iOS (`CashChatIOS`)
- `project.pbxproj`: `firebase-ios-sdk` SPM 추가(`FirebaseRemoteConfig`, `FirebaseAnalytics`).
- `GoogleService-Info.plist`: 동기화 그룹 폴더에 위치(gitignore + CI 주입).
- `CashChatIOSApp.swift`: `FirebaseApp.configure()`.
- `RemoteConfig/RemoteConfigManager.swift`, `AppConfig.swift` 폴백 확장, `Analytics/AnalyticsManager.swift`.

### 긴급 게이트
`maintenance_mode==true` 또는 현재 버전 < `force_update_min_version`이면 루트에 전체 화면 차단 뷰 표시(Android Composable / SwiftUI View). 버전 비교는 semver 단순 비교.

## 5. CI / 시크릿

- iOS 신설: `IOS_GOOGLE_SERVICE_INFO_PLIST`(base64) → `release-ios-distribute.yml`·`ios-build-check.yml`에 "Write GoogleService-Info.plist" 스텝. build-check는 더미 폴백.
- `.gitignore`에 `GoogleService-Info.plist` 추가.
- Android: 기존 `GOOGLE_SERVICES_JSON` 재활용(플러그인 적용으로 소비됨). build-check는 더미 폴백 유지.

## 6. 개발자 로컬 워크플로우

설정 파일은 gitignore 유지. 개발자는 Firebase 콘솔에서 받아 정해진 경로에 둔다:
- Android: `apps/frontend/app/google-services.json`
- iOS: `apps/frontend/CashChatIOS/CashChatIOS/GoogleService-Info.plist`

`docs/guides/`에 상세 가이드 작성.

## 7. 테스트

- Android/iOS `RemoteConfigManager` 폴백 로직 단위 테스트(RC 있음/없음/실패).
- 두 build-check 워크플로우가 더미 설정으로 통과(Firebase 초기화 크래시 없음).
</invoke>
