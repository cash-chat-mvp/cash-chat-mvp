# 혜택존 Phase F + Phase 1 (기반 인프라 + 출석체크) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** shared KMM 인증 Ktor 클라이언트와 iOS 메인 탭 셸을 구축하고, 그 위에 출석체크 기능을 Android/iOS 양 플랫폼에서 실제 API로 동작시킨다.

**Architecture:** 인증 네트워킹·DTO·도메인 상태는 `shared/commonMain`(Ktor + Koin + StateFlow)에 두어 양 플랫폼이 공유한다. 화면은 플랫폼 네이티브(Android Compose / iOS SwiftUI)로 구현하고 shared Store를 구독한다. 토큰 저장소는 `TokenProvider` 인터페이스로 추상화해 Android는 DataStore, iOS는 Keychain에 위임한다. 미구현 BE(`GET /api/points/me`)는 `PointsRepository` 인터페이스 뒤로 격리한다.

**Tech Stack:** Kotlin Multiplatform, Ktor 2.3.12 (client-core/auth/content-negotiation/mock), kotlinx.serialization, kotlinx-coroutines, Koin 3.5.6, Jetpack Compose (Material3), SwiftUI, kotlin-test + coroutines-test.

**관련 spec:** `docs/superpowers/specs/2026-06-07-benefit-zone-foundation-attendance-design.md`

**진행 로그 규약:** 각 task 완료 시 서브에이전트는 `docs/superpowers/specs/2026-06-07-benefit-zone-progress.md`에 항목을 **append**(한국어, append-only)한다. 항목: Task 번호/제목, 상태(✅/⚠️/❌), 변경·추가 파일, 검증 결과(빌드/테스트/수동), 다음 task 인계 메모. 오케스트레이터는 각 서브에이전트 디스패치 프롬프트에 이 지시를 포함한다.

**공통 작업 디렉토리:** `apps/frontend/` (Gradle 명령은 모두 이 디렉토리에서 실행).

**커밋 규약:** Conventional Commits, 한국어 메시지. 각 Task 끝에서 커밋.

---

## 파일 구조 (생성/수정 대상)

**Phase F — 인증 네트워킹 (shared):**
- Create `shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/core/network/TokenProvider.kt` — 토큰 공급/저장 추상 인터페이스
- Create `shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/core/network/AuthenticatedApiClient.kt` — Bearer 주입 + 401 refresh Ktor 클라이언트 팩토리
- Create `shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/core/network/ApiConfig.kt` — baseUrl 보관
- Create `shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/core/network/AuthenticatedApiClientTest.kt`
- Modify `app/src/main/java/com/nomadclub/cashchat/core/data/TokenDataStore.kt` — `TokenProvider` 구현 어댑터 추가(Android)
- Create `shared/src/iosMain/.../core/network/` 토큰 브리지는 iOS 셸 Task에서 처리
- Modify `gradle/libs.versions.toml`, `shared/build.gradle.kts` — ktor-auth/mock, test 의존성

**Phase F — iOS 셸:**
- Create `CashChatIOS/CashChatIOS/MainTabView.swift` — 4탭 TabView
- Create `CashChatIOS/CashChatIOS/KeychainTokenProvider.swift` — `TokenProvider` actual 위임
- Modify `CashChatIOS/CashChatIOS/ContentView.swift` — 로그인 후 `MainTabView` 진입

**Phase 1 — 출석 (shared):**
- Create `shared/src/commonMain/.../attendance/model/AttendanceModels.kt` — @Serializable DTO
- Create `shared/src/commonMain/.../attendance/AttendanceApiService.kt`
- Create `shared/src/commonMain/.../points/PointsRepository.kt` — 잔액 격리 인터페이스 + 잠정 어댑터
- Create `shared/src/commonMain/.../attendance/AttendanceStore.kt`
- Create `shared/src/commonTest/.../attendance/AttendanceApiServiceTest.kt`, `AttendanceStoreTest.kt`
- Create `shared/src/commonMain/.../di/SharedModule.kt` — Koin 모듈
- Modify `app/.../di/AppModule.kt` — shared 모듈 포함 + Store 노출

**Phase 1 — 출석 UI:**
- Create `app/.../feature/rewards/BenefitZoneScreen.kt`, `app/.../feature/rewards/AttendanceWidget.kt`
- Modify `app/.../feature/main/MainScreen.kt` — `RewardsScreen` → `BenefitZoneScreen`
- Create `CashChatIOS/CashChatIOS/BenefitZone/BenefitZoneView.swift`, `AttendanceWidgetView.swift`, `AttendanceViewModel.swift`

---

## Milestone 0: 의존성 추가

### Task 0: 테스트/인증 라이브러리 추가

**Files:**
- Modify: `apps/frontend/gradle/libs.versions.toml`
- Modify: `apps/frontend/shared/build.gradle.kts`

- [ ] **Step 1: libs.versions.toml에 라이브러리 추가**

`[libraries]` 섹션에 추가 (ktor 버전 ref는 기존 `ktor = "2.3.12"` 재사용):

```toml
ktor-client-auth = { group = "io.ktor", name = "ktor-client-auth", version.ref = "ktor" }
ktor-client-mock = { group = "io.ktor", name = "ktor-client-mock", version.ref = "ktor" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
```

`[versions]`에 `coroutines` 가 없으면 추가(기존 `kotlinx.coroutines.core` 의 버전과 동일하게). 기존 toml에서 coroutines 버전 키 확인 후 ref 일치시킬 것. 없다면:

```toml
coroutines = "1.8.1"
```

- [ ] **Step 2: shared/build.gradle.kts 의존성 추가**

`commonMain.dependencies` 에 추가:

```kotlin
implementation(libs.ktor.client.auth)
```

`sourceSets { ... }` 안에 commonTest 추가:

```kotlin
commonTest.dependencies {
    implementation(kotlin("test"))
    implementation(libs.ktor.client.mock)
    implementation(libs.kotlinx.coroutines.test)
}
```

- [ ] **Step 3: 동기화 확인**

Run: `./gradlew :shared:dependencies --configuration commonMainImplementation -q | grep -i ktor`
Expected: `ktor-client-auth` 가 목록에 보임.

- [ ] **Step 4: Commit**

```bash
git add apps/frontend/gradle/libs.versions.toml apps/frontend/shared/build.gradle.kts
git commit -m "chore(shared): 혜택존 인증/테스트 의존성 추가 (ktor-auth, ktor-mock, coroutines-test)"
```

---

## Milestone 1: shared 인증 Ktor 클라이언트 (Phase F)

### Task 1: TokenProvider 인터페이스 + ApiConfig

**Files:**
- Create: `shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/core/network/TokenProvider.kt`
- Create: `shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/core/network/ApiConfig.kt`

- [ ] **Step 1: TokenProvider 작성**

```kotlin
package com.nomadclub.cashchat.shared.core.network

/**
 * 플랫폼별 토큰 저장소 추상화.
 * Android → TokenDataStore(DataStore), iOS → Keychain.
 * suspend 미사용(블로킹 접근 허용) — 기존 TokenDataStore 의 *Blocking 패턴과 정렬.
 */
interface TokenProvider {
    fun accessToken(): String?
    fun refreshToken(): String?
    /** "GUEST" | "MEMBER" | "ADMIN" | null */
    fun role(): String?
    fun deviceToken(): String?
    /** refresh 성공 시 새 토큰 일괄 저장 */
    fun updateTokens(accessToken: String, refreshToken: String)
}
```

- [ ] **Step 2: ApiConfig 작성**

```kotlin
package com.nomadclub.cashchat.shared.core.network

/** API 서버 기본 URL 보관. DI 로 주입. */
class ApiConfig(val baseUrl: String)
```

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew :shared:compileKotlinMetadata -q`
Expected: BUILD SUCCESSFUL (신규 인터페이스/클래스 컴파일).

- [ ] **Step 4: Commit**

```bash
git add apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/core/network/
git commit -m "feat(shared): TokenProvider 추상화 및 ApiConfig 추가"
```

### Task 2: AuthenticatedApiClient — 실패 테스트 먼저

**Files:**
- Create: `shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/core/network/AuthenticatedApiClientTest.kt`

테스트는 Ktor `MockEngine`을 주입할 수 있도록 팩토리가 `engine` 파라미터를 받는 설계로 작성한다.

- [ ] **Step 1: 실패 테스트 작성**

```kotlin
package com.nomadclub.cashchat.shared.core.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeTokenProvider(
    private var access: String?,
    private var refresh: String?,
    private var roleValue: String? = "MEMBER",
) : TokenProvider {
    var updatedAccess: String? = null
    override fun accessToken() = access
    override fun refreshToken() = refresh
    override fun role() = roleValue
    override fun deviceToken() = "device-1"
    override fun updateTokens(accessToken: String, refreshToken: String) {
        access = accessToken; refresh = refreshToken; updatedAccess = accessToken
    }
}

class AuthenticatedApiClientTest {

    @Test
    fun `요청에 Bearer 액세스 토큰을 주입한다`() = runTest {
        var seenAuth: String? = null
        val engine = MockEngine { request ->
            seenAuth = request.headers[HttpHeaders.Authorization]
            respond("ok", HttpStatusCode.OK)
        }
        val provider = FakeTokenProvider(access = "acc-1", refresh = "ref-1")
        val client = AuthenticatedApiClient(ApiConfig("http://test"), provider, engine).httpClient

        val res = client.get("http://test/api/users/me").bodyAsText()

        assertEquals("ok", res)
        assertEquals("Bearer acc-1", seenAuth)
    }

    @Test
    fun `401 응답 시 refresh 후 새 토큰으로 재요청한다`() = runTest {
        val provider = FakeTokenProvider(access = "old", refresh = "ref-1")
        var calls = 0
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/api/auth/refresh") ->
                    respond(
                        """{"accessToken":"new","refreshToken":"ref-2","role":"MEMBER","userId":1}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                else -> {
                    calls++
                    if (calls == 1) respond("unauthorized", HttpStatusCode.Unauthorized)
                    else respond("protected", HttpStatusCode.OK)
                }
            }
        }
        val client = AuthenticatedApiClient(ApiConfig("http://test"), provider, engine).httpClient

        val res = client.get("http://test/api/attendance/me").bodyAsText()

        assertEquals("protected", res)
        assertEquals("new", provider.updatedAccess)
        assertTrue(calls == 2)
    }
}
```

- [ ] **Step 2: 테스트 실행 → 컴파일 실패 확인**

Run: `./gradlew :shared:testDebugUnitTest --tests "*AuthenticatedApiClientTest*"`
Expected: FAIL — `AuthenticatedApiClient` 미존재로 컴파일 에러.

### Task 3: AuthenticatedApiClient 구현

**Files:**
- Create: `shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/core/network/AuthenticatedApiClient.kt`

기존 `TokenAuthenticator` 정책 이식: refresh 엔드포인트는 재인증 제외, 게스트/멤버 분기. Ktor `Auth.bearer` 의 `refreshTokens` 사용. 게스트는 refreshToken 대신 deviceToken 으로 `/api/auth/guest` 재발급.

- [ ] **Step 1: 구현 작성**

```kotlin
package com.nomadclub.cashchat.shared.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class RefreshBody(val refreshToken: String)

@Serializable
private data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
)

/**
 * 인증된 KMM Ktor 클라이언트.
 * - 모든 요청에 Bearer accessToken 자동 주입.
 * - 401 시 refreshTokens 콜백이 토큰 갱신 후 재요청.
 *   MEMBER/ADMIN → POST /api/auth/refresh, GUEST → POST /api/auth/guest?deviceToken=...
 * - 갱신 엔드포인트는 sendWithoutRequest 로 토큰 미부착(무한 루프 방지).
 */
class AuthenticatedApiClient(
    private val config: ApiConfig,
    private val tokenProvider: TokenProvider,
    engine: HttpClientEngine,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // refresh 호출 전용(Auth 플러그인 미설치) — 갱신 중 재귀 방지
    private val refreshClient = HttpClient(engine) {
        install(ContentNegotiation) { json(json) }
    }

    val httpClient = HttpClient(engine) {
        install(ContentNegotiation) { json(json) }
        install(Auth) {
            bearer {
                loadTokens {
                    val acc = tokenProvider.accessToken()
                    val ref = tokenProvider.refreshToken()
                    if (acc != null) BearerTokens(acc, ref ?: "") else null
                }
                refreshTokens {
                    val pair = refreshAccessToken()
                    if (pair != null) {
                        tokenProvider.updateTokens(pair.accessToken, pair.refreshToken)
                        BearerTokens(pair.accessToken, pair.refreshToken)
                    } else null
                }
                sendWithoutRequest { request ->
                    val path = request.url.encodedPath
                    !(path.contains("auth/refresh") || path.contains("auth/guest"))
                }
            }
        }
    }

    private suspend fun refreshAccessToken(): TokenPair? {
        return when (tokenProvider.role()) {
            "MEMBER", "ADMIN" -> {
                val ref = tokenProvider.refreshToken() ?: return null
                runCatching {
                    refreshClient.post("${config.baseUrl}/api/auth/refresh") {
                        contentType(ContentType.Application.Json)
                        setBody(RefreshBody(ref))
                    }.body<TokenPair>()
                }.getOrNull()
            }
            "GUEST" -> {
                val device = tokenProvider.deviceToken() ?: return null
                runCatching {
                    refreshClient.post("${config.baseUrl}/api/auth/guest") {
                        parameter("deviceToken", device)
                    }.body<TokenPair>()
                }.getOrNull()
            }
            else -> null
        }
    }
}
```

> 참고: 서버 `AuthResponse` 에 `accessToken`/`refreshToken` 외 필드가 있어도 `ignoreUnknownKeys=true` 로 무시되어 `TokenPair` 파싱 가능.

- [ ] **Step 2: 테스트 통과 확인**

Run: `./gradlew :shared:testDebugUnitTest --tests "*AuthenticatedApiClientTest*"`
Expected: PASS (2 tests).

- [ ] **Step 3: Commit**

```bash
git add apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/core/network/AuthenticatedApiClient.kt apps/frontend/shared/src/commonTest/
git commit -m "feat(shared): Bearer 주입+401 refresh 인증 Ktor 클라이언트 추가"
```

### Task 4: Android TokenProvider 어댑터

**Files:**
- Modify: `app/src/main/java/com/nomadclub/cashchat/core/data/TokenDataStore.kt`
- Create: `app/src/main/java/com/nomadclub/cashchat/core/data/DataStoreTokenProvider.kt`

- [ ] **Step 1: 어댑터 작성**

```kotlin
package com.nomadclub.cashchat.core.data

import com.nomadclub.cashchat.shared.core.network.TokenProvider

/** TokenDataStore(DataStore) 를 shared TokenProvider 로 위임. */
class DataStoreTokenProvider(
    private val store: TokenDataStore,
) : TokenProvider {
    override fun accessToken(): String? = store.getAccessTokenBlocking()
    override fun refreshToken(): String? = store.getRefreshTokenBlocking()
    override fun role(): String? = store.getRoleBlocking()
    override fun deviceToken(): String? = store.getOrCreateDeviceTokenBlocking()
    override fun updateTokens(accessToken: String, refreshToken: String) {
        store.updateTokensBlocking(accessToken, refreshToken)
    }
}
```

- [ ] **Step 2: TokenDataStore에 updateTokensBlocking 추가**

`TokenDataStore.kt` 의 `*Blocking` 함수들 옆에 추가(기존 키 상수 `KEY_ACCESS_TOKEN`, `KEY_REFRESH_TOKEN` 재사용):

```kotlin
fun updateTokensBlocking(accessToken: String, refreshToken: String) = runBlocking {
    store.edit { prefs ->
        prefs[KEY_ACCESS_TOKEN] = accessToken
        prefs[KEY_REFRESH_TOKEN] = refreshToken
    }
}
```

> `store.edit` import 가 없으면 `androidx.datastore.preferences.core.edit` 추가. `runBlocking` 은 기존 파일에 이미 import 됨.

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew :app:compileDevDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add apps/frontend/app/src/main/java/com/nomadclub/cashchat/core/data/
git commit -m "feat(app): DataStore 기반 TokenProvider 어댑터 추가"
```

---

## Milestone 2: 출석 도메인 shared 로직 (Phase 1)

### Task 5: 출석 DTO 모델

**Files:**
- Create: `shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/attendance/model/AttendanceModels.kt`

BE 응답(`CheckInResponse`, `MonthlyAttendanceResponse`)과 1:1 매칭.

- [ ] **Step 1: 모델 작성**

```kotlin
package com.nomadclub.cashchat.shared.attendance.model

import kotlinx.serialization.Serializable

@Serializable
data class BonusItem(val itemCode: String, val quantity: Int)

@Serializable
data class RewardPreview(
    val dayCount: Int,
    val coin: Long,
    val bonusItems: List<BonusItem>,
)

@Serializable
data class MonthlyAttendance(
    val year: Int,
    val month: Int,
    val checkedDays: List<Int>,
    val currentStreak: Int,
    val todayChecked: Boolean,
    val nextRewardPreview: RewardPreview,
)

@Serializable
data class CheckInResult(
    val awardedCoin: Long,
    val streakDayCount: Int,
    val bonusItems: List<BonusItem>,
    val nextRewardPreview: RewardPreview,
)
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew :shared:compileKotlinMetadata -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/attendance/model/
git commit -m "feat(shared): 출석 API 응답 DTO 추가"
```

### Task 6: AttendanceApiService — 실패 테스트 먼저

**Files:**
- Create: `shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/attendance/AttendanceApiServiceTest.kt`

- [ ] **Step 1: 실패 테스트 작성**

```kotlin
package com.nomadclub.cashchat.shared.attendance

import com.nomadclub.cashchat.shared.core.network.ApiConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class AttendanceApiServiceTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun client(engine: MockEngine) = HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    @Test
    fun `getMonthly 는 attendance me 를 호출하고 파싱한다`() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/api/attendance/me", request.url.encodedPath)
            respond(
                """{"year":2026,"month":5,"checkedDays":[1,2,3],"currentStreak":3,"todayChecked":false,
                   "nextRewardPreview":{"dayCount":4,"coin":20,"bonusItems":[]}}""",
                HttpStatusCode.OK, jsonHeaders,
            )
        }
        val service = AttendanceApiService(ApiConfig("http://test"), client(engine))

        val result = service.getMonthly(2026, 5)

        assertEquals(3, result.currentStreak)
        assertEquals(listOf(1, 2, 3), result.checkedDays)
        assertEquals(false, result.todayChecked)
    }

    @Test
    fun `checkIn 은 POST check-in 을 호출하고 보상을 파싱한다`() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/attendance/check-in", request.url.encodedPath)
            respond(
                """{"awardedCoin":30,"streakDayCount":7,
                   "bonusItems":[{"itemCode":"EVOLVE_STONE","quantity":1}],
                   "nextRewardPreview":{"dayCount":8,"coin":20,"bonusItems":[]}}""",
                HttpStatusCode.OK, jsonHeaders,
            )
        }
        val service = AttendanceApiService(ApiConfig("http://test"), client(engine))

        val result = service.checkIn()

        assertEquals(30, result.awardedCoin)
        assertEquals(7, result.streakDayCount)
        assertEquals("EVOLVE_STONE", result.bonusItems.first().itemCode)
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :shared:testDebugUnitTest --tests "*AttendanceApiServiceTest*"`
Expected: FAIL — `AttendanceApiService` 미존재.

### Task 7: AttendanceApiService 구현

**Files:**
- Create: `shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/attendance/AttendanceApiService.kt`

- [ ] **Step 1: 구현 작성**

```kotlin
package com.nomadclub.cashchat.shared.attendance

import com.nomadclub.cashchat.shared.attendance.model.CheckInResult
import com.nomadclub.cashchat.shared.attendance.model.MonthlyAttendance
import com.nomadclub.cashchat.shared.core.network.ApiConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.parameter
import kotlinx.coroutines.CancellationException

/**
 * 출석 REST 클라이언트. 인증 클라이언트(AuthenticatedApiClient.httpClient)를 주입받는다.
 * iOS 에서 호출하는 suspend 함수는 @Throws 필수 — 미준수 시 예외 발생하면 앱 크래시.
 */
class AttendanceApiService(
    private val config: ApiConfig,
    private val httpClient: HttpClient,
) {
    @Throws(CancellationException::class, Exception::class)
    suspend fun getMonthly(year: Int? = null, month: Int? = null): MonthlyAttendance =
        httpClient.get("${config.baseUrl}/api/attendance/me") {
            if (year != null) parameter("year", year)
            if (month != null) parameter("month", month)
        }.body()

    @Throws(CancellationException::class, Exception::class)
    suspend fun checkIn(): CheckInResult =
        httpClient.post("${config.baseUrl}/api/attendance/check-in").body()
}
```

- [ ] **Step 2: 통과 확인**

Run: `./gradlew :shared:testDebugUnitTest --tests "*AttendanceApiServiceTest*"`
Expected: PASS (2 tests).

- [ ] **Step 3: Commit**

```bash
git add apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/attendance/AttendanceApiService.kt apps/frontend/shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/attendance/
git commit -m "feat(shared): 출석 API 서비스 추가 (getMonthly/checkIn)"
```

### Task 8: PointsRepository — 잔액 격리 인터페이스

**Files:**
- Create: `shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/points/PointsRepository.kt`

`GET /api/points/me` 부재를 격리. 잠정 구현은 로컬 누적(초기값 + 적립분). BE 준비 시 실제 호출 구현으로 교체.

- [ ] **Step 1: 인터페이스 + 잠정 구현 작성**

```kotlin
package com.nomadclub.cashchat.shared.points

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 코인 잔액 소스. BE 의 GET /api/points/me 미구현 상태를 이 인터페이스 뒤로 격리한다.
 * 준비되면 RemotePointsRepository(httpClient) 로 교체(인터페이스 불변).
 */
interface PointsRepository {
    val balance: StateFlow<Long>
    /** 서버에서 최신 잔액 동기화(준비 전에는 no-op). */
    suspend fun refresh()
    /** 적립 발생 시 로컬 잔액 반영(서버 동기화 전 즉시 UI 갱신용). */
    fun applyDelta(delta: Long)
}

/** BE 부재 동안 사용하는 잠정 구현 — 초기값 + 적립 누적. */
class LocalPointsRepository(initial: Long = 1250) : PointsRepository {
    private val _balance = MutableStateFlow(initial)
    override val balance: StateFlow<Long> = _balance.asStateFlow()
    override suspend fun refresh() { /* no-op until GET /api/points/me 구현 */ }
    override fun applyDelta(delta: Long) = _balance.update { (it + delta).coerceAtLeast(0) }
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew :shared:compileKotlinMetadata -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/points/PointsRepository.kt
git commit -m "feat(shared): 코인 잔액 격리용 PointsRepository 추가 (BE 부재 잠정 구현)"
```

### Task 9: AttendanceStore — 실패 테스트 먼저

**Files:**
- Create: `shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/attendance/AttendanceStoreTest.kt`

Store는 API 서비스에 의존하므로, 테스트를 위해 `AttendanceApiService` 를 추상화하거나 MockEngine 으로 실제 서비스를 구성한다. 여기서는 MockEngine 기반 실제 서비스 사용(추가 인터페이스 없이).

- [ ] **Step 1: 실패 테스트 작성**

```kotlin
package com.nomadclub.cashchat.shared.attendance

import com.nomadclub.cashchat.shared.core.network.ApiConfig
import com.nomadclub.cashchat.shared.points.LocalPointsRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AttendanceStoreTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private fun http(engine: MockEngine) = HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    @Test
    fun `loadMonthly 후 상태에 출석 일자가 반영된다`() = runTest {
        val engine = MockEngine {
            respond(
                """{"year":2026,"month":6,"checkedDays":[1,2],"currentStreak":2,"todayChecked":false,
                   "nextRewardPreview":{"dayCount":3,"coin":20,"bonusItems":[]}}""",
                HttpStatusCode.OK, jsonHeaders,
            )
        }
        val service = AttendanceApiService(ApiConfig("http://test"), http(engine))
        val store = AttendanceStore(service, LocalPointsRepository(initial = 1000), scope = this)

        store.loadMonthly()

        val state = store.state.first { !it.isLoading }
        assertEquals(listOf(1, 2), state.checkedDays)
        assertEquals(2, state.currentStreak)
        assertEquals(false, state.todayChecked)
    }

    @Test
    fun `checkIn 성공 시 todayChecked true 와 코인 적립이 반영된다`() = runTest {
        var checkedIn = false
        val engine = MockEngine { request ->
            if (request.url.encodedPath.endsWith("/check-in")) {
                checkedIn = true
                respond(
                    """{"awardedCoin":30,"streakDayCount":3,"bonusItems":[],
                       "nextRewardPreview":{"dayCount":4,"coin":20,"bonusItems":[]}}""",
                    HttpStatusCode.OK, jsonHeaders,
                )
            } else {
                respond(
                    """{"year":2026,"month":6,"checkedDays":[1,2],"currentStreak":2,"todayChecked":false,
                       "nextRewardPreview":{"dayCount":3,"coin":20,"bonusItems":[]}}""",
                    HttpStatusCode.OK, jsonHeaders,
                )
            }
        }
        val service = AttendanceApiService(ApiConfig("http://test"), http(engine))
        val points = LocalPointsRepository(initial = 1000)
        val store = AttendanceStore(service, points, scope = this)
        store.loadMonthly()
        store.state.first { !it.isLoading }

        store.checkIn()

        val state = store.state.first { it.todayChecked }
        assertTrue(checkedIn)
        assertEquals(3, state.currentStreak)
        assertEquals(1030, points.balance.first())
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :shared:testDebugUnitTest --tests "*AttendanceStoreTest*"`
Expected: FAIL — `AttendanceStore` 미존재.

### Task 10: AttendanceStore 구현

**Files:**
- Create: `shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/attendance/AttendanceStore.kt`

- [ ] **Step 1: 구현 작성**

```kotlin
package com.nomadclub.cashchat.shared.attendance

import com.nomadclub.cashchat.shared.attendance.model.BonusItem
import com.nomadclub.cashchat.shared.attendance.model.RewardPreview
import com.nomadclub.cashchat.shared.points.PointsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AttendanceUiState(
    val year: Int = 0,
    val month: Int = 0,
    val checkedDays: List<Int> = emptyList(),
    val currentStreak: Int = 0,
    val todayChecked: Boolean = false,
    val nextReward: RewardPreview? = null,
    val isLoading: Boolean = false,
    val isCheckingIn: Boolean = false,
    val errorMessage: String? = null,
)

/** 출석 성공 토스트/애니메이션용 일회성 이벤트. */
data class CheckInRewardEvent(val awardedCoin: Long, val bonusItems: List<BonusItem>)

class AttendanceStore(
    private val service: AttendanceApiService,
    private val pointsRepository: PointsRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(AttendanceUiState())
    val state: StateFlow<AttendanceUiState> = _state.asStateFlow()

    private val _rewardEvents = MutableSharedFlow<CheckInRewardEvent>(extraBufferCapacity = 4)
    val rewardEvents: SharedFlow<CheckInRewardEvent> = _rewardEvents.asSharedFlow()

    fun loadMonthly(year: Int? = null, month: Int? = null) {
        scope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val m = service.getMonthly(year, month)
                _state.update {
                    it.copy(
                        year = m.year, month = m.month, checkedDays = m.checkedDays,
                        currentStreak = m.currentStreak, todayChecked = m.todayChecked,
                        nextReward = m.nextRewardPreview, isLoading = false,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = e.message ?: "출석 정보를 불러오지 못했어요") }
            }
        }
    }

    fun checkIn() {
        if (_state.value.todayChecked || _state.value.isCheckingIn) return
        scope.launch {
            _state.update { it.copy(isCheckingIn = true, errorMessage = null) }
            try {
                val result = service.checkIn()
                pointsRepository.applyDelta(result.awardedCoin)
                _state.update { prev ->
                    val today = prev.checkedDays.maxOrNull()?.plus(1) ?: 1
                    prev.copy(
                        isCheckingIn = false,
                        todayChecked = true,
                        currentStreak = result.streakDayCount,
                        checkedDays = (prev.checkedDays + today).distinct().sorted(),
                        nextReward = result.nextRewardPreview,
                    )
                }
                _rewardEvents.emit(CheckInRewardEvent(result.awardedCoin, result.bonusItems))
            } catch (e: Exception) {
                _state.update { it.copy(isCheckingIn = false, errorMessage = e.message ?: "이미 출석했거나 오류가 발생했어요") }
            }
        }
    }
}
```

> 참고: 테스트는 `today = checkedDays.max()+1` 추정으로 충분하다. 실제 화면은 `loadMonthly` 재호출로 서버 기준 `checkedDays` 를 다시 받아 정확화할 수 있으나, 즉시 UI 반영을 위해 낙관적 갱신을 둔다.

- [ ] **Step 2: 통과 확인**

Run: `./gradlew :shared:testDebugUnitTest --tests "*AttendanceStoreTest*"`
Expected: PASS (2 tests).

- [ ] **Step 3: 전체 shared 테스트 확인**

Run: `./gradlew :shared:testDebugUnitTest`
Expected: PASS (Task 2/6/9 모든 테스트).

- [ ] **Step 4: Commit**

```bash
git add apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/attendance/AttendanceStore.kt apps/frontend/shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/attendance/AttendanceStoreTest.kt
git commit -m "feat(shared): AttendanceStore 추가 (월별 조회/출석/보상 이벤트)"
```

### Task 11: Koin shared 모듈

**Files:**
- Create: `shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/di/SharedModule.kt`

- [ ] **Step 1: 모듈 작성**

```kotlin
package com.nomadclub.cashchat.shared.di

import com.nomadclub.cashchat.shared.attendance.AttendanceApiService
import com.nomadclub.cashchat.shared.attendance.AttendanceStore
import com.nomadclub.cashchat.shared.core.network.ApiConfig
import com.nomadclub.cashchat.shared.core.network.AuthenticatedApiClient
import com.nomadclub.cashchat.shared.core.network.TokenProvider
import com.nomadclub.cashchat.shared.points.LocalPointsRepository
import com.nomadclub.cashchat.shared.points.PointsRepository
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

/**
 * shared 도메인 DI. 플랫폼은 ApiConfig, TokenProvider, HttpClientEngine 을 제공해야 한다.
 *  - Android: AppModule 에서 baseUrl(BuildConfig.BASE_URL), DataStoreTokenProvider, OkHttp 엔진 제공
 *  - iOS: Koin start 시 Darwin 엔진 + KeychainTokenProvider 제공
 */
fun sharedModule(
    baseUrl: String,
    tokenProvider: TokenProvider,
    engineProvider: () -> HttpClientEngine,
) = module {
    single { ApiConfig(baseUrl) }
    single { tokenProvider }
    single { AuthenticatedApiClient(get(), get(), engineProvider()) }
    single<PointsRepository> { LocalPointsRepository() }
    single { AttendanceApiService(get(), get<AuthenticatedApiClient>().httpClient) }
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { AttendanceStore(get(), get(), get()) }
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew :shared:compileKotlinMetadata -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/di/SharedModule.kt
git commit -m "feat(shared): 출석/포인트/인증 클라이언트 Koin 모듈 추가"
```

---

## Milestone 3: Android 혜택존 UI (Phase 1)

> UI 단위는 로직 테스트 대신 빌드 통과 + preview/수동 확인으로 검증한다(코드베이스의 기존 화면도 단위 테스트 없음).

### Task 12: Android DI 배선

**Files:**
- Modify: `app/src/main/java/com/nomadclub/cashchat/di/AppModule.kt`
- Modify: `app/src/main/java/com/nomadclub/cashchat/CashChatApplication.kt` (Koin 시작 지점 — 실제 파일명 확인 후 수정)

- [ ] **Step 1: AppModule에 shared 모듈 결합**

`AppModule.kt` 의 `appModule` 정의 뒤에 추가:

```kotlin
import com.nomadclub.cashchat.BuildConfig
import com.nomadclub.cashchat.core.data.DataStoreTokenProvider
import com.nomadclub.cashchat.core.data.TokenDataStore
import com.nomadclub.cashchat.shared.di.sharedModule
import io.ktor.client.engine.okhttp.OkHttp

// appModule 내부에 single 추가 (DataStoreTokenProvider 제공)
single { DataStoreTokenProvider(get<TokenDataStore>()) }
```

그리고 Koin 시작부(Application 의 `startKoin { modules(...) }`)에 shared 모듈을 추가:

```kotlin
startKoin {
    androidContext(this@CashChatApplication)
    modules(
        appModule,
        sharedModule(
            baseUrl = BuildConfig.BASE_URL,
            tokenProvider = DataStoreTokenProvider(TokenDataStore(this@CashChatApplication)),
            engineProvider = { OkHttp.create() },
        ),
    )
}
```

> Application 클래스의 정확한 위치/이름은 `grep -rn "startKoin" app/src/main` 으로 확인 후 수정. `ktor-client-okhttp` 는 이미 androidMain 의존성이지만, app 모듈에서 직접 `OkHttp.create()` 를 쓰려면 `app/build.gradle.kts` 에 `implementation(libs.ktor.client.okhttp)` 추가 필요할 수 있음 — 컴파일 에러 시 추가.

- [ ] **Step 2: 빌드 확인**

Run: `./gradlew :app:assembleDevDebug -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add apps/frontend/app/src/main/java/com/nomadclub/cashchat/
git commit -m "feat(app): shared 모듈 Koin 배선 (인증 클라이언트/출석 Store)"
```

### Task 13: AttendanceWidget Composable

**Files:**
- Create: `app/src/main/java/com/nomadclub/cashchat/feature/rewards/AttendanceWidget.kt`

디자인 기준: `docs/design-preview/index.html` line 933~955 (월 라벨, 도트 그리드, 보상 프리뷰, 출석 버튼). 컬러 완료 `#5C6BFA` / 오늘 `#FFB800` / 미출석 `#E0DCEF`, 버튼 `#5C6BFA` 48dp.

- [ ] **Step 1: 위젯 작성**

```kotlin
package com.nomadclub.cashchat.feature.rewards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nomadclub.cashchat.shared.attendance.AttendanceUiState

private val Primary = Color(0xFF5C6BFA)
private val Accent = Color(0xFFFFB800)
private val Unchecked = Color(0xFFE0DCEF)

@Composable
fun AttendanceWidget(
    state: AttendanceUiState,
    onCheckIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFE8E1FF), Color(0xFFFAFBFF))))
            .padding(20.dp)
    ) {
        Text("${state.month}월 출석체크", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color(0xFF1B1B2A))
        Spacer(Modifier.height(14.dp))

        val daysInMonth = 31
        val checked = state.checkedDays.toSet()
        val todayNum = state.checkedDays.maxOrNull()?.let { if (state.todayChecked) it else it + 1 } ?: 1
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxWidth().height(180.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items((1..daysInMonth).toList()) { day ->
                val color = when {
                    day in checked -> Primary
                    day == todayNum && !state.todayChecked -> Accent
                    else -> Unchecked
                }
                Box(
                    modifier = Modifier.size(28.dp).clip(CircleShape).background(color),
                    contentAlignment = Alignment.Center,
                ) { Text("$day", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
        }

        Spacer(Modifier.height(12.dp))
        state.nextReward?.let { r ->
            val bonus = r.bonusItems.joinToString(" ") { "📦 ${it.itemCode} ${it.quantity}개" }
            Text("오늘 보상: 🪙+${r.coin}  $bonus", fontSize = 13.sp, color = Color(0xFF1B1B2A))
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onCheckIn,
            enabled = !state.todayChecked && !state.isCheckingIn,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary, disabledContainerColor = Unchecked),
        ) {
            Text(if (state.todayChecked) "오늘 출석 완료" else "출석 도장 찍기", fontWeight = FontWeight.ExtraBold, color = Color.White)
        }
    }
}
```

> import 정리: `Box`, `Spacer`, `size`, `height` 등은 `androidx.compose.foundation.layout.*` 에 포함. 컴파일 에러 시 누락 import 추가.

- [ ] **Step 2: 빌드 확인**

Run: `./gradlew :app:compileDevDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/rewards/AttendanceWidget.kt
git commit -m "feat(app): 출석체크 위젯 Composable 추가"
```

### Task 14: BenefitZoneScreen + 토스트 + 나머지 영역 placeholder

**Files:**
- Create: `app/src/main/java/com/nomadclub/cashchat/feature/rewards/BenefitZoneScreen.kt`

- [ ] **Step 1: 화면 작성**

```kotlin
package com.nomadclub.cashchat.feature.rewards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.nomadclub.cashchat.shared.attendance.AttendanceStore
import com.nomadclub.cashchat.shared.points.PointsRepository
import org.koin.compose.koinInject

@Composable
fun BenefitZoneScreen(
    store: AttendanceStore = koinInject(),
    pointsRepository: PointsRepository = koinInject(),
) {
    val state by store.state.collectAsState()
    val balance by pointsRepository.balance.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { store.loadMonthly() }
    LaunchedEffect(Unit) {
        store.rewardEvents.collect { ev ->
            Toast.makeText(context, "출석 완료! 🪙+${ev.awardedCoin}", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("혜택존", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = MaterialTheme.colorScheme.onBackground)
                Text("🪙 ${balance}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFB07C00))
            }
        }
        item { AttendanceWidget(state = state, onCheckIn = store::checkIn) }
        // 후속 Phase 자리 (데일리 미션 / 리워드 광고 / TNK 오퍼월)
        item { PhasePlaceholder("데일리 미션 (Phase 3)") }
        item { PhasePlaceholder("리워드 광고 (Phase 2)") }
        item { PhasePlaceholder("TNK Factory 오퍼월 (Phase 4)") }
    }
}

@Composable
private fun PhasePlaceholder(label: String) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFF2F1F7)).padding(20.dp)
    ) { Text(label, color = Color(0xFFB0ADBE), fontWeight = FontWeight.Bold) }
}
```

> `org.koin.compose.koinInject` 사용을 위해 `app/build.gradle.kts` 에 `implementation(libs.koin.androidx.compose)` 가 이미 있는지 확인(있음). 없으면 추가.

- [ ] **Step 2: 빌드 확인**

Run: `./gradlew :app:compileDevDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/rewards/BenefitZoneScreen.kt
git commit -m "feat(app): 혜택존 화면 추가 (출석 위젯 + 후속 Phase placeholder)"
```

### Task 15: MainScreen 라우팅 교체

**Files:**
- Modify: `app/src/main/java/com/nomadclub/cashchat/feature/main/MainScreen.kt:94-96`

- [ ] **Step 1: REWARDS 컴포저블 교체**

기존:
```kotlin
composable(MainTab.REWARDS.route) {
    RewardsScreen(points = points, messageCount = messageCount, addPoints = addPoints)
}
```
변경:
```kotlin
composable(MainTab.REWARDS.route) {
    BenefitZoneScreen()
}
```
그리고 import 를 `com.nomadclub.cashchat.feature.rewards.RewardsScreen` → `com.nomadclub.cashchat.feature.rewards.BenefitZoneScreen` 으로 교체. (`RewardsScreen.kt` 는 후속 참조 위해 일단 보존; 미사용 경고는 무시)

- [ ] **Step 2: 빌드 확인**

Run: `./gradlew :app:assembleDevDebug -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 수동 검증 (에뮬레이터/기기)**

빌드한 APK 실행 → 로그인 → 혜택존 탭 진입. 확인:
- 출석 도트 그리드가 서버 `checkedDays` 기준 렌더(네트워크 연결 시).
- "출석 도장 찍기" 탭 → 토스트 + 버튼 비활성 + 상단 코인 증가.
- 이미 출석한 날 재진입 시 버튼 비활성.
서버 미가용 시: `state.errorMessage` 로 graceful (크래시 없음) 확인.

- [ ] **Step 4: Commit**

```bash
git add apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/main/MainScreen.kt
git commit -m "feat(app): 혜택존 탭을 BenefitZoneScreen 으로 교체"
```

---

## Milestone 4: iOS 셸 + 혜택존 (Phase F iOS + Phase 1 iOS)

> iOS 빌드 전 반드시: `export JAVA_HOME="$(/usr/libexec/java_home -v 21)"`. shared 프레임워크는 `:shared:embedAndSignAppleFrameworkForXcode` 로 임베드됨.

### Task 16: KeychainTokenProvider (iOS TokenProvider actual)

**Files:**
- Create: `CashChatIOS/CashChatIOS/KeychainTokenProvider.swift`

기존 `KeychainHelper.swift` 활용. shared `TokenProvider` 프로토콜은 KMM 프레임워크에서 `TokenProvider` 로 노출됨.

- [ ] **Step 1: 작성**

```swift
import Foundation
import CashChatShared

/// shared TokenProvider 를 iOS Keychain 으로 위임.
final class KeychainTokenProvider: TokenProvider {
    private let keychain = KeychainHelper.standard
    private let service = "com.nomadclub.cashchat.auth"

    func accessToken() -> String? { keychain.read(service: service, account: "accessToken") }
    func refreshToken() -> String? { keychain.read(service: service, account: "refreshToken") }
    func role() -> String? { keychain.read(service: service, account: "role") }
    func deviceToken() -> String? { keychain.read(service: service, account: "deviceToken") }
    func updateTokens(accessToken: String, refreshToken: String) {
        keychain.save(accessToken, service: service, account: "accessToken")
        keychain.save(refreshToken, service: service, account: "refreshToken")
    }
}
```

> `KeychainHelper` 의 실제 `read`/`save` 시그니처를 `KeychainHelper.swift` 에서 확인하여 맞출 것. 키 account 명은 기존 auth 저장 로직과 일치시켜야 함(불일치 시 토큰을 못 읽음).

- [ ] **Step 2: 검증** — Task 18 빌드 시 일괄 확인. 단독 커밋:

```bash
git add CashChatIOS/CashChatIOS/KeychainTokenProvider.swift
git commit -m "feat(ios): Keychain 기반 TokenProvider 추가"
```

### Task 17: iOS Koin 시작 + Darwin 엔진 주입

**Files:**
- Modify: `CashChatIOS/CashChatIOS/CashChatIOSApp.swift` (앱 진입점)
- shared: 필요 시 `shared/src/iosMain/.../di/KoinIos.kt` helper

- [ ] **Step 1: iOS Koin 헬퍼 (shared iosMain)**

Create `shared/src/iosMain/kotlin/com/nomadclub/cashchat/shared/di/KoinIos.kt`:

```kotlin
package com.nomadclub.cashchat.shared.di

import com.nomadclub.cashchat.shared.core.network.TokenProvider
import io.ktor.client.engine.darwin.Darwin
import org.koin.core.context.startKoin

fun doInitKoin(baseUrl: String, tokenProvider: TokenProvider) {
    startKoin {
        modules(
            sharedModule(
                baseUrl = baseUrl,
                tokenProvider = tokenProvider,
                engineProvider = { Darwin.create() },
            )
        )
    }
}
```

- [ ] **Step 2: 앱 진입점에서 호출**

`CashChatIOSApp.swift` 의 `init()` 에서(로그인 이전이라도 Koin 자체는 시작 가능):

```swift
import CashChatShared

init() {
    KoinIosKt.doInitKoin(
        baseUrl: AppConfig.baseURL,
        tokenProvider: KeychainTokenProvider()
    )
}
```

> `AppConfig.baseURL` 의 실제 프로퍼티명을 `AppConfig.swift` 에서 확인. Koin 중복 시작 방지를 위해 이미 시작됐는지 가드가 필요하면 추가.

- [ ] **Step 3: shared 프레임워크 재빌드 + iOS 빌드**

Run:
```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
./gradlew :shared:compileKotlinIosSimulatorArm64 -q
```
Expected: BUILD SUCCESSFUL. (Xcode 빌드는 Task 18에서.)

- [ ] **Step 4: Commit**

```bash
git add apps/frontend/shared/src/iosMain/ CashChatIOS/CashChatIOS/CashChatIOSApp.swift
git commit -m "feat(ios): Koin 시작 및 Darwin 엔진 주입"
```

### Task 18: MainTabView + BenefitZoneView (iOS)

**Files:**
- Create: `CashChatIOS/CashChatIOS/MainTabView.swift`
- Create: `CashChatIOS/CashChatIOS/BenefitZone/AttendanceViewModel.swift`
- Create: `CashChatIOS/CashChatIOS/BenefitZone/BenefitZoneView.swift`
- Modify: `CashChatIOS/CashChatIOS/ContentView.swift` (로그인 후 MainTabView 표시)

- [ ] **Step 1: AttendanceViewModel (shared Store 브리지)**

```swift
import Foundation
import CashChatShared

@MainActor
final class AttendanceViewModel: ObservableObject {
    @Published var checkedDays: [Int] = []
    @Published var month: Int = 0
    @Published var todayChecked = false
    @Published var nextRewardCoin: Int64 = 0
    @Published var balance: Int64 = 0
    @Published var toast: String?

    private let store: AttendanceStore = KoinHelper().attendanceStore()
    private let points: PointsRepository = KoinHelper().pointsRepository()

    func load() {
        store.loadMonthly(year: nil, month: nil)
        observeState()
        observeRewards()
        observeBalance()
    }
    func checkIn() { store.checkIn() }

    private func observeState() {
        // StateFlow 구독: CollectFlow 헬퍼(아래 KoinHelper와 함께 shared 에 노출) 사용
        FlowCollector().collectAttendance(store: store) { [weak self] s in
            self?.checkedDays = s.checkedDays.map { $0.intValue }
            self?.month = Int(s.month)
            self?.todayChecked = s.todayChecked
            self?.nextRewardCoin = s.nextReward?.coin ?? 0
        }
    }
    private func observeRewards() {
        FlowCollector().collectRewards(store: store) { [weak self] ev in
            self?.toast = "출석 완료! 🪙+\(ev.awardedCoin)"
        }
    }
    private func observeBalance() {
        FlowCollector().collectBalance(repo: points) { [weak self] v in
            self?.balance = v.int64Value
        }
    }
}
```

> KMM `StateFlow`/`SharedFlow` 는 Swift 에서 직접 구독이 번거롭다. **shared `iosMain` 에 Flow→콜백 브리지(`FlowCollector`, `KoinHelper`)를 노출**해야 한다(아래 Step 2). 이는 [[reference-kmm-suspend-throws]] 와 함께 KMM-iOS 상호운용 표준 패턴.

- [ ] **Step 2: shared iosMain Flow 브리지 + Koin 접근자**

Create `shared/src/iosMain/kotlin/com/nomadclub/cashchat/shared/di/IosBridges.kt`:

```kotlin
package com.nomadclub.cashchat.shared.di

import com.nomadclub.cashchat.shared.attendance.AttendanceStore
import com.nomadclub.cashchat.shared.attendance.AttendanceUiState
import com.nomadclub.cashchat.shared.attendance.CheckInRewardEvent
import com.nomadclub.cashchat.shared.points.PointsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class KoinHelper : KoinComponent {
    private val store: AttendanceStore by inject()
    private val points: PointsRepository by inject()
    fun attendanceStore(): AttendanceStore = store
    fun pointsRepository(): PointsRepository = points
}

class FlowCollector {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    fun collectAttendance(store: AttendanceStore, onEach: (AttendanceUiState) -> Unit) {
        scope.launch { store.state.collect { onEach(it) } }
    }
    fun collectRewards(store: AttendanceStore, onEach: (CheckInRewardEvent) -> Unit) {
        scope.launch { store.rewardEvents.collect { onEach(it) } }
    }
    fun collectBalance(repo: PointsRepository, onEach: (Long) -> Unit) {
        scope.launch { repo.balance.collect { onEach(it) } }
    }
}
```

- [ ] **Step 3: BenefitZoneView + MainTabView**

`BenefitZoneView.swift`:
```swift
import SwiftUI

struct BenefitZoneView: View {
    @StateObject private var vm = AttendanceViewModel()
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    Text("혜택존").font(.title).bold()
                    Spacer()
                    Text("🪙 \(vm.balance)").bold().foregroundColor(Color(red: 0.69, green: 0.49, blue: 0))
                }
                AttendanceWidgetView(
                    month: vm.month, checkedDays: Set(vm.checkedDays),
                    todayChecked: vm.todayChecked, nextRewardCoin: vm.nextRewardCoin,
                    onCheckIn: { vm.checkIn() }
                )
                PhasePlaceholderView(label: "데일리 미션 (Phase 3)")
                PhasePlaceholderView(label: "리워드 광고 (Phase 2)")
                PhasePlaceholderView(label: "TNK Factory 오퍼월 (Phase 4)")
            }.padding()
        }
        .onAppear { vm.load() }
        .overlay(alignment: .bottom) {
            if let t = vm.toast { Text(t).padding().background(.black.opacity(0.8))
                .foregroundColor(.white).cornerRadius(8).padding() }
        }
    }
}

struct PhasePlaceholderView: View {
    let label: String
    var body: some View {
        Text(label).foregroundColor(Color(white: 0.7)).bold()
            .frame(maxWidth: .infinity, alignment: .leading).padding()
            .background(Color(white: 0.95)).cornerRadius(16)
    }
}
```

`AttendanceWidgetView.swift`:
```swift
import SwiftUI

struct AttendanceWidgetView: View {
    let month: Int
    let checkedDays: Set<Int>
    let todayChecked: Bool
    let nextRewardCoin: Int64
    let onCheckIn: () -> Void

    private let primary = Color(red: 0.36, green: 0.42, blue: 0.98)
    private let accent = Color(red: 1.0, green: 0.72, blue: 0)
    private let unchecked = Color(red: 0.88, green: 0.86, blue: 0.94)
    private let columns = Array(repeating: GridItem(.flexible(), spacing: 8), count: 7)

    private var todayNum: Int {
        guard let m = checkedDays.max() else { return 1 }
        return todayChecked ? m : m + 1
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("\(month)월 출석체크").bold()
            LazyVGrid(columns: columns, spacing: 8) {
                ForEach(1...31, id: \.self) { day in
                    Text("\(day)").font(.caption2).bold().foregroundColor(.white)
                        .frame(width: 28, height: 28)
                        .background(color(for: day)).clipShape(Circle())
                }
            }
            Text("오늘 보상: 🪙+\(nextRewardCoin)").font(.footnote)
            Button(action: onCheckIn) {
                Text(todayChecked ? "오늘 출석 완료" : "출석 도장 찍기")
                    .bold().foregroundColor(.white).frame(maxWidth: .infinity, minHeight: 48)
                    .background(todayChecked ? unchecked : primary).cornerRadius(14)
            }.disabled(todayChecked)
        }
        .padding()
        .background(LinearGradient(colors: [Color(red:0.91,green:0.88,blue:1.0), Color(red:0.98,green:0.98,blue:1.0)],
                                   startPoint: .top, endPoint: .bottom))
        .cornerRadius(20)
    }

    private func color(for day: Int) -> Color {
        if checkedDays.contains(day) { return primary }
        if day == todayNum && !todayChecked { return accent }
        return unchecked
    }
}
```

`MainTabView.swift`:
```swift
import SwiftUI

struct MainTabView: View {
    var body: some View {
        TabView {
            Text("채팅").tabItem { Label("채팅", systemImage: "message") }
            BenefitZoneView().tabItem { Label("혜택존", systemImage: "gift") }
            Text("상점").tabItem { Label("상점", systemImage: "bag") }
            SettingsView().tabItem { Label("MY", systemImage: "person") }
        }
    }
}
```

`ContentView.swift`: 로그인 완료 분기에서 메인 화면을 `MainTabView()` 로 표시하도록 수정(기존 인증 상태 판단 로직 확인 후 연결).

- [ ] **Step 4: shared 재빌드 + Xcode 빌드**

Run:
```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
./gradlew :shared:embedAndSignAppleFrameworkForXcode -q || ./gradlew :shared:assembleCashChatSharedDebugXCFramework -q
```
그 후 Xcode 에서 `CashChatIOS.xcodeproj` 빌드(시뮬레이터). Expected: 빌드 성공, 혜택존 탭에서 출석 그리드 렌더 + 출석 버튼 동작.

- [ ] **Step 5: 수동 검증** — 시뮬레이터에서 로그인 → 혜택존 탭 → 출석 도장 → 토스트/코인 증가/버튼 비활성 확인.

- [ ] **Step 6: Commit**

```bash
git add apps/frontend/shared/src/iosMain/ CashChatIOS/CashChatIOS/
git commit -m "feat(ios): 메인 탭 셸 + 혜택존 출석 화면 추가"
```

---

## 최종 통합 검증

### Task 19: 전체 빌드 + 테스트 + 진행 로그 마감

- [ ] **Step 1: shared 테스트 전체**

Run: `./gradlew :shared:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 2: Android 빌드**

Run: `./gradlew :app:assembleDevDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 진행 로그 최종 요약 append**

`docs/superpowers/specs/2026-06-07-benefit-zone-progress.md` 에 Phase F+1 완료 요약(완료 Task 목록, 미결: iOS 실기기 검증·`GET /api/points/me` BE 대기·후속 Phase 2~4) append.

- [ ] **Step 4: 브랜치 마무리**

`superpowers:finishing-a-development-branch` 스킬로 PR/머지 옵션 결정(커밋·머지는 사용자 승인 후).

---

## 후속 (별도 plan)

- **Phase 2 — 리워드 광고:** AdMob SDK + SSV. spec §6 기준 별도 plan.
- **Phase 3 — 데일리 미션:** 미션 API 계약 + 어댑터.
- **Phase 4 — TNK 오퍼월:** `OfferwallProvider` expect/actual + 네이티브 SDK(앱 등록 후).
- **인증 이관:** 기존 Android Retrofit 인증 경로를 shared 인증 클라이언트로 통합(회귀 위험 격리 위해 별도 작업).
