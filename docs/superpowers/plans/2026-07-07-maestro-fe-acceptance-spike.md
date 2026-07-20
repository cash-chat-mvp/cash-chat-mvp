# Maestro FE 인수 테스트 스파이크 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 백엔드 서버 없이(hermetic Fake) Maestro로 핵심 3개 여정(AI 채팅·구글 보상형 광고·TNK 오퍼월)을 GWT 인수 기준에 따라 관통/인수 검증한다.

**Architecture:** Android `mock` product flavor를 추가하고, Koin에서 **딱 3개만 override**한다 — (1) `HttpClient`를 Ktor `MockEngine` 기반 인앱 Fake 백엔드로, (2) `RewardedAdPresenter`(AdMob seam)를 Fake로, (3) `OfferwallLauncher`(TNK seam)를 Fake로. 나머지 `*Api`·Store는 무변경으로 Fake 백엔드를 호출하므로 직렬화·SSE 파서·에러 매핑까지 실제 경로가 관통된다. 로그인/네비 게이트는 mock 부팅 시 `TokenDataStore`에 `role="MEMBER"` 세션을 직접 심어 우회한다.

**Tech Stack:** Kotlin 2.0.21, AGP 9.0.1, Java 21, Jetpack Compose (Material3), Koin DI, Ktor client(`ktor-client-mock`), Maestro (E2E).

## Global Constraints

- 패키지 루트: `com.nomadclub.cashchat`. 앱 소스는 `app/src/main/java/...` (kotlin 아님 `java` 디렉터리).
- Android `applicationId`는 mock에서도 **`com.nomadclub.cashchat` 유지**(applicationIdSuffix 금지 — `google-services.json` package_name 불일치로 google-services 플러그인이 빌드 실패함).
- 커밋: Conventional Commits. **subject는 한국어로 시작**(commitlint subject-case가 영어 약어/대문자 시작을 거부). 예: `feat: mock 플레이버 추가`.
- 커밋 금지: `build/`, `local.properties`, `.gradle-local/`, `.idea`.
- 모든 Maestro flow는 상단 주석에 대응 `US-*`/`AC-FE-*` ID를 역참조한다.
- 대상 플랫폼: Android 에뮬레이터. iOS 범위 외.
- 작업 디렉터리: `apps/frontend`. gradle 명령은 `cd apps/frontend` 후 실행.

## File Structure

**신규 (mock 소스셋 — `app/src/mock/java/com/nomadclub/cashchat/`)**
- `mock/MockBackendState.kt` — 인메모리 백엔드 상태(잔액·에너지·quota·시나리오)
- `mock/FakeBackendEngine.kt` — Ktor `MockEngine` 팩토리(경로별 canned 응답 + SSE)
- `mock/FakeRewardedAdPresenter.kt` — AdMob seam Fake
- `mock/FakeOfferwallLauncher.kt` — TNK seam Fake
- `mock/MockModule.kt` — Koin override 모듈(HttpClient/Presenter/Launcher/State)
- `flavor/FlavorModules.kt` (mock 판) — override 모듈 + 세션 심기 + 시나리오 적용

**신규 (real 소스셋 — `app/src/real/java/com/nomadclub/cashchat/`)**
- `flavor/FlavorModules.kt` (real 판) — 전부 noop

**신규 (main 소스셋)**
- `ads/RewardedAdPresenter.kt` — AdMob seam 인터페이스
- `offerwall/OfferwallLauncher.kt` — TNK seam 인터페이스

**수정 (main)**
- `app/build.gradle.kts` — flavor + mockImplementation deps
- `di/AppModule.kt` — seam 인터페이스 바인딩
- `CashChatApplication.kt` — FlavorModules 연동(override 모듈 로드·세션 심기·mock 시 SDK init 스킵)
- `MainActivity.kt` — FlavorModules 시나리오 적용 훅
- `ads/RewardedAdManager.kt` / `offerwall/TnkOfferwallManager.kt` — 인터페이스 구현 선언
- 광고/오퍼월 주입부 3~4곳 — 주입 타입을 인터페이스로 교체(기계적)

**신규 (Maestro — `apps/frontend/maestro/`)**
- `config.yaml`, `README.md`, `flows/chat/*.yaml`, `flows/rewarded-ad/*.yaml`, `flows/offerwall/*.yaml`

**신규/수정 (docs)**
- 신규 `docs/domains/chat/{README.md,_glossary.md,US-CHAT-001-ai-chat-response.md}`
- 수정 `docs/domains/README.md`(도메인 인덱스), `docs/domains/reward/US-REWARD-002-rewarded-ad.md`, `US-REWARD-003-tnk-offerwall.md`(FE 관통 AC 추가)

---

### Task 1: mock/real product flavor + 의존성 추가

**Files:**
- Modify: `apps/frontend/app/build.gradle.kts`

**Interfaces:**
- Produces: `BuildConfig.IS_MOCK`(Boolean) 플래그, `mock`·`real` 두 flavor, mock 소스셋에서 쓸 ktor 의존성.

- [ ] **Step 1: flavor 블록 추가**

`android { }` 안 `buildTypes { }` 아래에 추가:
```kotlin
    flavorDimensions += "backend"
    productFlavors {
        create("real") {
            dimension = "backend"
            isDefault = true
            buildConfigField("Boolean", "IS_MOCK", "false")
        }
        create("mock") {
            dimension = "backend"
            // applicationIdSuffix 사용 금지(google-services package_name 불일치 방지)
            buildConfigField("Boolean", "IS_MOCK", "true")
        }
    }
```

- [ ] **Step 2: mock 소스셋 의존성 추가**

`dependencies { }` 안에 추가(카탈로그 alias는 `apps/frontend/gradle/libs.versions.toml`에 이미 존재):
```kotlin
    // mock 플레이버 전용 — 인앱 Fake 백엔드(Ktor MockEngine)
    "mockImplementation"(libs.ktor.client.core)
    "mockImplementation"(libs.ktor.client.mock)
    "mockImplementation"(libs.kotlinx.coroutines.core)
```

- [ ] **Step 3: real/mock 소스셋 디렉터리 생성**

```bash
cd apps/frontend
mkdir -p app/src/real/java/com/nomadclub/cashchat/flavor
mkdir -p app/src/mock/java/com/nomadclub/cashchat/mock
mkdir -p app/src/mock/java/com/nomadclub/cashchat/flavor
```

- [ ] **Step 4: real flavor 빌드로 flavor 설정 검증**

Run: `cd apps/frontend && ./gradlew :app:assembleRealDebug -x lint`
Expected: BUILD SUCCESSFUL (mock 소스셋이 아직 비어 assembleMockDebug는 뒤 Task에서).

- [ ] **Step 5: Commit**

```bash
git add apps/frontend/app/build.gradle.kts
git commit -m "feat: mock/real 플레이버와 인앱 Fake 백엔드 의존성 추가 (CC-391)"
```

---

### Task 2: RewardedAdPresenter seam 추출 (AdMob)

**Files:**
- Create: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/ads/RewardedAdPresenter.kt`
- Modify: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/ads/RewardedAdManager.kt`
- Modify: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/di/AppModule.kt:116`
- Modify (주입 타입 교체): `feature/chat/EnergyGateBottomSheet.kt`, `feature/chat/ChatScreen.kt`, `feature/rewards/RewardAdCard.kt`

**Interfaces:**
- Produces: `interface RewardedAdPresenter { fun preload(context); fun isReady(): Boolean; fun show(activity, nonce, onRewarded, onDismissed, onNotReady) }` — 시그니처는 기존 `RewardedAdManager` 공개 메서드와 동일.

- [ ] **Step 1: 인터페이스 생성**

`RewardedAdPresenter.kt`:
```kotlin
package com.nomadclub.cashchat.ads

import android.app.Activity
import android.content.Context

/** 보상형 광고 표시 seam. 프로덕션은 [RewardedAdManager](AdMob), 테스트는 Fake. */
interface RewardedAdPresenter {
    fun preload(context: Context)
    fun isReady(): Boolean
    fun show(
        activity: Activity,
        nonce: String? = null,
        onRewarded: (amount: Int) -> Unit,
        onDismissed: () -> Unit,
        onNotReady: () -> Unit = {},
    )
}
```

- [ ] **Step 2: RewardedAdManager가 인터페이스 구현하도록 선언**

`RewardedAdManager.kt`:
- 클래스 선언을 `class RewardedAdManager(private val appConfig: AppConfig) : RewardedAdPresenter {` 로 변경.
- `fun preload(`, `fun isReady(`, `fun show(` 앞에 각각 `override` 추가.

- [ ] **Step 3: DI 바인딩을 인터페이스로 변경**

`AppModule.kt:116` 를:
```kotlin
    single<RewardedAdPresenter> { com.nomadclub.cashchat.ads.RewardedAdManager(get()) }
```

- [ ] **Step 4: 주입부 타입 교체(기계적)**

아래 파일에서 주입/파라미터 타입 `RewardedAdManager` → `RewardedAdPresenter` 로 변경(호출 메서드 `preload/show/isReady`는 인터페이스에 동일하게 존재):
- `feature/chat/EnergyGateBottomSheet.kt` — `adManager` 주입 타입(`koinInject<RewardedAdManager>()` 또는 파라미터).
- `feature/chat/ChatScreen.kt` — AdGate용 `adManager` 주입 타입.
- `feature/rewards/RewardAdCard.kt` — `adManager` 주입 타입.

각 파일에서 `import com.nomadclub.cashchat.ads.RewardedAdManager` 가 있으면 `RewardedAdPresenter` import로 교체(또는 병행).

- [ ] **Step 5: real 빌드로 seam 무결성 검증(동작 불변)**

Run: `cd apps/frontend && ./gradlew :app:assembleRealDebug -x lint`
Expected: BUILD SUCCESSFUL. 컴파일러가 누락된 타입 교체를 잡아준다.

- [ ] **Step 6: Commit**

```bash
git add apps/frontend/app/src/main
git commit -m "refactor: 보상형 광고 표시를 RewardedAdPresenter 인터페이스로 분리 (CC-391)"
```

---

### Task 3: OfferwallLauncher seam 추출 (TNK)

**Files:**
- Create: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/offerwall/OfferwallLauncher.kt`
- Modify: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/offerwall/TnkOfferwallManager.kt`
- Modify: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/di/AppModule.kt:119`
- Modify (주입 타입 교체): `feature/rewards/BenefitZoneScreen.kt`

**Interfaces:**
- Produces: `interface OfferwallLauncher { suspend fun launch(activity: Activity): Result<Unit> }`.

- [ ] **Step 1: 인터페이스 생성**

`OfferwallLauncher.kt`:
```kotlin
package com.nomadclub.cashchat.offerwall

import android.app.Activity

/** 오퍼월 진입 seam. 프로덕션은 [TnkOfferwallManager](TNK), 테스트는 Fake. */
interface OfferwallLauncher {
    suspend fun launch(activity: Activity): Result<Unit>
}
```

- [ ] **Step 2: TnkOfferwallManager가 인터페이스 구현하도록 선언**

`TnkOfferwallManager.kt`:
- `class TnkOfferwallManager(private val offerwallApi: OfferwallApi) : OfferwallLauncher {`
- `suspend fun launch(` 앞에 `override` 추가.

- [ ] **Step 3: DI 바인딩을 인터페이스로 변경**

`AppModule.kt:119` 를:
```kotlin
    single<OfferwallLauncher> { com.nomadclub.cashchat.offerwall.TnkOfferwallManager(get()) }
```

- [ ] **Step 4: 주입부 타입 교체**

`feature/rewards/BenefitZoneScreen.kt` 에서 `offerwallManager` 주입/파라미터 타입 `TnkOfferwallManager` → `OfferwallLauncher`.

- [ ] **Step 5: real 빌드 검증**

Run: `cd apps/frontend && ./gradlew :app:assembleRealDebug -x lint`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add apps/frontend/app/src/main
git commit -m "refactor: 오퍼월 진입을 OfferwallLauncher 인터페이스로 분리 (CC-391)"
```

---

### Task 4: MockBackendState + FakeBackendEngine (인앱 Fake 백엔드)

**Files:**
- Create: `apps/frontend/app/src/mock/java/com/nomadclub/cashchat/mock/MockBackendState.kt`
- Create: `apps/frontend/app/src/mock/java/com/nomadclub/cashchat/mock/FakeBackendEngine.kt`
- Test: `apps/frontend/app/src/mock/java/com/nomadclub/cashchat/mock/FakeBackendEngineTest.kt` (mockUnitTest 소스셋: `app/src/testMock/...`)

**Interfaces:**
- Produces: `class MockBackendState(var scenario: String = "happy")` with `pointsBalance:Long`, `energy:Int`, `maxEnergy:Int`, `usedToday:Int`, `dailyLimit:Int` (var). `fun fakeBackendEngine(state: MockBackendState): HttpClientEngine`.
- Consumes(Task 6/7): 엔진은 `com.nomadclub.cashchat.shared.core.network.createCashChatHttpClient(baseUrl, tokenProvider, engine)`의 `engine` 인자로 주입.

- [ ] **Step 1: MockBackendState 작성**

`MockBackendState.kt`:
```kotlin
package com.nomadclub.cashchat.mock

/**
 * 인앱 Fake 백엔드의 가변 상태. Koin single 로 공유되어
 * FakeBackendEngine(응답 생성)과 Fake SDK(보상 반영)가 같은 인스턴스를 본다.
 * scenario 는 Maestro launchArguments 의 "scenario" extra 로 주입(MainActivity → FlavorModules).
 */
class MockBackendState {
    @Volatile var scenario: String = "happy"      // happy | chat_error | ad_quota_exceeded | offerwall_fail
    @Volatile var pointsBalance: Long = 0
    @Volatile var energy: Int = 10
    @Volatile var maxEnergy: Int = 10
    @Volatile var usedToday: Int = 0
    @Volatile var dailyLimit: Int = 5

    val remaining: Int get() = (dailyLimit - usedToday).coerceAtLeast(0)

    /** ad_quota_exceeded 시나리오면 한도를 소진 상태로 초기화. */
    fun applyScenarioDefaults() {
        if (scenario == "ad_quota_exceeded") usedToday = dailyLimit
    }
}
```

- [ ] **Step 2: 실패하는 테스트 작성**

먼저 mock 유닛테스트 소스셋 디렉터리 생성:
```bash
cd apps/frontend && mkdir -p app/src/testMock/java/com/nomadclub/cashchat/mock
```
`app/src/testMock/java/com/nomadclub/cashchat/mock/FakeBackendEngineTest.kt`:
```kotlin
package com.nomadclub.cashchat.mock

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeBackendEngineTest {
    private fun client(state: MockBackendState) = HttpClient(fakeBackendEngine(state)) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    @Test
    fun `points balance reflects state`() = runTest {
        val state = MockBackendState().apply { pointsBalance = 1500 }
        val body = client(state).get("https://mock.local/api/points/me").bodyAsText()
        assertTrue(body.contains("1500"))
    }

    @Test
    fun `quota reflects remaining`() = runTest {
        val state = MockBackendState().apply { usedToday = 5; dailyLimit = 5 }
        val body = client(state).get("https://mock.local/api/ads/reward/quota").bodyAsText()
        assertTrue(body.contains("\"remaining\":0"))
    }
}
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `cd apps/frontend && ./gradlew :app:testMockDebugUnitTest --tests "*FakeBackendEngineTest*"`
Expected: FAIL (`fakeBackendEngine` 미정의 컴파일 에러).

- [ ] **Step 4: FakeBackendEngine 구현**

`FakeBackendEngine.kt`:
```kotlin
package com.nomadclub.cashchat.mock

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel

/**
 * 경로별 canned 응답을 돌려주는 인앱 Fake 백엔드.
 * 모든 *Api 가 이 엔진을 통해 호출되므로 직렬화/에러매핑/SSE 파서가 실제로 관통된다.
 */
fun fakeBackendEngine(state: MockBackendState): HttpClientEngine = MockEngine { request ->
    val path = request.url.encodedPath
    val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    fun json(body: String) = respond(body, HttpStatusCode.OK, jsonHeaders)

    when {
        // ── 채팅 ──
        request.method == HttpMethod.Post && path == "/api/v1/chat/conversations" ->
            json("""{"conversationId":1,"title":"mock","createdAt":"2026-07-07T00:00:00Z","updatedAt":"2026-07-07T00:00:00Z"}""")

        request.method == HttpMethod.Get && path == "/api/v1/chat/conversations" -> json("[]")

        request.method == HttpMethod.Post && path == "/api/v1/chat/stream" -> {
            val sse = if (state.scenario == "chat_error") {
                "event: error\ndata: 응답 생성 중 오류가 발생했어요\n\n"
            } else {
                "data: 목킹응답: 반갑습니다 👋\n\nevent: done\ndata: [DONE]\n\n"
            }
            respond(
                ByteReadChannel(sse),
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, ContentType.Text.EventStream.toString()),
            )
        }

        // ── 잔액/에너지/진화 ──
        request.method == HttpMethod.Get && path == "/api/points/me" ->
            json("""{"balance":${state.pointsBalance}}""")
        request.method == HttpMethod.Get && path == "/api/energy/me" ->
            json("""{"energy":${state.energy},"maxEnergy":${state.maxEnergy}}""")
        request.method == HttpMethod.Get && path == "/api/evolution/me" ->
            json("""{"level":1,"isMaxLevel":false}""")

        // ── 광고 quota/nonce ──
        request.method == HttpMethod.Get && path == "/api/ads/reward/quota" ->
            json("""{"usedToday":${state.usedToday},"dailyLimit":${state.dailyLimit},"remaining":${state.remaining},"resetAtKst":"2026-07-08T00:00:00+09:00"}""")
        request.method == HttpMethod.Post && path == "/api/ads/reward/issue-nonce" ->
            json("""{"nonce":"mock-nonce","expiresAt":"2026-07-07T00:05:00Z"}""")

        // ── 오퍼월 토큰(Fake launcher 가 우회하므로 보통 미호출) ──
        request.method == HttpMethod.Post && path == "/api/offerwall/tnk/user-token" ->
            json("""{"token":"mock-tnk-token"}""")

        // ── 기본값: 빈 객체(대부분 호출부가 runCatching 로 보호) ──
        else -> json("{}")
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `cd apps/frontend && ./gradlew :app:testMockDebugUnitTest --tests "*FakeBackendEngineTest*"`
Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```bash
git add apps/frontend/app/src/mock apps/frontend/app/src/testMock
git commit -m "feat: 인앱 Fake 백엔드(MockBackendState + Ktor MockEngine) 추가 (CC-391)"
```

---

### Task 5: Fake SDK presenters (보상형 광고 / 오퍼월)

**Files:**
- Create: `apps/frontend/app/src/mock/java/com/nomadclub/cashchat/mock/FakeRewardedAdPresenter.kt`
- Create: `apps/frontend/app/src/mock/java/com/nomadclub/cashchat/mock/FakeOfferwallLauncher.kt`
- Test: `apps/frontend/app/src/testMock/java/com/nomadclub/cashchat/mock/FakeSdkTest.kt`

**Interfaces:**
- Consumes: `RewardedAdPresenter`(Task 2), `OfferwallLauncher`(Task 3), `MockBackendState`(Task 4), `PointsRepository`(shared).
- Produces: `FakeRewardedAdPresenter(state)`, `FakeOfferwallLauncher(state, pointsRepository)`.

- [ ] **Step 1: 실패하는 테스트 작성**

`FakeSdkTest.kt`:
```kotlin
package com.nomadclub.cashchat.mock

import com.nomadclub.cashchat.shared.points.PointsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakePointsRepo : PointsRepository {
    private val _b = MutableStateFlow(0L)
    override val balance: StateFlow<Long> = _b
    override suspend fun refresh() {}
    override fun applyDelta(delta: Long) { _b.value += delta }
    override fun reset() { _b.value = 0 }
}

class FakeSdkTest {
    @Test
    fun `rewarded ad increments usedToday and fires callbacks`() {
        val state = MockBackendState().apply { usedToday = 0 }
        var rewarded = false; var dismissed = false
        FakeRewardedAdPresenter(state).show(
            activity = org.mockito.Mockito.mock(android.app.Activity::class.java),
            onRewarded = { rewarded = true }, onDismissed = { dismissed = true },
        )
        assertEquals(1, state.usedToday)
        assertTrue(rewarded && dismissed)
    }

    @Test
    fun `offerwall success bumps balance`() = runTest {
        val state = MockBackendState()
        val repo = FakePointsRepo()
        val result = FakeOfferwallLauncher(state, repo)
            .launch(org.mockito.Mockito.mock(android.app.Activity::class.java))
        assertTrue(result.isSuccess)
        assertEquals(1500L, repo.balance.value)
    }

    @Test
    fun `offerwall fail scenario returns failure`() = runTest {
        val state = MockBackendState().apply { scenario = "offerwall_fail" }
        val result = FakeOfferwallLauncher(state, FakePointsRepo())
            .launch(org.mockito.Mockito.mock(android.app.Activity::class.java))
        assertTrue(result.isFailure)
    }
}
```

먼저 mockito 테스트 의존성을 추가(`app/build.gradle.kts` dependencies):
```kotlin
    "testMockImplementation"("org.mockito:mockito-core:5.11.0")
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd apps/frontend && ./gradlew :app:testMockDebugUnitTest --tests "*FakeSdkTest*"`
Expected: FAIL (Fake 클래스 미정의).

- [ ] **Step 3: FakeRewardedAdPresenter 구현**

`FakeRewardedAdPresenter.kt`:
```kotlin
package com.nomadclub.cashchat.mock

import android.app.Activity
import android.content.Context
import com.nomadclub.cashchat.ads.RewardedAdPresenter

/**
 * AdMob 대체. show() 즉시 보상 적립을 시뮬레이션한다:
 * usedToday++ → 다음 /api/ads/reward/quota 조회가 증가분 반영 →
 * AdRewardStore.awaitRewardApplied 가 즉시(attempt 0) APPLIED 판정.
 */
class FakeRewardedAdPresenter(private val state: MockBackendState) : RewardedAdPresenter {
    override fun preload(context: Context) { /* no-op */ }
    override fun isReady(): Boolean = true
    override fun show(
        activity: Activity,
        nonce: String?,
        onRewarded: (amount: Int) -> Unit,
        onDismissed: () -> Unit,
        onNotReady: () -> Unit,
    ) {
        state.usedToday += 1
        state.energy = state.maxEnergy   // refreshEnergyOnly 가 충전 관측
        onRewarded(10)
        onDismissed()
    }
}
```

- [ ] **Step 4: FakeOfferwallLauncher 구현**

`FakeOfferwallLauncher.kt`:
```kotlin
package com.nomadclub.cashchat.mock

import android.app.Activity
import com.nomadclub.cashchat.offerwall.OfferwallLauncher
import com.nomadclub.cashchat.shared.points.PointsRepository

/**
 * TNK 오퍼월 대체. 성공 시 오퍼 완료 콜백(비동기 SSV) 을 시뮬레이션 —
 * 잔액을 올리고 PointsRepository.refresh() 로 화면을 갱신한다.
 * offerwall_fail 시나리오면 실패를 반환(진입 실패 토스트 유도).
 */
class FakeOfferwallLauncher(
    private val state: MockBackendState,
    private val pointsRepository: PointsRepository,
) : OfferwallLauncher {
    override suspend fun launch(activity: Activity): Result<Unit> {
        if (state.scenario == "offerwall_fail") {
            return Result.failure(IllegalStateException("mock offerwall token fail"))
        }
        state.pointsBalance += 1500
        pointsRepository.refresh()   // /api/points/me 재조회 → balance StateFlow 갱신
        return Result.success(Unit)
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `cd apps/frontend && ./gradlew :app:testMockDebugUnitTest --tests "*FakeSdkTest*"`
Expected: PASS (3 tests).

- [ ] **Step 6: Commit**

```bash
git add apps/frontend/app/src/mock apps/frontend/app/src/testMock apps/frontend/app/build.gradle.kts
git commit -m "feat: 보상형 광고/오퍼월 Fake SDK presenter 추가 (CC-391)"
```

---

### Task 6: mock Koin 모듈 + FlavorModules + Application/Activity 연동

**Files:**
- Create: `apps/frontend/app/src/mock/java/com/nomadclub/cashchat/mock/MockModule.kt`
- Create: `apps/frontend/app/src/mock/java/com/nomadclub/cashchat/flavor/FlavorModules.kt`
- Create: `apps/frontend/app/src/real/java/com/nomadclub/cashchat/flavor/FlavorModules.kt`
- Modify: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/CashChatApplication.kt`
- Modify: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/MainActivity.kt`

**Interfaces:**
- Produces: `object FlavorModules { val overrides: List<Module>; fun onAppCreated(koin: Koin); fun onMainActivityCreated(activity: android.app.Activity) }` — real/mock 두 판이 동일 FQN.

- [ ] **Step 1: mock Koin override 모듈**

`MockModule.kt`:
```kotlin
package com.nomadclub.cashchat.mock

import com.nomadclub.cashchat.ads.RewardedAdPresenter
import com.nomadclub.cashchat.offerwall.OfferwallLauncher
import com.nomadclub.cashchat.shared.core.network.TokenProvider
import com.nomadclub.cashchat.shared.core.network.createCashChatHttpClient
import com.nomadclub.cashchat.shared.points.PointsRepository
import org.koin.dsl.module

/** 딱 3개만 override: HttpClient(MockEngine) / RewardedAdPresenter / OfferwallLauncher. */
val mockModule = module {
    single { MockBackendState() }
    // HttpClient override → 모든 *Api 가 Fake 백엔드를 침
    single {
        createCashChatHttpClient(
            baseUrl = "https://mock.local",
            tokenProvider = get<TokenProvider>(),
            engine = fakeBackendEngine(get<MockBackendState>()),
        )
    }
    single<RewardedAdPresenter> { FakeRewardedAdPresenter(get<MockBackendState>()) }
    single<OfferwallLauncher> { FakeOfferwallLauncher(get<MockBackendState>(), get<PointsRepository>()) }
}
```

- [ ] **Step 2: mock FlavorModules**

`app/src/mock/.../flavor/FlavorModules.kt`:
```kotlin
package com.nomadclub.cashchat.flavor

import android.app.Activity
import com.nomadclub.cashchat.core.data.TokenDataStore
import com.nomadclub.cashchat.mock.MockBackendState
import com.nomadclub.cashchat.mock.mockModule
import com.nomadclub.cashchat.shared.auth.model.AuthResponse
import kotlinx.coroutines.runBlocking
import org.koin.core.Koin
import org.koin.core.module.Module

object FlavorModules {
    val overrides: List<Module> = listOf(mockModule)

    /** 로그인/네비 게이트 우회: MEMBER 세션을 DataStore 에 직접 심는다. */
    fun onAppCreated(koin: Koin) {
        runBlocking {
            koin.get<TokenDataStore>().saveAuthResponse(
                AuthResponse(userId = 1L, role = "MEMBER", accessToken = "mock-token", refreshToken = null),
            )
        }
    }

    /** Maestro launchArguments 의 "scenario" extra → MockBackendState 반영. */
    fun onMainActivityCreated(activity: Activity) {
        val scenario = activity.intent?.getStringExtra("scenario") ?: "happy"
        val koin = org.koin.core.context.GlobalContext.get()
        koin.get<MockBackendState>().apply {
            this.scenario = scenario
            applyScenarioDefaults()
        }
    }
}
```

- [ ] **Step 3: real FlavorModules (noop)**

`app/src/real/.../flavor/FlavorModules.kt`:
```kotlin
package com.nomadclub.cashchat.flavor

import android.app.Activity
import org.koin.core.Koin
import org.koin.core.module.Module

object FlavorModules {
    val overrides: List<Module> = emptyList()
    fun onAppCreated(koin: Koin) { /* no-op */ }
    fun onMainActivityCreated(activity: Activity) { /* no-op */ }
}
```

- [ ] **Step 4: CashChatApplication 연동**

`CashChatApplication.kt` `onCreate` 를 아래로 교체(모듈 로드에 `FlavorModules.overrides` 추가, mock 이면 실 SDK init 스킵, 세션 심기):
```kotlin
    override fun onCreate() {
        super.onCreate()
        AndroidLocalLlmContext.init(this)
        val koin = startKoin {
            androidContext(this@CashChatApplication)
            modules(listOf(appModule, sharedDataModule(BuildConfig.BASE_URL)) + com.nomadclub.cashchat.flavor.FlavorModules.overrides)
        }.koin
        com.nomadclub.cashchat.flavor.FlavorModules.onAppCreated(koin)
        koin.get<RemoteConfigManager>().initialize()
        if (!BuildConfig.IS_MOCK) {
            MobileAds.initialize(this)
            TnkSession.applicationStarted(this)
        }
    }
```
> 참고: Koin 은 마지막 정의가 이깁니다(override). 만약 `DefinitionOverrideException` 이 뜨면 `startKoin { allowOverride(true); ... }` 를 추가하세요.

- [ ] **Step 5: MainActivity 시나리오 훅**

`MainActivity.kt` `onCreate` 안, `setContent { }` 직전에 추가:
```kotlin
        com.nomadclub.cashchat.flavor.FlavorModules.onMainActivityCreated(this)
```

- [ ] **Step 6: mock 빌드 + 유닛테스트 검증**

Run:
```bash
cd apps/frontend
./gradlew :app:assembleMockDebug -x lint
./gradlew :app:testMockDebugUnitTest
```
Expected: 둘 다 BUILD SUCCESSFUL / PASS.

- [ ] **Step 7: Commit**

```bash
git add apps/frontend/app/src
git commit -m "feat: mock 플레이버 Koin override와 로그인 우회 부트스트랩 배선 (CC-391)"
```

---

### Task 7: Maestro 설정 + 채팅 flow (walking skeleton)

**Files:**
- Create: `apps/frontend/maestro/config.yaml`
- Create: `apps/frontend/maestro/README.md`
- Create: `apps/frontend/maestro/flows/chat/ai-response.yaml`
- Create: `apps/frontend/maestro/flows/chat/stream-error.yaml`

**Interfaces:**
- Consumes: `com.nomadclub.cashchat` appId(mock 빌드), 채팅 selector(placeholder `"메시지를 입력하세요..."`, 전송 `"전송"`, canned 응답 `"목킹응답"`).

- [ ] **Step 1: Maestro 설치 확인 & mock APK 설치**

Run(에뮬레이터 실행 상태 가정):
```bash
which maestro || curl -Ls "https://get.maestro.mobile.dev" | bash
cd apps/frontend && ./gradlew :app:installMockDebug
```
Expected: `installMockDebug` BUILD SUCCESSFUL(앱이 에뮬레이터에 설치됨).

- [ ] **Step 2: config.yaml + README**

`maestro/config.yaml`:
```yaml
appId: com.nomadclub.cashchat
```
`maestro/README.md`:
```markdown
# Maestro FE 인수 테스트 (CC-391 스파이크)

백엔드 서버 없이 `mock` 플레이버(인앱 Fake 백엔드)로 핵심 여정을 검증한다.

## 실행
```bash
cd apps/frontend
./gradlew :app:installMockDebug        # 에뮬레이터에 mock APK 설치
maestro test maestro/flows             # 전체 flow 실행
maestro test maestro/flows/chat/ai-response.yaml   # 단일 flow
```
각 flow 는 `docs/domains` 의 US/AC ID 를 주석으로 역참조한다.
시나리오는 flow 의 `launchApp.arguments.scenario` 로 주입된다(happy | chat_error | ad_quota_exceeded | offerwall_fail).
```

- [ ] **Step 3: 채팅 happy flow**

`maestro/flows/chat/ai-response.yaml`:
```yaml
# US-CHAT-001 / AC-FE-01 — 메시지 전송 시 AI(목) 응답 수신
appId: com.nomadclub.cashchat
---
- launchApp:
    clearState: true
    arguments:
      scenario: "happy"
- tapOn: "메시지를 입력하세요..."
- inputText: "안녕"
- tapOn:
    id: "전송"
- assertVisible: "목킹응답: 반갑습니다 👋"
```
> `id: "전송"` 은 `contentDescription="전송"` 매칭. 텍스트가 안 잡히면 `tapOn: "전송"` 로 대체.

- [ ] **Step 4: happy flow 실행(green 확인)**

Run: `cd apps/frontend && maestro test maestro/flows/chat/ai-response.yaml`
Expected: Flow PASS (`✅`). 실패 시 `maestro studio` 로 실제 selector 확인 후 수정.

- [ ] **Step 5: 채팅 error flow**

`maestro/flows/chat/stream-error.yaml`:
```yaml
# US-CHAT-001 / AC-FE-02 — 스트림 에러 시 정상 응답이 렌더되지 않는다
appId: com.nomadclub.cashchat
---
- launchApp:
    clearState: true
    arguments:
      scenario: "chat_error"
- tapOn: "메시지를 입력하세요..."
- inputText: "안녕"
- tapOn:
    id: "전송"
- assertNotVisible: "목킹응답"
```
> 에러 UI 문자열을 assert 하려면 이 시나리오로 앱을 1회 구동해 실제 표시 텍스트(예: `응답 생성 중 오류가 발생했어요`)를 확인 후 `assertVisible` 로 강화한다. 미확인 시 위 부정 단언으로 유지.

- [ ] **Step 6: error flow 실행**

Run: `cd apps/frontend && maestro test maestro/flows/chat/stream-error.yaml`
Expected: Flow PASS.

- [ ] **Step 7: Commit**

```bash
git add apps/frontend/maestro
git commit -m "test: Maestro 채팅 여정 flow(happy/error) 추가 (CC-391)"
```

---

### Task 8: Maestro 보상형 광고 flow (happy / quota 초과)

**Files:**
- Create: `apps/frontend/maestro/flows/rewarded-ad/watch-reward.yaml`
- Create: `apps/frontend/maestro/flows/rewarded-ad/quota-exceeded.yaml`

**Interfaces:**
- Consumes: REWARDS 탭 selector, RewardAdCard 버튼 `"▶  광고 보기"`/`"내일 다시 만나요"`, 성공 토스트 `"에너지를 충전했어요!"`.

- [ ] **Step 1: 보상형 광고 happy flow**

`maestro/flows/rewarded-ad/watch-reward.yaml`:
```yaml
# US-REWARD-002 / AC-FE-01 — 광고 시청 완료 시 보상 반영 토스트
appId: com.nomadclub.cashchat
---
- launchApp:
    clearState: true
    arguments:
      scenario: "happy"
- tapOn: "혜택존"        # 하단 네비 REWARDS 탭 라벨(실제 라벨로 확인·수정)
- tapOn: "▶  광고 보기"
- assertVisible: "에너지를 충전했어요!"
```
> REWARDS 탭 라벨(`"혜택존"` 등)은 `MainScreen` 하단 네비의 실제 텍스트로 확인해 맞춘다.

- [ ] **Step 2: happy flow 실행**

Run: `cd apps/frontend && maestro test maestro/flows/rewarded-ad/watch-reward.yaml`
Expected: Flow PASS.

- [ ] **Step 3: quota 초과 flow**

`maestro/flows/rewarded-ad/quota-exceeded.yaml`:
```yaml
# US-REWARD-002 / AC-FE-02 — 일일 한도 소진 시 시청 불가 표시
appId: com.nomadclub.cashchat
---
- launchApp:
    clearState: true
    arguments:
      scenario: "ad_quota_exceeded"
- tapOn: "혜택존"
- assertVisible: "내일 다시 만나요"
```

- [ ] **Step 4: quota flow 실행**

Run: `cd apps/frontend && maestro test maestro/flows/rewarded-ad/quota-exceeded.yaml`
Expected: Flow PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/frontend/maestro
git commit -m "test: Maestro 보상형 광고 flow(happy/quota) 추가 (CC-391)"
```

---

### Task 9: Maestro 오퍼월 flow (happy / 토큰 실패)

**Files:**
- Create: `apps/frontend/maestro/flows/offerwall/complete-reward.yaml`
- Create: `apps/frontend/maestro/flows/offerwall/token-fail.yaml`

**Interfaces:**
- Consumes: 오퍼월 카드 `"TNK 오퍼월"`, 실패 토스트 `"오퍼월 진입에 실패했어요"`, 잔액 표시 `"🪙 1500"`(콤마 없음).

- [ ] **Step 1: 오퍼월 happy flow**

`maestro/flows/offerwall/complete-reward.yaml`:
```yaml
# US-REWARD-003 / AC-FE-01 — 오퍼 완료(목) 시 코인 잔액 증가
appId: com.nomadclub.cashchat
---
- launchApp:
    clearState: true
    arguments:
      scenario: "happy"
- tapOn: "혜택존"
- tapOn: "TNK 오퍼월"
- assertVisible: "🪙 1500"
```
> 잔액 표시는 `"🪙 $balance"`(천단위 콤마 없음). Fake launcher 가 +1500 후 refresh → 상단 잔액 칩이 `🪙 1500`.

- [ ] **Step 2: happy flow 실행**

Run: `cd apps/frontend && maestro test maestro/flows/offerwall/complete-reward.yaml`
Expected: Flow PASS.

- [ ] **Step 3: 오퍼월 실패 flow**

`maestro/flows/offerwall/token-fail.yaml`:
```yaml
# US-REWARD-003 / AC-FE-02 — 진입 실패 시 실패 토스트
appId: com.nomadclub.cashchat
---
- launchApp:
    clearState: true
    arguments:
      scenario: "offerwall_fail"
- tapOn: "혜택존"
- tapOn: "TNK 오퍼월"
- assertVisible: "오퍼월 진입에 실패했어요"
```

- [ ] **Step 4: 실패 flow 실행**

Run: `cd apps/frontend && maestro test maestro/flows/offerwall/token-fail.yaml`
Expected: Flow PASS.

- [ ] **Step 5: 전체 flow 일괄 실행**

Run: `cd apps/frontend && maestro test maestro/flows`
Expected: 6개 flow 전부 PASS.

- [ ] **Step 6: Commit**

```bash
git add apps/frontend/maestro
git commit -m "test: Maestro 오퍼월 flow(happy/실패) 추가 (CC-391)"
```

---

### Task 10: 유저 스토리 문서 (docs/domains)

**Files:**
- Create: `docs/domains/chat/README.md`, `docs/domains/chat/_glossary.md`, `docs/domains/chat/US-CHAT-001-ai-chat-response.md`
- Modify: `docs/domains/README.md` (도메인 인덱스 표에 chat 추가)
- Modify: `docs/domains/reward/US-REWARD-002-rewarded-ad.md`, `docs/domains/reward/US-REWARD-003-tnk-offerwall.md` (`## FE 관통 인수 기준` 섹션 추가)

**Interfaces:**
- Consumes: 기존 카탈로그 규칙(`docs/domains/README.md`), AC 스타일(`US-REWARD-001`).

- [ ] **Step 1: chat 도메인 README + glossary**

`docs/domains/chat/README.md`:
```markdown
# chat 도메인

AI 채팅(서버 SSE 스트리밍) 여정.

| ID | 스토리 | 상태 |
| -- | ----- | ---- |
| [US-CHAT-001](./US-CHAT-001-ai-chat-response.md) | AI 채팅 응답 수신 | draft |
```
`docs/domains/chat/_glossary.md`:
```markdown
# chat 용어

- **SSE 스트림**: `POST /api/v1/chat/stream` 이 `text/event-stream` 으로 토큰을 순차 방출. `event: done`/`data: [DONE]` 로 종료.
- **대화(conversation)**: 첫 메시지 전송 시 `POST /api/v1/chat/conversations` 로 lazy 생성.
```

- [ ] **Step 2: US-CHAT-001 작성**

`docs/domains/chat/US-CHAT-001-ai-chat-response.md`:
```markdown
---
id: US-CHAT-001
domain: chat
slug: ai-chat-response
status: draft
jira: CC-391
source: apps/frontend (feature/chat), docs/superpowers/specs/2026-07-07-maestro-fe-acceptance-spike-design.md
related-domains: [chat, energy]
---

# AI 채팅 응답 수신

## 스토리

사용자로서, 나는 채팅 입력창에 메시지를 보내면 AI의 응답을 화면에서 받고 싶다.
응답 스트림이 실패하면, 잘못된 응답이 정상처럼 표시되지 않기를 바란다.

## FE 관통 인수 기준 (Acceptance Criteria)

- [ ] **AC-FE-01 정상 응답 수신**
  Given mock 플레이버 앱이 로그인(MEMBER) 상태로 채팅 화면에 있다
  When 입력창에 메시지를 입력하고 전송한다
  Then 인앱 Fake 백엔드의 SSE 응답(`목킹응답: 반갑습니다 👋`)이 대화에 렌더된다.

- [ ] **AC-FE-02 스트림 에러**
  Given `chat_error` 시나리오다
  When 메시지를 전송한다
  Then 정상 응답(`목킹응답`)이 렌더되지 않는다(에러 경로로 분기).

## 검증 매핑 (Verification)

- FE(Maestro): `apps/frontend/maestro/flows/chat/ai-response.yaml`, `stream-error.yaml`
- 기술 상세: `docs/superpowers/specs/2026-07-07-maestro-fe-acceptance-spike-design.md`

## 관련

- 용어: [_glossary.md](./_glossary.md)
```

- [ ] **Step 3: domains 인덱스에 chat 추가**

`docs/domains/README.md` 의 도메인 표에 행 추가:
```markdown
| **chat** | AI 채팅(서버 SSE 스트리밍) | [chat/README.md](./chat/README.md) |
```

- [ ] **Step 4: US-REWARD-002 에 FE 관통 AC 추가**

`docs/domains/reward/US-REWARD-002-rewarded-ad.md` 의 `## 검증 매핑` 섹션 **앞**에 삽입:
```markdown
## FE 관통 인수 기준 (Acceptance Criteria)

- [ ] **AC-FE-01 광고 시청 완료 → 보상 반영**
  Given mock 플레이버 앱이 혜택존에 있고 일일 한도가 남아 있다
  When 리워드 광고 카드의 [광고 보기]를 눌러 (Fake) 광고를 완료한다
  Then 보상 반영 피드백(`에너지를 충전했어요!`)이 표시된다.

- [ ] **AC-FE-02 일일 한도 소진**
  Given `ad_quota_exceeded` 시나리오다
  When 혜택존에 진입한다
  Then 리워드 광고 카드가 시청 불가(`내일 다시 만나요`)로 표시된다.

```

- [ ] **Step 5: US-REWARD-003 에 FE 관통 AC 추가**

`docs/domains/reward/US-REWARD-003-tnk-offerwall.md` 의 `## 검증 매핑`(또는 마지막 AC) **뒤**에 삽입:
```markdown
## FE 관통 인수 기준 (Acceptance Criteria)

- [ ] **AC-FE-01 오퍼 완료 → 잔액 증가**
  Given mock 플레이버 앱이 혜택존에 있다
  When [TNK 오퍼월] 카드를 눌러 (Fake) 오퍼를 완료한다
  Then 코인 잔액이 증가해 상단 잔액 칩(`🪙 1500`)에 반영된다.

- [ ] **AC-FE-02 진입 실패**
  Given `offerwall_fail` 시나리오다
  When [TNK 오퍼월] 카드를 누른다
  Then 진입 실패 안내(`오퍼월 진입에 실패했어요`)가 표시된다.

```

- [ ] **Step 6: Commit**

```bash
git add docs/domains
git commit -m "docs: FE 관통 유저 스토리(US-CHAT-001) 및 광고/오퍼월 AC-FE 추가 (CC-391)"
```

---

### Task 11: 회고 + 계약 테스트 노트 + 최종 검증

**Files:**
- Modify: `docs/superpowers/specs/2026-07-07-maestro-fe-acceptance-spike-design.md` (§9 회고 작성)

**Interfaces:** 없음(문서/검증).

- [ ] **Step 1: 전체 검증 재실행**

Run:
```bash
cd apps/frontend
./gradlew :app:assembleRealDebug :app:assembleMockDebug -x lint
./gradlew :app:testMockDebugUnitTest
maestro test maestro/flows
```
Expected: 빌드 SUCCESSFUL, 유닛테스트 PASS, 6개 flow 전부 PASS.

- [ ] **Step 2: 회고 작성**

설계 문서 §9 회고의 세 항목을 실제 경험으로 채운다:
- **도입 비용**: seam 2개 추출·flavor·Fake 백엔드 유지보수 부담(실측 커밋 수/시간).
- **한계**: 인증(Retrofit) 우회를 세션 심기로 처리한 점, 에러 UI selector 확인 필요, testTag 부재로 텍스트 selector 의존.
- **확대 권고**: 다음 대상 여정, CI(에뮬레이터) 배선 여부, real 서버 스모크 병행 여부, `commonTest` MockEngine 계약 테스트와의 역할 분담.

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/specs/2026-07-07-maestro-fe-acceptance-spike-design.md
git commit -m "docs: Maestro 스파이크 회고 기록 (CC-391)"
```

---

## Self-Review

**Spec coverage** (설계 문서 §1~§9 대비):
- §2 범위(3여정×happy+fail) → Task 7·8·9. §5.1 flavor → Task 1. §5.2 seam → Task 2·3. §5.3 Fake 백엔드 → Task 4·6. §5.3.1 selector → Task 7~9(텍스트/contentDescription). §7 유저스토리 → Task 10. §8 완료기준 → Task 11 검증. §9 회고 → Task 11. ✅ 갭 없음.

**Placeholder scan:** 채팅 error flow의 에러 UI 문자열은 관측 후 강화하도록 "부정 단언 기본 + 관측 시 강화"로 **동작하는 기본값**을 제시(빈 TODO 아님). REWARDS 탭 라벨은 실제 라벨 확인 지시(기본 `"혜택존"`). ✅

**Type consistency:** `RewardedAdPresenter.show(...)` 시그니처는 Task 2 정의 = Task 5 Fake override = 기존 `RewardedAdManager.show` 동일. `OfferwallLauncher.launch(activity): Result<Unit>` Task 3 = Task 5 Fake. `MockBackendState` 필드(usedToday/remaining/pointsBalance/energy) Task 4 정의 = Task 5·6 사용 일치. `PointsRepository`(balance/refresh/applyDelta/reset)는 shared 실제 인터페이스. ✅

**주의(실행 중 확인 필요):** ① Koin override 예외 시 `allowOverride(true)`(Task 6 Step 4 명시). ② REWARDS 탭·채팅 selector는 최초 flow 실행 시 `maestro studio`로 실측 보정. ③ 에뮬레이터 필요(Maestro 단계).
