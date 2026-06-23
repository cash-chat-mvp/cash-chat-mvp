# 혜택존 TNK 오퍼월 — 프론트엔드 연동 설계

> 성격: 설계/스펙 (FE에서 무엇을 어떻게 만들지)
> Jira: CC-355 · 브랜치: `feature/CC-355`
> 관련: [Confluence — 혜택존 TNK 오퍼월 구조와 동작 흐름](https://moneyfactoryslave.atlassian.net/wiki/spaces/FCTC/pages/23068755) · BE Jira CC-288

## 1. 배경 & 목표

백엔드는 이미 구현·배포 완료다(토큰 발급 API, 콜백 검증·멱등 적립, DB 원장). 현재 Android·iOS 혜택존에는 "TNK 오퍼월" 카드가 `dimmed`/`준비중(SOON)` 상태로만 남아 있다. 이 카드를 **실제 동작하는 오퍼월**로 전환하는 것이 이번 작업의 목표다.

오퍼월 흐름(BE 문서 §3 요약):
1. 앱이 BE에서 불투명 사용자 토큰을 발급받는다 (`POST /api/offerwall/tnk/user-token`, 인증 필요).
2. 앱이 TNK SDK `setUserName(token)` 설정 후 오퍼월 화면을 띄운다.
3. 사용자가 오퍼 완료 → TNK 서버가 BE로 S2S 포스트백 → BE가 검증·적립.
4. 적립은 **비동기**이므로 화면에 즉시 안 뜬다. 앱은 `GET /api/points/me`로 잔액을 새로고침해 반영한다 (현재 이 `refresh()` 호출 트리거가 없음 — 이번에 신설).

**범위**: Android·iOS **동시** 구현. 오퍼월 노출은 **TNK SDK 기본 전체화면 UI** 사용.

## 2. TNK SDK 연동 사실 (조사 결과)

| | Android | iOS |
|---|---|---|
| 배포 | `com.tnkfactory:rwd:8.09.07`, maven repo `https://repository.tnkad.net:8443/repository/public/` | `TnkRwdSdk2.xcframework` 수동 통합 (CocoaPods/SPM 아님 — Xcode 프로젝트 편집 필요) |
| App ID 설정 | Manifest `<meta-data android:name="tnkad_app_id" android:value="..."/>` | Info.plist `tnkad_app_id` 또는 `initInstance(appId:)` 파라미터 |
| 초기화 | `TnkSession.applicationStarted(context)` | `TnkSession.initInstance(appId:)` |
| 사용자 설정 | `TnkOfferwall.setUserName(token)` | `TnkSession.sharedInstance()?.setUserName(token)` |
| 오퍼월 노출 | `TnkOfferwall.startOfferwallActivity(activity)` (메인 스레드) | `AdOfferwallViewController` → `UINavigationController` fullScreen present |

## 3. 키 / 시크릿 구조

TNK 값은 성격이 다른 2종이며 **저장 위치가 다르다**.

| 키 | 성격 | 저장 위치 |
|---|---|---|
| `tnkad_app_id` (클라이언트 App ID) | 앱에 탑재되는 식별자 (Android·iOS 각 1개) | **앱 빌드 시크릿** |
| `app_key` (콜백 `md_chk` 서명용) | 서버 전용 공유 시크릿 | **백엔드 배포 env** (`app.offerwall.tnk.app-key`) — 앱은 보지 않음 |

**신규 Git 시크릿(앱 빌드용)**:
- `TNK_APP_ID_ANDROID` — TNK 콘솔 Android 앱 App ID
- `TNK_APP_ID_IOS` — TNK 콘솔 iOS 앱 App ID

흐름은 기존 AdMob 키와 동일:
- Android: `secrets → local.properties → buildConfigField(TNK_APP_ID) → AndroidManifest manifestPlaceholder`
- iOS: `secrets → Secrets.swift placeholder sed 치환`

**메모(범위 외)**: BE는 콜백 `app_key`를 **앱별로 분리할 예정**이며 **iOS 쪽 BE는 아직 미배포**다. 따라서 iOS 오퍼월 클라이언트(토큰 발급·SDK·UI)는 이번에 완성되지만, **iOS 오퍼 적립의 end-to-end 검증은 BE iOS app_key 배포 후**에 가능하다. iOS 토큰 발급 엔드포인트 자체는 플랫폼 무관하게 동작한다.

## 4. 컴포넌트 설계

### 4.1 아키텍처 결정

오케스트레이션을 **플랫폼 네이티브 매니저**에 둔다 (기존 AdMob `RewardedAdManager.kt`/`.swift` 패턴과 동일). shared는 토큰 발급 API만 얇게 제공한다. SDK가 네이티브 Activity/UIViewController를 직접 다루므로 `expect/actual`로 감싸면 브릿지만 늘어 비효율적이다.

### 4.2 KMM shared — 토큰 API

신규 `shared/src/commonMain/.../offerwall/OfferwallApi.kt`:

```kotlin
@Serializable
data class UserTokenDto(val token: String)

class OfferwallApi(private val client: HttpClient, private val baseUrl: String) {
    @Throws(Exception::class)
    suspend fun issueUserToken(): UserTokenDto =
        client.post("$baseUrl/api/offerwall/tnk/user-token").body()
}
```

- `@Throws(Exception::class)` 필수 — iOS에서 호출하는 suspend는 미지정 시 예외에 크래시(기존 `AdsApi` 패턴).
- 인증은 기존 `createCashChatHttpClient`의 `TokenProvider`가 자동 부착.
- Koin: `SharedModule.kt`에 `single { OfferwallApi(get(), baseUrl) }` 추가.
- iOS 노출: `IosBridges.kt`의 `KoinHelper`에 `offerwallApi()` 추가.

### 4.3 Android

- `app/build.gradle.kts`:
  - TNK maven repo (settings.gradle 또는 모듈 repositories) + `implementation("com.tnkfactory:rwd:8.09.07")`
  - `buildConfigField("String", "TNK_APP_ID", ...)` + `manifestPlaceholders["tnkAppId"]` ← `local.properties` `TNK_APP_ID`
  - `AppConfig`에 `tnkAppId` 필드 추가
- `AndroidManifest.xml`: `<meta-data android:name="tnkad_app_id" android:value="${tnkAppId}"/>`
- 초기화: `TnkSession.applicationStarted(context)` (Application 또는 MainActivity onCreate, AdMob init과 같은 위치)
- 신규 `app/.../offerwall/TnkOfferwallManager.kt`:
  - `suspend fun launch(activity: Activity)`: `offerwallApi.issueUserToken()` → `TnkOfferwall.setUserName(token)` → `TnkOfferwall.startOfferwallActivity(activity)`
  - 토큰 실패 시 토스트/로그 후 중단(오퍼월 미노출)
- `BenefitZoneScreen.kt`: TNK 카드 `dimmed=false`, 배지 `NEXT`, `onClick` → 매니저 실행 (Activity 컨텍스트 필요 — `LocalContext`/`LocalActivity`).

### 4.4 iOS

- `TnkRwdSdk2.xcframework` 수동 통합: 다운로드 → Xcode `Frameworks` 임베드 → `.pbxproj` 편집
- `Secrets.swift`: `static let tnkAppId = "TNK_APP_ID_IOS_PLACEHOLDER"`; Info.plist `tnkad_app_id`
- 초기화: `TnkSession.initInstance(appId: Secrets.tnkAppId)` (App 시작, AdMob init 위치)
- 신규 `CashChatIOS/.../Offerwall/TnkOfferwallManager.swift`:
  - 토큰 발급(`KoinHelper.offerwallApi().issueUserToken` suspend → Swift async 브리지) → `setUserName(token)` → `AdOfferwallViewController`를 `UINavigationController(fullScreen)`로 root VC에서 present
- `BenefitZoneScreen.swift`: TNK 카드 활성화(`dimmed: false`, `.next`), 탭 → present.

### 4.5 잔액·데이터 새로고침 (BE 문서 §5 해결)

두 가지 트리거를 양 플랫폼에 신설한다. 둘 다 **잔액 + 출석**을 함께 갱신한다 (`pointsRepository.refresh()` + `attendanceStore.loadMonthly()` — 둘 다 기존 메서드).

1. **on-resume**: 오퍼월(또는 앱)에서 혜택존으로 복귀 시 자동 갱신.
   - Android: 혜택존 화면에 `Lifecycle.Event.ON_RESUME` 옵저버.
   - iOS: `scenePhase == .active` 또는 오퍼월 dismiss 콜백.
2. **pull-to-refresh**: 혜택존 화면을 아래로 당기면 수동 갱신.
   - Android: `PullToRefreshBox`(Material3)로 `LazyColumn` 감싸기.
   - iOS: `ScrollView` + `.refreshable { }`.

## 5. 작업/CI 변경

- `release-android-distribute.yml`: `local.properties`에 `TNK_APP_ID=${{ secrets.TNK_APP_ID_ANDROID }}` 추가.
- `release-ios-distribute.yml`: `Secrets.swift` 생성/sed 블록에 `TNK_APP_ID_IOS` placeholder·치환 추가.
- `android-build-check.yml` / `ios-build-check.yml`: TNK 의존성으로 빌드 깨지지 않는지 확인(시크릿 없으면 빈 값/테스트 동작 fallback 고려).

## 6. 테스트 전략

- shared `OfferwallApi`: 기존 `AdsApiTest`/`ApiErrorTest` 패턴으로 MockEngine 단위 테스트 (정상 200 `{token}` 파싱, 에러 전파).
- 새로고침 로직: 가능하면 화면 무관 함수로 추출해 단위 테스트, 아니면 수동 검증.
- 네이티브 SDK·오퍼월 UI: 자동 테스트 어려움 → 수동 검증 (토큰 발급 호출 확인, 오퍼월 노출, 복귀 시 잔액 갱신).

## 7. 검증 (인수 기준)

- [ ] Android: 혜택존 TNK 카드 탭 → 토큰 발급 → 오퍼월 전체화면 노출 → 복귀 시 잔액·출석 갱신.
- [ ] Android: 혜택존 당겨서 새로고침으로 잔액·출석 갱신.
- [ ] iOS: 동일 흐름 (단, 실제 적립 검증은 BE iOS app_key 배포 후).
- [ ] `TNK_APP_ID_ANDROID`/`TNK_APP_ID_IOS` 시크릿 미설정 시에도 앱 빌드·부팅 정상(오퍼월만 비동작).
- [ ] `OfferwallApi` 단위 테스트 통과, 기존 빌드/테스트 무회귀.

## 8. 범위 외 / 후속

- BE `app_key` 앱별 분리 및 iOS BE 배포 (BE/운영).
- dev/prod 콜백 URL TNK 콘솔 등록 (운영).
- 오퍼월 자동 취소/환수(claw-back), 추가 오퍼월(Buzzvil 등).
- iOS xcframework 통합은 `.pbxproj` 편집이라 CLI만으로 빌드 검증이 어려움 — 실기기/Xcode 빌드 확인 단계를 별도로 둔다.
