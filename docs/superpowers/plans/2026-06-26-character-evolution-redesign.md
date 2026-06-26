# Character Evolution Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Android와 iOS의 `캐릭터 진화` 화면을 미래형 분석 장치 UI로 개편하고, 백엔드 지원 여부를 자동 감지하는 길게 누르기 타이밍 보너스를 제공한다.

**Architecture:** shared 모듈이 진화 상태, 타이밍 세션 capability, 타이밍 시도 계약과 멱등성을 담당한다. 플랫폼 UI는 동일한 화면 상태를 사용해 캐릭터·경험치·확률·비용을 표시하고 press/drag/release 애니메이션과 햅틱만 플랫폼별로 구현한다. 타이밍 API가 404/405/네트워크 오류면 기존 기본 확률 시도로 자동 폴백한다.

**Tech Stack:** Kotlin Multiplatform, Ktor, kotlinx.serialization/Flow, Jetpack Compose Material3, SwiftUI, Koin, kotlin.test

## Global Constraints

- 사용자 노출 화면명은 `캐릭터 진화`이다.
- 0.6초 이전 해제는 취소하며 경험치를 소모하지 않는다.
- Normal은 +0%p, Great는 +5%p, Perfect는 +10%p이다.
- 타이밍 API 미지원 시 보너스 문구를 숨기고 기존 기본 확률 시도를 제공한다.
- 서버 응답의 등급·보너스·최종 확률을 클라이언트 예상값보다 우선한다.
- 성공 확률 상한은 서버 기준 100%이다.
- Android/iOS 모두 작은 화면과 큰 글꼴에서 본문 스크롤, CTA 하단 고정을 유지한다.
- Reduce Motion 활성 시 회전·파티클·플래시를 크로스페이드로 축소한다.

---

## File Structure

- Modify `shared/.../evolution/EvolutionApi.kt`: 타이밍 세션 및 시도 DTO/API
- Modify `shared/.../evolution/EvolutionStore.kt`: capability 감지, 멱등 시도
- Create `shared/.../evolution/EvolutionTiming.kt`: 등급·화면 상태에 공통으로 쓰는 순수 계산
- Create `shared/src/commonTest/.../evolution/EvolutionTimingTest.kt`: 경계값 테스트
- Create `shared/src/commonTest/.../evolution/EvolutionApiTest.kt`: capability/API 계약 테스트
- Modify Android `EvolutionViewModel.kt`: 로딩·오류·폴백·타이밍 상태머신
- Replace Android `EvolutionScreen.kt`: 캐릭터 진화 UI와 하단 press CTA
- Create Android `EvolutionTimingGauge.kt`, `EvolutionEffects.kt`: 집중된 시각 컴포넌트
- Modify iOS `EvolutionScreen.swift`: ViewModel 상태와 화면 조립
- Create iOS `EvolutionTimingGauge.swift`, `EvolutionEffects.swift`: 타이밍 입력과 효과

### Task 1: Shared Timing Domain

**Files:**
- Create: `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/evolution/EvolutionTiming.kt`
- Test: `apps/frontend/shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/evolution/EvolutionTimingTest.kt`

**Interfaces:**
- Produces: `TimingGrade`, `TimingWindow`, `localTimingGrade(position: Float, window: TimingWindow): TimingGrade`

- [ ] **Step 1: Write the failing boundary tests**

```kotlin
class EvolutionTimingTest {
    private val window = TimingWindow(
        minimumHoldMs = 600,
        cycleDurationMs = 1800,
        perfectStart = 0.45f,
        perfectEnd = 0.55f,
        greatStart = 0.38f,
        greatEnd = 0.62f,
    )

    @Test fun `center is perfect`() =
        assertEquals(TimingGrade.PERFECT, localTimingGrade(0.50f, window))

    @Test fun `great excludes perfect`() =
        assertEquals(TimingGrade.GREAT, localTimingGrade(0.40f, window))

    @Test fun `outside bonus windows is normal`() =
        assertEquals(TimingGrade.NORMAL, localTimingGrade(0.20f, window))
}
```

- [ ] **Step 2: Run the tests and verify failure**

Run: `cd apps/frontend && ./gradlew :shared:testDebugUnitTest --tests "*EvolutionTimingTest*"`

Expected: FAIL with unresolved `TimingWindow`.

- [ ] **Step 3: Implement the pure domain types**

```kotlin
enum class TimingGrade(val bonusRate: Double) {
    NORMAL(0.0), GREAT(0.05), PERFECT(0.10)
}

data class TimingWindow(
    val minimumHoldMs: Long,
    val cycleDurationMs: Long,
    val perfectStart: Float = 0.45f,
    val perfectEnd: Float = 0.55f,
    val greatStart: Float = 0.38f,
    val greatEnd: Float = 0.62f,
)

fun localTimingGrade(position: Float, window: TimingWindow): TimingGrade = when {
    position in window.perfectStart..window.perfectEnd -> TimingGrade.PERFECT
    position in window.greatStart..window.greatEnd -> TimingGrade.GREAT
    else -> TimingGrade.NORMAL
}
```

- [ ] **Step 4: Run the tests**

Run: `cd apps/frontend && ./gradlew :shared:testDebugUnitTest --tests "*EvolutionTimingTest*"`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/evolution/EvolutionTiming.kt apps/frontend/shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/evolution/EvolutionTimingTest.kt
git commit -m "feat(evolution): add timing grade domain"
```

### Task 2: Timing Capability and Attempt API

**Files:**
- Modify: `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/evolution/EvolutionApi.kt`
- Test: `apps/frontend/shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/evolution/EvolutionApiTest.kt`

**Interfaces:**
- Produces: `TimingSessionDto`, expanded `EvolutionAttemptDto`, `createTimingSession()`, `attempt(idempotencyKey, timing)`

- [ ] **Step 1: Add failing MockEngine tests**

```kotlin
@Test
fun `timing session response is decoded`() = runTest {
    val engine = MockEngine { request ->
        assertEquals("/api/evolution/timing-sessions", request.url.encodedPath)
        respond(
            """{"sessionId":"s1","serverStartedAt":"2026-06-26T00:00:00Z","minimumHoldMs":600,"cycleDurationMs":1800}""",
            HttpStatusCode.OK,
            jsonHeaders,
        )
    }
    val api = EvolutionApi(createCashChatHttpClient("https://api.test", NoAuth, engine), "https://api.test")
    assertEquals("s1", api.createTimingSession().sessionId)
}

@Test
fun `attempt decodes server timing result`() = runTest {
    val engine = MockEngine {
        respond(
            """{"success":true,"fromLevel":2,"resultLevel":3,"cost":1200,"timingGrade":"PERFECT","timingBonusRate":0.10,"baseSuccessRate":0.65,"finalSuccessRate":0.75}""",
            HttpStatusCode.OK,
            jsonHeaders,
        )
    }
    val api = EvolutionApi(createCashChatHttpClient("https://api.test", NoAuth, engine), "https://api.test")
    val result = api.attempt("key", TimingAttempt("s1", 1432))
    assertEquals(TimingGrade.PERFECT, result.timingGrade)
    assertEquals(0.75, result.finalSuccessRate)
}
```

- [ ] **Step 2: Verify tests fail**

Run: `cd apps/frontend && ./gradlew :shared:testDebugUnitTest --tests "*EvolutionApiTest*"`

Expected: FAIL because timing DTOs and methods do not exist.

- [ ] **Step 3: Add serializable contracts**

```kotlin
@Serializable
data class TimingSessionDto(
    val sessionId: String,
    val serverStartedAt: String,
    val minimumHoldMs: Long,
    val cycleDurationMs: Long,
)

@Serializable
data class TimingAttempt(val sessionId: String, val releasedAtMs: Long)

@Serializable
private data class EvolutionAttemptRequest(
    val idempotencyKey: String,
    val timing: TimingAttempt? = null,
)
```

Expand `EvolutionAttemptDto` using nullable defaults:

```kotlin
val timingGrade: TimingGrade? = null,
val timingBonusRate: Double? = null,
val baseSuccessRate: Double? = null,
val finalSuccessRate: Double? = null,
```

Add:

```kotlin
suspend fun createTimingSession(): TimingSessionDto =
    client.post("$baseUrl/api/evolution/timing-sessions").body()

suspend fun attempt(idempotencyKey: String, timing: TimingAttempt? = null): EvolutionAttemptDto =
    client.post("$baseUrl/api/evolution/attempt") {
        contentType(ContentType.Application.Json)
        setBody(EvolutionAttemptRequest(idempotencyKey, timing))
    }.body()
```

- [ ] **Step 4: Run API and compatibility tests**

Run: `cd apps/frontend && ./gradlew :shared:testDebugUnitTest --tests "*EvolutionApiTest*" --tests "*EvolutionStateDtoTest*"`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/evolution/EvolutionApi.kt apps/frontend/shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/evolution/EvolutionApiTest.kt
git commit -m "feat(evolution): add timing capability api"
```

### Task 3: Store Capability Fallback and Idempotency

**Files:**
- Modify: `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/evolution/EvolutionStore.kt`
- Test: `apps/frontend/shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/evolution/EvolutionStoreTest.kt`

**Interfaces:**
- Produces: `TimingCapability`, `detectTimingCapability()`, `attempt(timing: TimingAttempt?)`

- [ ] **Step 1: Introduce an API abstraction and failing store tests**

Define `EvolutionGateway` with the four API methods and make `EvolutionApi` implement it. Test:

```kotlin
@Test
fun `404 timing session falls back to unsupported`() = runTest {
    val gateway = FakeEvolutionGateway(
        timingError = ApiException("NOT_FOUND", "missing", 404),
    )
    val store = EvolutionStore(gateway)
    assertEquals(TimingCapability.UNSUPPORTED, store.detectTimingCapability())
}

@Test
fun `timing attempt sends session data`() = runTest {
    val gateway = FakeEvolutionGateway(session = session)
    val store = EvolutionStore(gateway)
    store.detectTimingCapability()
    store.attempt(TimingAttempt("s1", 1432))
    assertEquals("s1", gateway.lastTiming?.sessionId)
}
```

- [ ] **Step 2: Verify failure**

Run: `cd apps/frontend && ./gradlew :shared:testDebugUnitTest --tests "*EvolutionStoreTest*"`

Expected: FAIL because the gateway and capability do not exist.

- [ ] **Step 3: Implement fallback rules**

```kotlin
enum class TimingCapability { UNKNOWN, SUPPORTED, UNSUPPORTED }

suspend fun detectTimingCapability(): TimingCapability =
    runCatching { api.createTimingSession() }
        .fold(
            onSuccess = {
                timingSession = it
                TimingCapability.SUPPORTED
            },
            onFailure = {
                timingSession = null
                TimingCapability.UNSUPPORTED
            },
        ).also { _timingCapability.value = it }
```

Preserve one `currentAttemptKey` and pass timing only for a supported session. Never retry a failed timing request via the legacy body automatically.

- [ ] **Step 4: Run store tests**

Run: `cd apps/frontend && ./gradlew :shared:testDebugUnitTest --tests "*EvolutionStoreTest*"`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/evolution/EvolutionApi.kt apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/evolution/EvolutionStore.kt apps/frontend/shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/evolution/EvolutionStoreTest.kt
git commit -m "feat(evolution): detect timing server capability"
```

### Task 4: Android State Machine and UI

**Files:**
- Modify: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/chat/evolution/EvolutionViewModel.kt`
- Replace: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/chat/evolution/EvolutionScreen.kt`
- Create: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/chat/evolution/EvolutionTimingGauge.kt`
- Create: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/chat/evolution/EvolutionEffects.kt`

**Interfaces:**
- Consumes: shared timing API/store from Tasks 1–3
- Produces: Android loading/error/insufficient/ready/charging/resolving/result states

- [ ] **Step 1: Extract ViewModel UI state**

```kotlin
sealed interface EvolutionUiState {
    data object Loading : EvolutionUiState
    data class LoadError(val message: String) : EvolutionUiState
    data class Content(
        val evolution: EvolutionStateDto,
        val capability: TimingCapability,
        val phase: Phase = Phase.IDLE,
        val timingPosition: Float = 0f,
        val predictedGrade: TimingGrade? = null,
        val result: EvolutionAttemptDto? = null,
    ) : EvolutionUiState
}
```

Add `retryLoad()`, `beginHold()`, `updateTiming(position)`, `cancelHold()`, and `releaseHold(heldMs, position)`. When `heldMs < 600`, return to idle without calling the store.

- [ ] **Step 2: Build the screen structure**

Use a `Box` with a scrollable content column and bottom fixed action area. Render:

- loading skeleton
- inline load error and retry
- hero orb and next form
- `currentExp / nextAttemptCost` progress
- probability/cost cards
- unsupported capability: legacy `진화 시도` button
- supported capability: `HoldToEvolveButton` and timing gauge
- max-level completion state

- [ ] **Step 3: Implement press input**

Use `pointerInput` and `awaitEachGesture`:

```kotlin
awaitFirstDown()
viewModel.beginHold()
val released = waitForUpOrCancellation()
if (released == null) viewModel.cancelHold()
else viewModel.releaseHold(elapsedRealtime() - startedAt, markerPosition)
```

Cancel when the pointer leaves bounds. Disable duplicate input during resolving.

- [ ] **Step 4: Add effects and accessibility**

Use ring rotation, scan line, grade pulse, success/failure effects and haptics. Read `LocalView.current.context` animator duration scale or Compose accessibility settings and provide reduced motion fallback.

- [ ] **Step 5: Build Android**

Run: `cd apps/frontend && ./gradlew :shared:testDebugUnitTest :app:assembleDebug`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/chat/evolution
git commit -m "feat(android): redesign character evolution"
```

### Task 5: iOS State Machine and UI

**Files:**
- Modify: `apps/frontend/CashChatIOS/CashChatIOS/EvolutionScreen.swift`
- Create: `apps/frontend/CashChatIOS/CashChatIOS/EvolutionTimingGauge.swift`
- Create: `apps/frontend/CashChatIOS/CashChatIOS/EvolutionEffects.swift`

**Interfaces:**
- Consumes: shared timing API/store and DTOs
- Produces: SwiftUI parity with Android

- [ ] **Step 1: Model screen state**

```swift
enum EvolutionScreenState {
    case loading
    case loadError(String)
    case content
}

@Published var screenState: EvolutionScreenState = .loading
@Published var phase: EvolutionPhase = .idle
@Published var timingCapability: TimingCapability = .unknown
@Published var timingPosition: CGFloat = 0
@Published var predictedGrade: TimingGrade?
```

Load state and capability concurrently. Refresh the shared `HudStore` after every successful server result.

- [ ] **Step 2: Implement long-press tracking**

Use `DragGesture(minimumDistance: 0)` with `@GestureState`:

```swift
.onChanged { value in
    if holdStartedAt == nil { beginHold() }
    updateMarker(for: value.time)
}
.onEnded { value in
    releaseHold(at: value.time)
}
```

Cancel if the pointer leaves the button bounds or duration is below 0.6 seconds.

- [ ] **Step 3: Build the responsive screen**

Use `ScrollView` for hero and cards, `safeAreaInset(edge: .bottom)` for the CTA, and `@Environment(\.accessibilityReduceMotion)` for effect reduction. Keep title `캐릭터 진화`.

- [ ] **Step 4: Add grade/result feedback**

Normal uses neutral pulse, Great purple, Perfect gold. Success switches character form and shows level/cost/energy bonus; failure shows cost and actions without blaming timing.

- [ ] **Step 5: Build iOS**

Run:

```bash
cd apps/frontend
xcodebuild -project CashChatIOS/CashChatIOS.xcodeproj -scheme CashChatIOS -sdk iphonesimulator -configuration Debug -derivedDataPath /tmp/cashchat-derived CODE_SIGNING_ALLOWED=NO build
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 6: Commit**

```bash
git add apps/frontend/CashChatIOS/CashChatIOS/EvolutionScreen.swift apps/frontend/CashChatIOS/CashChatIOS/EvolutionTimingGauge.swift apps/frontend/CashChatIOS/CashChatIOS/EvolutionEffects.swift
git commit -m "feat(ios): redesign character evolution"
```

### Task 6: Cross-Platform Verification

**Files:**
- Modify only if verification reveals a defect.

- [ ] **Step 1: Run shared and Android verification**

Run: `cd apps/frontend && ./gradlew :shared:testDebugUnitTest :app:assembleDebug`

Expected: PASS and `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run iOS simulator build**

Run:

```bash
cd apps/frontend
xcodebuild -project CashChatIOS/CashChatIOS.xcodeproj -scheme CashChatIOS -sdk iphonesimulator -configuration Debug -derivedDataPath /tmp/cashchat-derived CODE_SIGNING_ALLOWED=NO build
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 3: Manual smoke matrix**

Verify on both platforms:

- timing API missing → no bonus UI, legacy attempt works
- loading/error/retry
- insufficient EXP copy
- 0.6s early release cancellation
- Normal/Great/Perfect local feedback
- server grade overrides predicted grade
- success/failure and HUD refresh
- max level
- large text and reduced motion

- [ ] **Step 4: Commit verification fixes**

```bash
git add apps/frontend
git commit -m "fix(evolution): address cross-platform ui verification"
```
