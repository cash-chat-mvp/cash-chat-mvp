# 혜택존 TNK 오퍼월 프론트엔드 연동 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Android·iOS 혜택존의 "준비중" TNK 오퍼월 카드를 실제 동작하는 오퍼월(토큰 발급 → TNK SDK 노출 → 복귀 시 잔액·출석 새로고침)로 전환한다.

**Architecture:** KMM `shared`는 BE 토큰 발급 API(`OfferwallApi`)만 얇게 제공한다. 오퍼월 노출 오케스트레이션(토큰 발급 → `setUserName` → 오퍼월 화면)은 기존 AdMob `RewardedAdManager`와 동일하게 **플랫폼 네이티브 매니저**가 담당한다. 혜택존에는 on-resume + pull-to-refresh 트리거를 신설해 비동기 적립을 화면에 반영한다.

**Tech Stack:** Kotlin Multiplatform, Ktor, Koin, Jetpack Compose (Material3), SwiftUI, TNK SDK (Android `com.tnkfactory:rwd:8.09.07` / iOS `TnkRwdSdk2.xcframework`)

**참고:** 설계 문서 `docs/superpowers/specs/2026-06-20-tnk-offerwall-frontend-design.md`. BE는 구현·배포 완료. 엔드포인트 `POST /api/offerwall/tnk/user-token` → `{ "token": "..." }` (인증 필요).

---

## File Structure

**shared (신규/수정):**
- Create `shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/offerwall/OfferwallApi.kt` — 토큰 발급 API + DTO
- Create `shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/offerwall/OfferwallApiTest.kt` — MockEngine 단위 테스트
- Modify `shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/di/SharedModule.kt` — Koin 등록
- Modify `shared/src/iosMain/kotlin/com/nomadclub/cashchat/shared/di/IosBridges.kt` — `KoinHelper.offerwallApi()`

**Android (신규/수정):**
- Modify `settings.gradle.kts` — TNK maven repo
- Modify `app/build.gradle.kts` — TNK 의존성, `TNK_APP_ID` BuildConfig/manifestPlaceholder
- Modify `app/src/main/AndroidManifest.xml` — `tnkad_app_id` meta-data
- Modify `app/src/main/java/com/nomadclub/cashchat/config/AppConfig.kt` — `tnkAppId`
- Modify `app/src/main/java/com/nomadclub/cashchat/CashChatApplication.kt` — `TnkSession.applicationStarted`
- Create `app/src/main/java/com/nomadclub/cashchat/offerwall/TnkOfferwallManager.kt` — 오케스트레이션
- Modify `app/src/main/java/com/nomadclub/cashchat/feature/rewards/BenefitZoneScreen.kt` — 카드 활성화 + pull-to-refresh + on-resume

**iOS (신규/수정):**
- Add `TnkRwdSdk2.xcframework` (Xcode 임베드, `.pbxproj`)
- Modify `CashChatIOS/CashChatIOS/Secrets.swift`(+`.example`), `AppConfig.swift` — `tnkAppId`
- Modify `CashChatIOS/CashChatIOS/Info.plist` — `tnkad_app_id`
- Modify `CashChatIOS/CashChatIOS/CashChatIOSApp.swift` — `TnkSession.initInstance`
- Create `CashChatIOS/CashChatIOS/Offerwall/TnkOfferwallManager.swift` — 오케스트레이션
- Modify `CashChatIOS/CashChatIOS/BenefitZoneScreen.swift` — 카드 활성화 + `.refreshable` + scenePhase
- Modify `CashChatIOS/CashChatIOS/BenefitZone/AttendanceViewModel.swift` — `refresh()`

**CI (수정):**
- Modify `.github/workflows/release-android-distribute.yml`, `.github/workflows/release-ios-distribute.yml`

---

## Phase 1 — shared: 토큰 발급 API (TDD)

### Task 1: OfferwallApi + DTO

**Files:**
- Create: `shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/offerwall/OfferwallApi.kt`
- Test: `shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/offerwall/OfferwallApiTest.kt`

- [ ] **Step 1: 실패하는 테스트 작성**

`OfferwallApiTest.kt`:

```kotlin
package com.nomadclub.cashchat.shared.offerwall

import com.nomadclub.cashchat.shared.core.network.ApiException
import com.nomadclub.cashchat.shared.core.network.TokenProvider
import com.nomadclub.cashchat.shared.core.network.createCashChatHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private object NoAuth : TokenProvider {
    override suspend fun accessToken(): String? = null
    override suspend fun refresh(): Boolean = false
}

private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

class OfferwallApiTest {

    @Test
    fun `user-token 을 POST 하고 token 을 파싱한다`() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/offerwall/tnk/user-token", request.url.encodedPath)
            respond("""{"token":"opaque-abc-123"}""", HttpStatusCode.OK, jsonHeaders)
        }
        val api = OfferwallApi(createCashChatHttpClient("https://api.test", NoAuth, engine), "https://api.test")
        assertEquals("opaque-abc-123", api.issueUserToken().token)
    }

    @Test
    fun `4xx 응답은 ApiException 으로 던진다`() = runTest {
        val engine = MockEngine {
            respond("""{"code":"UNAUTHORIZED","message":"x"}""", HttpStatusCode.Unauthorized, jsonHeaders)
        }
        val api = OfferwallApi(createCashChatHttpClient("https://api.test", NoAuth, engine), "https://api.test")
        assertFailsWith<ApiException> { api.issueUserToken() }
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd apps/frontend && ./gradlew :shared:testDebugUnitTest --tests "*OfferwallApiTest*"`
Expected: FAIL — `OfferwallApi` / `issueUserToken` 미정의 컴파일 에러.

> 참고: `createCashChatHttpClient(baseUrl, tokenProvider, engine)`의 3-인자 시그니처가 기존에 있는지 `shared/src/commonMain/.../core/network/HttpClientFactory.kt`로 확인한다. `ChatApiTest`가 동일하게 호출하므로 존재한다. 4xx→`ApiException` 변환도 이 클라이언트가 담당한다(`ApiErrorTest` 참고).

- [ ] **Step 3: 최소 구현**

`OfferwallApi.kt`:

```kotlin
package com.nomadclub.cashchat.shared.offerwall

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import kotlinx.serialization.Serializable

@Serializable
data class UserTokenDto(val token: String)

/**
 * TNK 오퍼월 사용자 토큰 발급 API.
 * 사용자당 안정적인 불투명 토큰(get-or-create)을 받아 TNK SDK setUserName 에 사용한다.
 */
class OfferwallApi(private val client: HttpClient, private val baseUrl: String) {
    // iOS 에서 호출하는 suspend 는 @Throws 가 없으면 예외 발생 시 앱이 크래시한다.
    @Throws(Exception::class)
    suspend fun issueUserToken(): UserTokenDto =
        client.post("$baseUrl/api/offerwall/tnk/user-token").body()
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd apps/frontend && ./gradlew :shared:testDebugUnitTest --tests "*OfferwallApiTest*"`
Expected: PASS (2 tests)

- [ ] **Step 5: 커밋**

```bash
git add apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/offerwall apps/frontend/shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/offerwall
git commit -m "feat(offerwall): TNK 오퍼월 사용자 토큰 발급 API 추가"
```

### Task 2: Koin 등록 + iOS 브릿지

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/di/SharedModule.kt`
- Modify: `shared/src/iosMain/kotlin/com/nomadclub/cashchat/shared/di/IosBridges.kt`

- [ ] **Step 1: SharedModule 에 single 등록**

`SharedModule.kt`의 `single { AttendanceApi(get(), baseUrl) }` 줄 아래에 추가:

```kotlin
    single { com.nomadclub.cashchat.shared.offerwall.OfferwallApi(get(), baseUrl) }
```

- [ ] **Step 2: KoinHelper 에 접근자 추가**

`IosBridges.kt`의 `KoinHelper` 클래스 안, `private val shopApiInstance: ShopApi by inject()` 아래에 추가:

```kotlin
    private val offerwallApiInstance: com.nomadclub.cashchat.shared.offerwall.OfferwallApi by inject()
```

그리고 `fun shopApi(): ShopApi = shopApiInstance` 아래에 추가:

```kotlin
    fun offerwallApi(): com.nomadclub.cashchat.shared.offerwall.OfferwallApi = offerwallApiInstance
```

- [ ] **Step 3: shared 컴파일 확인**

Run: `cd apps/frontend && ./gradlew :shared:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/di/SharedModule.kt apps/frontend/shared/src/iosMain/kotlin/com/nomadclub/cashchat/shared/di/IosBridges.kt
git commit -m "feat(offerwall): OfferwallApi Koin 등록 및 iOS 브릿지 노출"
```

---

## Phase 2 — Android

### Task 3: Gradle 의존성 + App ID 주입

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/nomadclub/cashchat/config/AppConfig.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `local.properties` (로컬 — 커밋 안 됨)

- [ ] **Step 1: TNK maven repo 추가**

`settings.gradle.kts`의 `dependencyResolutionManagement { repositories { google(); mavenCentral() } }` 블록에 추가:

```kotlin
        maven { url = uri("https://repository.tnkad.net:8443/repository/public/") }
```

- [ ] **Step 2: app/build.gradle.kts 에 의존성 + BuildConfig 추가**

`dependencies { }` 블록에 추가:

```kotlin
    implementation("com.tnkfactory:rwd:8.09.07")
```

`defaultConfig` 의 `buildConfigField("String", "SENTRY_DSN", ...)` 줄 아래에 추가:

```kotlin
        buildConfigField("String", "TNK_APP_ID", "\"${localProperties.getProperty("TNK_APP_ID", "")}\"")
        manifestPlaceholders["tnkAppId"] = localProperties.getProperty("TNK_APP_ID", "")
```

- [ ] **Step 3: AppConfig 에 필드 추가**

`AppConfig.kt`의 `sentryDsn: String,` 아래에 `val tnkAppId: String,` 추가하고, `fromBuildConfig()` 의 `sentryDsn = BuildConfig.SENTRY_DSN,` 아래에 추가:

```kotlin
            tnkAppId = BuildConfig.TNK_APP_ID,
```

- [ ] **Step 4: AndroidManifest 에 meta-data 추가**

`AndroidManifest.xml`의 `<application>` 안 (기존 `admobAppId` meta-data 옆)에 추가:

```xml
        <meta-data
            android:name="tnkad_app_id"
            android:value="${tnkAppId}" />
```

- [ ] **Step 5: local.properties 에 테스트용 값 추가 (로컬 검증용)**

`apps/frontend/local.properties`에 한 줄 추가 (실제 TNK 콘솔 Android App ID, 없으면 빈 값으로 두면 manifestPlaceholder 가 빈 문자열이 됨 — 빌드는 통과):

```
TNK_APP_ID=<TNK 콘솔 Android App ID>
```

- [ ] **Step 6: 빌드 확인**

Run: `cd apps/frontend && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL (TNK 의존성 resolve, manifest merge 성공)

- [ ] **Step 7: 커밋** (local.properties 는 제외)

```bash
git add apps/frontend/settings.gradle.kts apps/frontend/app/build.gradle.kts apps/frontend/app/src/main/java/com/nomadclub/cashchat/config/AppConfig.kt apps/frontend/app/src/main/AndroidManifest.xml
git commit -m "build(offerwall): Android TNK SDK 의존성 및 tnkad_app_id 주입 구성"
```

### Task 4: TNK 초기화 + 오퍼월 매니저

**Files:**
- Modify: `app/src/main/java/com/nomadclub/cashchat/CashChatApplication.kt`
- Create: `app/src/main/java/com/nomadclub/cashchat/offerwall/TnkOfferwallManager.kt`

- [ ] **Step 1: Application 에서 TNK 초기화**

`CashChatApplication.kt`의 `MobileAds.initialize(this)` 아래에 추가:

```kotlin
        com.tnkfactory.ad.TnkSession.applicationStarted(this)
```

(import 경로는 SDK가 `com.tnkfactory.ad.TnkSession` 또는 `com.tnkfactory.ad.rwdplus.TnkSession`일 수 있으니, 빌드 후 IDE 자동완성으로 실제 패키지를 확정한다. Android_Guide.md 기준 `TnkSession.applicationStarted(Context)`.)

- [ ] **Step 2: 오퍼월 매니저 작성**

`TnkOfferwallManager.kt`:

```kotlin
package com.nomadclub.cashchat.offerwall

import android.app.Activity
import android.util.Log
import com.nomadclub.cashchat.shared.offerwall.OfferwallApi
import com.tnkfactory.ad.TnkOfferwall

/**
 * TNK 오퍼월 노출 오케스트레이션.
 *  1. BE 에서 불투명 사용자 토큰 발급
 *  2. TNK SDK setUserName 에 토큰 설정
 *  3. 오퍼월 전체화면(AdWallActivity) 노출
 *
 * 토큰 발급 실패 시 오퍼월을 띄우지 않는다(잘못된 사용자로 적립되는 사고 방지).
 */
class TnkOfferwallManager(private val offerwallApi: OfferwallApi) {

    /** 호출 전 토큰 발급(suspend)을 마쳐야 하므로 코루틴 컨텍스트에서 호출한다. */
    suspend fun launch(activity: Activity): Result<Unit> = runCatching {
        val token = offerwallApi.issueUserToken().token
        TnkOfferwall.setUserName(token)
        TnkOfferwall.startOfferwallActivity(activity) // 메인 스레드에서 호출됨(상위에서 보장)
    }.onFailure {
        Log.e("TnkOfferwall", "오퍼월 진입 실패", it)
    }
}
```

(`TnkOfferwall` 의 실제 패키지/메서드명은 Android_Guide.md 기준 `TnkOfferwall.setUserName(String)`, `TnkOfferwall.startOfferwallActivity(Activity)`. 빌드 후 import 확정.)

- [ ] **Step 3: Koin 에 매니저 등록**

`app/src/main/java/com/nomadclub/cashchat/di/AppModule.kt`의 `module { }` 안에 추가 (기존 `RewardedAdManager` 등록부 옆):

```kotlin
    single { com.nomadclub.cashchat.offerwall.TnkOfferwallManager(get()) }
```

(`OfferwallApi`는 `sharedDataModule`에서 이미 `single`로 제공되므로 `get()`으로 주입된다.)

- [ ] **Step 4: 빌드 확인**

Run: `cd apps/frontend && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add apps/frontend/app/src/main/java/com/nomadclub/cashchat/CashChatApplication.kt apps/frontend/app/src/main/java/com/nomadclub/cashchat/offerwall/TnkOfferwallManager.kt apps/frontend/app/src/main/java/com/nomadclub/cashchat/di/AppModule.kt
git commit -m "feat(offerwall): Android TNK 초기화 및 오퍼월 매니저 추가"
```

### Task 5: 혜택존 카드 활성화 + on-resume + pull-to-refresh (Android)

**Files:**
- Modify: `app/src/main/java/com/nomadclub/cashchat/feature/rewards/BenefitZoneScreen.kt`

- [ ] **Step 1: 새로고침 + 오퍼월 진입 로직과 pull-to-refresh 추가**

`BenefitZoneScreen.kt` 를 아래로 교체한다. 변경점: (a) `TnkOfferwallManager` 주입, (b) `coroutineScope`/`activity` 확보, (c) TNK 카드 `dimmed=false`·실제 진입, (d) `PullToRefreshBox`로 감싸 잔액+출석 동시 새로고침, (e) `ON_RESUME` 옵저버로 복귀 시 새로고침.

```kotlin
package com.nomadclub.cashchat.feature.rewards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nomadclub.cashchat.offerwall.TnkOfferwallManager
import com.nomadclub.cashchat.shared.attendance.AttendanceStore
import com.nomadclub.cashchat.shared.points.PointsRepository
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenefitZoneScreen(
    store: AttendanceStore = koinInject(),
    pointsRepository: PointsRepository = koinInject(),
    offerwallManager: TnkOfferwallManager = koinInject(),
) {
    val state by store.state.collectAsState()
    val balance by pointsRepository.balance.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    // 잔액 + 출석을 함께 새로고침 (오퍼월 적립은 비동기이므로 복귀/당겨서새로고침 시 반영).
    suspend fun refreshAll() {
        runCatching { pointsRepository.refresh() }
        store.loadMonthly()
    }

    LaunchedEffect(Unit) { store.loadMonthly() }
    LaunchedEffect(Unit) {
        store.rewardEvents.collect { ev ->
            Toast.makeText(context, "출석 완료! 🪙+${ev.awardedCoin}", Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
    }

    // 오퍼월 등에서 복귀(ON_RESUME) 시 자동 새로고침.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) scope.launch { refreshAll() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                refreshAll()
                isRefreshing = false
            }
        },
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("혜택존", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        "🪙 $balance",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFFB07C00),
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(Color(0xFFFFF7E6))
                            .padding(horizontal = 11.dp, vertical = 5.dp),
                    )
                }
            }
            item { AttendanceWidget(state = state, onCheckIn = store::checkIn) }
            item {
                BenefitInfoCard(
                    icon = "📺", title = "리워드 광고", badge = BenefitBadge.NEXT,
                    description = "광고 1회 시청 → 🪙+40 코인 · 하루 10회까지",
                    dimmed = false,
                    onClick = { Toast.makeText(context, "곧 만나요!", Toast.LENGTH_SHORT).show() },
                )
            }
            item {
                BenefitInfoCard(
                    icon = "🎯", title = "데일리 미션", badge = BenefitBadge.SOON,
                    description = "매일 바뀌는 3가지 미션을 완료하고 코인 적립",
                    dimmed = true,
                    onClick = { Toast.makeText(context, "곧 만나요!", Toast.LENGTH_SHORT).show() },
                )
            }
            item {
                BenefitInfoCard(
                    icon = "🎮", title = "TNK 오퍼월", badge = BenefitBadge.NEXT,
                    description = "앱 설치·설문 참여로 대량 코인 (최대 🪙+1,500)",
                    dimmed = false,
                    onClick = {
                        val activity = context.findActivity()
                        if (activity == null) {
                            Toast.makeText(context, "오퍼월을 열 수 없어요", Toast.LENGTH_SHORT).show()
                        } else {
                            scope.launch {
                                offerwallManager.launch(activity).onFailure {
                                    Toast.makeText(context, "오퍼월 진입에 실패했어요", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}
```

- [ ] **Step 2: 빌드 확인**

Run: `cd apps/frontend && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

> `PullToRefreshBox`는 Material3 `1.3.0+`에 있다. 컴파일 실패 시 `gradle/libs.versions.toml`의 `compose-bom`/`material3` 버전을 확인하고, 구버전이면 `androidx.compose.material3:material3:1.3.x`로 올린다(같은 PR 범위 내 최소 변경).

- [ ] **Step 3: 커밋**

```bash
git add apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/rewards/BenefitZoneScreen.kt
git commit -m "feat(offerwall): 혜택존 TNK 카드 활성화·당겨서새로고침·복귀 새로고침 (Android)"
```

### Task 6: Android 수동 검증

- [ ] **Step 1: 디바이스/에뮬레이터에서 동작 확인**

`local.properties`에 실제 `TNK_APP_ID`(Android)와 인증 가능한 `BASE_URL`을 설정한 뒤:

Run: `cd apps/frontend && ./gradlew :app:installDebug`

확인 항목:
- 혜택존 → "TNK 오퍼월" 카드가 활성(밝게)으로 보이고 탭 가능
- 탭 시 토큰 발급 후 TNK 오퍼월 전체화면이 뜸 (logcat `TnkOfferwall` 태그에 에러 없음)
- 뒤로가기로 복귀 시 잔액/출석이 새로고침됨 (logcat에서 `GET /api/points/me` 호출 확인)
- 혜택존을 아래로 당기면 새로고침 인디케이터가 뜨고 잔액/출석 갱신

이 단계는 자동 테스트가 아니므로 사람이 확인한다. 문제 발견 시 해당 Task로 돌아간다.

---

## Phase 3 — iOS

### Task 7: TNK xcframework 통합 + App ID 주입

**Files:**
- Add: `TnkRwdSdk2.xcframework` (Xcode)
- Modify: `CashChatIOS/CashChatIOS/Secrets.swift`, `CashChatIOS/CashChatIOS/Secrets.swift.example`(있으면), `CashChatIOS/CashChatIOS/AppConfig.swift`
- Modify: `CashChatIOS/CashChatIOS/Info.plist`

- [ ] **Step 1: xcframework 다운로드 및 임베드**

`https://github.com/tnkfactory/ios-sdk-rwd2` 의 iOS_Guide.md 지시에 따라 `TnkRwdSdk2.xcframework`를 받아 `CashChatIOS/` 하위(예: `CashChatIOS/Frameworks/`)에 둔다. Xcode에서 타깃 `CashChatIOS` → General → "Frameworks, Libraries, and Embedded Content"에 드래그하고 **Embed & Sign**으로 설정한다. (`.pbxproj`가 수정됨.)

- [ ] **Step 2: Secrets/AppConfig 에 tnkAppId 추가**

`Secrets.swift`의 `admobRewardedAdUnitId` 아래에 추가:

```swift
    static let tnkAppId = "<TNK 콘솔 iOS App ID>"
```

`Secrets.swift.example`이 있으면 동일 키를 placeholder 값(`"YOUR_TNK_APP_ID"`)으로 추가한다.

`AppConfig.swift`의 `admobRewardedAdUnitId` 아래에 추가:

```swift
    static let tnkAppId: String = required(Secrets.tnkAppId, key: "tnkAppId")
```

- [ ] **Step 3: Info.plist 에 tnkad_app_id 추가**

`Info.plist`에 키 추가 (값은 빌드시 주입하거나 직접 App ID):

```xml
    <key>tnkad_app_id</key>
    <string>$(TNK_APP_ID)</string>
```

> `initInstance(appId:)`로 코드에서 직접 주입(Task 8)하므로 Info.plist 항목은 선택이다. iOS_Guide.md가 plist를 요구하면 추가하고, init 파라미터만으로 충분하면 생략한다.

- [ ] **Step 4: shared framework 재생성 후 빌드 확인**

Run:
```bash
cd apps/frontend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && ./gradlew :shared:embedAndSignAppleFrameworkForXcode
```
그 후 Xcode에서 `CashChatIOS` 타깃 빌드(⌘B). Expected: 빌드 성공(TNK 심볼 resolve).

- [ ] **Step 5: 커밋** (Secrets.swift 는 .gitignore 대상이므로 제외)

```bash
git add apps/frontend/CashChatIOS/CashChatIOS/AppConfig.swift apps/frontend/CashChatIOS/CashChatIOS/Info.plist apps/frontend/CashChatIOS/CashChatIOS.xcodeproj/project.pbxproj
# Secrets.swift.example 이 있으면 함께 add
git commit -m "build(offerwall): iOS TNK SDK 임베드 및 tnkAppId 구성"
```

### Task 8: TNK 초기화 + 오퍼월 매니저 (iOS)

**Files:**
- Modify: `CashChatIOS/CashChatIOS/CashChatIOSApp.swift`
- Create: `CashChatIOS/CashChatIOS/Offerwall/TnkOfferwallManager.swift`

- [ ] **Step 1: App init 에서 TNK 초기화**

`CashChatIOSApp.swift` 상단에 `import` 추가 (SDK 모듈명은 가이드 확인 — 예 `import TnkRwdSdk2`), `init()`의 `MobileAds.shared.start(...)` 아래에 추가:

```swift
        TnkSession.initInstance(appId: AppConfig.tnkAppId)
```

- [ ] **Step 2: 오퍼월 매니저 작성**

`TnkOfferwallManager.swift`:

```swift
import UIKit
import CashChatShared
// import TnkRwdSdk2  // SDK 모듈명은 iOS_Guide.md 기준으로 확정

/// TNK 오퍼월 노출 오케스트레이션.
/// 1) BE 토큰 발급 → 2) setUserName → 3) AdOfferwallViewController 전체화면 present.
@MainActor
enum TnkOfferwallManager {
    private static let offerwallApi = KoinHelper().offerwallApi()

    static func present(from presenter: UIViewController) {
        Task { @MainActor in
            do {
                let dto = try await offerwallApi.issueUserToken()
                TnkSession.sharedInstance()?.setUserName(dto.token)
                let vc = AdOfferwallViewController()
                vc.title = "오퍼월"
                let nav = UINavigationController(rootViewController: vc)
                nav.modalPresentationStyle = .fullScreen
                presenter.present(nav, animated: true)
            } catch {
                print("TnkOfferwall: 오퍼월 진입 실패 - \(error)")
            }
        }
    }

    /// 최상위 표시 중인 ViewController 를 찾는다(SwiftUI 에서 present 하기 위함).
    static func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes.first { $0.activationState == .foregroundActive } as? UIWindowScene
        var top = scene?.keyWindow?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}
```

- [ ] **Step 3: Xcode 빌드 확인**

Xcode에서 `CashChatIOS` 빌드(⌘B). Expected: 성공. (`AdOfferwallViewController`, `TnkSession` 심볼 resolve — 실패 시 import 모듈명을 iOS_Guide.md로 확정.)

- [ ] **Step 4: 커밋**

```bash
git add apps/frontend/CashChatIOS/CashChatIOS/CashChatIOSApp.swift apps/frontend/CashChatIOS/CashChatIOS/Offerwall/TnkOfferwallManager.swift apps/frontend/CashChatIOS/CashChatIOS.xcodeproj/project.pbxproj
git commit -m "feat(offerwall): iOS TNK 초기화 및 오퍼월 매니저 추가"
```

### Task 9: 혜택존 카드 활성화 + refresh (iOS)

**Files:**
- Modify: `CashChatIOS/CashChatIOS/BenefitZone/AttendanceViewModel.swift`
- Modify: `CashChatIOS/CashChatIOS/BenefitZoneScreen.swift`

- [ ] **Step 1: AttendanceViewModel 에 refresh() 추가**

`AttendanceViewModel.swift`의 `func load()` 아래에 추가 (잔액+출석 동시 갱신):

```swift
    /// 당겨서 새로고침 / 화면 복귀 시 잔액과 출석을 함께 갱신.
    func refresh() async {
        store.loadMonthly(year: nil, month: nil)
        try? await points.refresh()
    }
```

- [ ] **Step 2: BenefitZoneScreen 에 카드 활성화 + .refreshable + scenePhase**

`BenefitZoneScreen.swift` 수정:

(a) 상단에 `@Environment(\.scenePhase) private var scenePhase` 추가.

(b) `ScrollView { ... }` 에 `.refreshable { await attendanceVM.refresh() }` 추가.

(c) TNK 카드를 활성화하고 탭 동작 추가 — 기존 줄

```swift
                BenefitInfoCardView(icon: "gamecontroller.fill", title: "TNK 오퍼월", badge: .soon,
                    description: "앱 설치·설문 참여로 대량 코인 (최대 +1,500 코인)", dimmed: true)
                    .padding(.horizontal, 16)
```

를 다음으로 교체:

```swift
                BenefitInfoCardView(icon: "gamecontroller.fill", title: "TNK 오퍼월", badge: .next,
                    description: "앱 설치·설문 참여로 대량 코인 (최대 +1,500 코인)", dimmed: false)
                    .padding(.horizontal, 16)
                    .onTapGesture {
                        if let top = TnkOfferwallManager.topViewController() {
                            TnkOfferwallManager.present(from: top)
                        }
                    }
```

(`BenefitInfoCardView`가 자체 탭 핸들러를 받는 구조면 `.onTapGesture` 대신 그 파라미터를 쓴다 — 파일에서 시그니처 확인.)

(d) `.onAppear` 블록 뒤에 scenePhase 변화 시 새로고침 추가:

```swift
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { Task { await attendanceVM.refresh() } }
        }
```

- [ ] **Step 3: Xcode 빌드 확인**

Xcode 빌드(⌘B). Expected: 성공.

- [ ] **Step 4: 커밋**

```bash
git add apps/frontend/CashChatIOS/CashChatIOS/BenefitZone/AttendanceViewModel.swift apps/frontend/CashChatIOS/CashChatIOS/BenefitZoneScreen.swift
git commit -m "feat(offerwall): 혜택존 TNK 카드 활성화·당겨서새로고침·복귀 새로고침 (iOS)"
```

### Task 10: iOS 수동 검증

- [ ] **Step 1: 시뮬레이터/실기기에서 동작 확인**

확인 항목:
- 혜택존 → "TNK 오퍼월" 카드 활성·탭 가능
- 탭 시 토큰 발급 후 오퍼월 전체화면 present
- 복귀 시(scenePhase active) 잔액/출석 새로고침
- 혜택존 당겨서 새로고침 동작
- (적립 end-to-end 검증은 BE iOS app_key 배포 후 — 이 단계에선 UI/토큰/노출까지만 확인)

---

## Phase 4 — CI 시크릿

### Task 11: 워크플로우에 TNK App ID 주입

**Files:**
- Modify: `.github/workflows/release-android-distribute.yml`
- Modify: `.github/workflows/release-ios-distribute.yml`

- [ ] **Step 1: Android 워크플로우**

`release-android-distribute.yml`의 "Write local.properties" 스텝에서 `SENTRY_DSN` 줄 아래에 추가:

```yaml
          echo "TNK_APP_ID=${{ secrets.TNK_APP_ID_ANDROID }}"                                       >> local.properties
```

- [ ] **Step 2: iOS 워크플로우**

`release-ios-distribute.yml`의 Secrets.swift 생성 블록에서 `admobRewardedAdUnitId` 줄 아래에 추가:

```bash
              static let tnkAppId = "TNK_APP_ID_IOS_PLACEHOLDER"
```

그리고 env 에 `TNK_APP_ID_IOS: ${{ secrets.TNK_APP_ID_IOS }}` 추가, sed 치환 목록에 추가:

```bash
            -e "s|TNK_APP_ID_IOS_PLACEHOLDER|${TNK_APP_ID_IOS}|g" \
```

- [ ] **Step 3: YAML 문법 확인**

Run: `python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/release-android-distribute.yml')); yaml.safe_load(open('.github/workflows/release-ios-distribute.yml')); print('OK')"`
Expected: `OK`

- [ ] **Step 4: 커밋**

```bash
git add .github/workflows/release-android-distribute.yml .github/workflows/release-ios-distribute.yml
git commit -m "ci(offerwall): TNK_APP_ID_ANDROID/IOS 시크릿 빌드 주입"
```

> GitHub 저장소 시크릿 `TNK_APP_ID_ANDROID`, `TNK_APP_ID_IOS` 등록은 운영자가 별도로 수행한다(코드 변경 아님).

---

## Phase 5 — 마무리

### Task 12: 전체 회귀 검증

- [ ] **Step 1: shared 테스트 + Android 빌드**

Run: `cd apps/frontend && ./gradlew :shared:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL, 모든 테스트 PASS.

- [ ] **Step 2: 설계 인수 기준 점검**

spec §7 체크리스트를 항목별로 확인하고, 미충족 항목이 있으면 해당 Task로 복귀한다.

- [ ] **Step 3: spec §8 범위 외 항목을 PR 설명/Jira 코멘트에 명시**

BE `app_key` 앱별 분리·iOS BE 미배포, 콜백 URL 콘솔 등록, GitHub 시크릿 등록을 후속 작업으로 남긴다.

---

## Self-Review 결과

- **Spec coverage:** §2 SDK 연동→Task 3/4/7/8, §3 키/시크릿→Task 3/7/11, §4.2 shared API→Task 1/2, §4.3 Android→Task 3~6, §4.4 iOS→Task 7~10, §4.5 새로고침(on-resume+pull-to-refresh)→Task 5/9, §5 CI→Task 11, §6 테스트→Task 1/6/10/12, §7 검증→Task 12. 누락 없음.
- **Placeholder scan:** SDK import 패키지/모듈명은 단정할 수 없어 "가이드로 확정" 지시를 명시(실제 미확정 사실이므로 placeholder 아님). 그 외 코드/명령/경로는 구체값.
- **Type consistency:** `OfferwallApi.issueUserToken(): UserTokenDto(token)` — Android/iOS 매니저, Koin, 브릿지 모두 동일 시그니처 사용. `refreshAll`/`refresh` 잔액+출석 동시 갱신 일관.
