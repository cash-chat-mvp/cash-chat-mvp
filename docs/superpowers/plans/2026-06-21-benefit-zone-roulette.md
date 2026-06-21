# 행운 룰렛 (FE-first 스텁) 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 혜택존에 "행운 룰렛"을 FE-first 스텁으로 추가한다 — 휠 UI(미니멀 2톤)·회전 애니메이션·무료1/광고4 스핀·에너지 가중추첨을 로컬 스텁으로 동작시키고, BE 준비 시 Remote 교체만 하도록 격리한다. (Android + iOS)

**Architecture:** 공유 KMM에 `RouletteRepository` 인터페이스 + `FakeRouletteRepository`(로컬 가중 랜덤, 주입형 RNG) + `RouletteStore`(상태·스핀·광고크레딧 오케스트레이션). 광고 추가 스핀은 기존 `RewardedAdManager`를 재사용한다. 당첨/에너지 실지급은 BE 몫이라 스텁은 UI·애니메이션 검증용이다. `PointsRepository` Local→Remote 격리 패턴을 그대로 따른다.

**Tech Stack:** Kotlin Multiplatform, Koin, Jetpack Compose(Canvas/Animatable), SwiftUI(Path/rotationEffect), kotlin.test + kotlinx-coroutines-test. iOS 빌드는 에이전트가 `xcodebuild`로 직접 검증.

**Spec:** `docs/superpowers/specs/2026-06-21-benefit-zone-roulette-design.md`
**BE 계약:** `docs/planning/be-api-requests-cc355.md` §2

---

## File Structure

| 파일 | 역할 | 작업 |
|---|---|---|
| `shared/.../roulette/RouletteModels.kt` | `RoulettePrize`/`RouletteSegment`/`RouletteStatus`/`RouletteSpinResult` | Create |
| `shared/.../roulette/RouletteRepository.kt` | 인터페이스 | Create |
| `shared/.../roulette/FakeRouletteRepository.kt` | 로컬 스텁(주입형 RNG) | Create |
| `shared/.../roulette/RouletteStore.kt` | 상태 + spin/광고크레딧 오케스트레이션 | Create |
| `shared/commonTest/.../roulette/FakeRouletteRepositoryTest.kt` | 확률·상태전이 테스트 | Create |
| `shared/commonTest/.../roulette/RouletteStoreTest.kt` | 스토어 흐름 테스트 | Create |
| `shared/.../di/SharedModule.kt` | Koin 등록 | Modify |
| `shared/.../di/IosBridges.kt` | `rouletteStore()` + `collectRouletteStatus` | Modify |
| `app/.../feature/rewards/RouletteWheel.kt` | 휠 Composable(그리기+회전) | Create |
| `app/.../feature/rewards/RouletteDialog.kt` | 룰렛 화면(다이얼로그) + `RouletteViewModel` | Create |
| `app/.../di/AppModule.kt` | `RouletteViewModel` 등록 | Modify |
| `app/.../feature/rewards/BenefitZoneScreen.kt` | 룰렛 진입 카드 추가 | Modify |
| `CashChatIOS/.../BenefitZone/RouletteView.swift` | iOS 휠 + VM | Create |
| `CashChatIOS/.../BenefitZoneScreen.swift` | 룰렛 진입 카드 추가 | Modify |

> 경로 접두사 `apps/frontend/`. 패키지 `com.nomadclub.cashchat.shared.roulette`. iOS 빌드: shared 프레임워크 선빌드 후 `xcodebuild -scheme CashChatIOS -destination 'platform=iOS Simulator,name=iPhone 16'`.

---

## Task 1: 공유 모델 + Repository 인터페이스 + Fake 스텁

**Files:**
- Create: `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/roulette/RouletteModels.kt`
- Create: `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/roulette/RouletteRepository.kt`
- Create: `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/roulette/FakeRouletteRepository.kt`
- Test: `apps/frontend/shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/roulette/FakeRouletteRepositoryTest.kt`

- [ ] **Step 1: 모델 + 인터페이스 작성**

`RouletteModels.kt`:
```kotlin
package com.nomadclub.cashchat.shared.roulette

/** 룰렛 상품(전부 에너지). energy 는 지급 에너지량. */
enum class RoulettePrize(val energy: Int) { JACKPOT_100(100), E10(10), E3(3), MISS(0) }

/** 휠 표시용 칸(고정 배치, 확률과 무관). */
data class RouletteSegment(val index: Int, val prize: RoulettePrize)

/** 룰렛 상태(서버가 진실, 스텁이 모사). */
data class RouletteStatus(
    val dailyLimit: Int,
    val spinsUsedToday: Int,
    val freeSpinAvailable: Boolean,
    val availableSpins: Int,
    val adSpinsRemaining: Int,
    val resetAtKst: String,
    val segments: List<RouletteSegment>,
)

/** 1회 스핀 결과 — UI 는 segmentIndex 칸으로 휠을 멈춘다. */
data class RouletteSpinResult(val prize: RoulettePrize, val segmentIndex: Int, val awardedEnergy: Int)
```

`RouletteRepository.kt`:
```kotlin
package com.nomadclub.cashchat.shared.roulette

/**
 * 룰렛 데이터 소스. 지금은 FakeRouletteRepository(로컬 스텁), BE 준비 시 RemoteRouletteRepository 로 교체.
 * iOS 에서 호출하므로 suspend 함수는 모두 @Throws.
 */
interface RouletteRepository {
    @Throws(Exception::class) suspend fun getStatus(): RouletteStatus
    @Throws(Exception::class) suspend fun spin(): RouletteSpinResult
    @Throws(Exception::class) suspend fun requestAdSpinNonce(): String
    /** 광고 시청 후 스핀 크레딧 적립을 폴링 판정. baseline 대비 availableSpins 증가 시 true. */
    @Throws(Exception::class) suspend fun awaitSpinCredited(baselineAvailable: Int): Boolean
}
```

- [ ] **Step 2: 실패 테스트 작성**

`FakeRouletteRepositoryTest.kt`:
```kotlin
package com.nomadclub.cashchat.shared.roulette

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FakeRouletteRepositoryTest {

    @Test
    fun `확률 경계 - 0_005 면 잭팟`() = runTest {
        val repo = FakeRouletteRepository(random = { 0.005 })
        assertEquals(RoulettePrize.JACKPOT_100, repo.spin().prize)
    }

    @Test
    fun `확률 경계 - 0_05 면 E10`() = runTest {
        val repo = FakeRouletteRepository(random = { 0.05 })
        assertEquals(RoulettePrize.E10, repo.spin().prize)
    }

    @Test
    fun `확률 경계 - 0_5 면 E3`() = runTest {
        val repo = FakeRouletteRepository(random = { 0.5 })
        assertEquals(RoulettePrize.E3, repo.spin().prize)
    }

    @Test
    fun `확률 경계 - 0_9 면 꽝`() = runTest {
        val repo = FakeRouletteRepository(random = { 0.9 })
        assertEquals(RoulettePrize.MISS, repo.spin().prize)
    }

    @Test
    fun `spin 은 결과 prize 와 일치하는 segmentIndex 를 돌려준다`() = runTest {
        val repo = FakeRouletteRepository(random = { 0.005 })
        val result = repo.spin()
        val status = repo.getStatus()
        assertEquals(result.prize, status.segments[result.segmentIndex].prize)
    }

    @Test
    fun `spin 은 무료 1회를 소모하고 availableSpins 를 줄인다`() = runTest {
        val repo = FakeRouletteRepository(random = { 0.5 })
        val before = repo.getStatus()
        assertEquals(true, before.freeSpinAvailable)
        assertEquals(1, before.availableSpins)
        repo.spin()
        val after = repo.getStatus()
        assertEquals(false, after.freeSpinAvailable)
        assertEquals(0, after.availableSpins)
        assertEquals(1, after.spinsUsedToday)
    }

    @Test
    fun `awaitSpinCredited 는 availableSpins 를 1 늘리고 adSpinsRemaining 을 줄인다`() = runTest {
        val repo = FakeRouletteRepository(random = { 0.5 })
        repo.spin() // 무료 소모 → availableSpins 0, adSpinsRemaining 4
        val credited = repo.awaitSpinCredited(baselineAvailable = 0)
        assertTrue(credited)
        val after = repo.getStatus()
        assertEquals(1, after.availableSpins)
        assertEquals(3, after.adSpinsRemaining)
    }
}
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `cd apps/frontend && ./gradlew :shared:testDebugUnitTest --tests "*.FakeRouletteRepositoryTest"`
Expected: FAIL(컴파일 에러, `FakeRouletteRepository` 미정의).

- [ ] **Step 4: `FakeRouletteRepository` 구현**

`FakeRouletteRepository.kt`:
```kotlin
package com.nomadclub.cashchat.shared.roulette

import kotlin.random.Random

/**
 * 로컬 스텁. 서버 가중 확률(잭팟 1% / E10 10% / E3 70% / 꽝 19%)을 모사한다.
 * @param random 0.0(포함)~1.0(미만) 난수 공급자. 테스트는 고정값 주입.
 * 에너지 실지급은 없음(BE 몫) — UI/애니메이션 검증용.
 */
class FakeRouletteRepository(
    private val random: () -> Double = { Random.nextDouble() },
) : RouletteRepository {

    // 표시용 8칸 고정 배치(스펙/BE 문서와 동일): 잭팟1·E10 2·E3 3·꽝 2.
    private val segments = listOf(
        RouletteSegment(0, RoulettePrize.JACKPOT_100),
        RouletteSegment(1, RoulettePrize.E3),
        RouletteSegment(2, RoulettePrize.MISS),
        RouletteSegment(3, RoulettePrize.E10),
        RouletteSegment(4, RoulettePrize.E3),
        RouletteSegment(5, RoulettePrize.MISS),
        RouletteSegment(6, RoulettePrize.E10),
        RouletteSegment(7, RoulettePrize.E3),
    )

    private var status = RouletteStatus(
        dailyLimit = 5,
        spinsUsedToday = 0,
        freeSpinAvailable = true,
        availableSpins = 1,
        adSpinsRemaining = 4,
        resetAtKst = "2026-06-22T00:00:00+09:00",
        segments = segments,
    )

    override suspend fun getStatus(): RouletteStatus = status

    override suspend fun spin(): RouletteSpinResult {
        val r = random()
        val prize = when {
            r < 0.01 -> RoulettePrize.JACKPOT_100
            r < 0.11 -> RoulettePrize.E10
            r < 0.81 -> RoulettePrize.E3
            else -> RoulettePrize.MISS
        }
        val segment = segments.first { it.prize == prize }
        status = status.copy(
            spinsUsedToday = status.spinsUsedToday + 1,
            freeSpinAvailable = false,
            availableSpins = (status.availableSpins - 1).coerceAtLeast(0),
        )
        return RouletteSpinResult(prize, segment.index, prize.energy)
    }

    override suspend fun requestAdSpinNonce(): String = "fake-nonce"

    override suspend fun awaitSpinCredited(baselineAvailable: Int): Boolean {
        if (status.adSpinsRemaining <= 0) return false
        status = status.copy(
            availableSpins = status.availableSpins + 1,
            adSpinsRemaining = status.adSpinsRemaining - 1,
        )
        return true
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `cd apps/frontend && ./gradlew :shared:testDebugUnitTest --tests "*.FakeRouletteRepositoryTest"`
Expected: PASS (7 테스트).

- [ ] **Step 6: 커밋**

```bash
git add apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/roulette/ apps/frontend/shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/roulette/
git commit -m "feat(roulette): 룰렛 모델·Repository·Fake 스텁 추가 (shared)"
```

---

## Task 2: `RouletteStore` (상태 + 스핀/광고크레딧 오케스트레이션)

**Files:**
- Create: `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/roulette/RouletteStore.kt`
- Test: `apps/frontend/shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/roulette/RouletteStoreTest.kt`

- [ ] **Step 1: 실패 테스트 작성**

`RouletteStoreTest.kt`:
```kotlin
package com.nomadclub.cashchat.shared.roulette

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class RouletteStoreTest {

    @Test
    fun `refresh 는 status 를 채운다`() = runTest {
        val store = RouletteStore(FakeRouletteRepository(random = { 0.5 }), onEnergyChanged = {})
        assertEquals(null, store.status.value)
        store.refresh()
        assertEquals(5, store.status.value?.dailyLimit)
    }

    @Test
    fun `spin 은 결과를 반환하고 에너지 변경 콜백을 호출한다`() = runTest {
        var energyRefreshed = 0
        val store = RouletteStore(FakeRouletteRepository(random = { 0.005 }), onEnergyChanged = { energyRefreshed++ })
        val result = store.spin()
        assertEquals(RoulettePrize.JACKPOT_100, result.prize)
        assertEquals(1, energyRefreshed)
        assertEquals(0, store.status.value?.availableSpins)
    }

    @Test
    fun `watchAdForSpin - 미시청이면 false, 크레딧 없음`() = runTest {
        val store = RouletteStore(FakeRouletteRepository(random = { 0.5 }), onEnergyChanged = {})
        store.spin() // availableSpins 0
        val credited = store.watchAdForSpin(showAd = { false })
        assertFalse(credited)
        assertEquals(0, store.status.value?.availableSpins)
    }

    @Test
    fun `watchAdForSpin - 시청하면 크레딧 적립되어 true`() = runTest {
        val store = RouletteStore(FakeRouletteRepository(random = { 0.5 }), onEnergyChanged = {})
        store.spin() // availableSpins 0, adSpinsRemaining 4
        val credited = store.watchAdForSpin(showAd = { true })
        assertTrue(credited)
        assertEquals(1, store.status.value?.availableSpins)
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd apps/frontend && ./gradlew :shared:testDebugUnitTest --tests "*.RouletteStoreTest"`
Expected: FAIL(`RouletteStore` 미정의).

- [ ] **Step 3: `RouletteStore` 구현**

`RouletteStore.kt`:
```kotlin
package com.nomadclub.cashchat.shared.roulette

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 룰렛 상태 보유 + 스핀/광고크레딧 오케스트레이션. 채팅·리워드 경로와 무관하게 독립 동작.
 * @param onEnergyChanged 스핀 후 에너지가 바뀌었을 수 있어 HUD 등을 갱신하는 콜백(DI 에서 HudStore.refreshEnergyOnly 주입).
 * iOS 에서 호출하므로 suspend 는 @Throws.
 */
class RouletteStore(
    private val repo: RouletteRepository,
    private val onEnergyChanged: suspend () -> Unit,
) {
    private val _status = MutableStateFlow<RouletteStatus?>(null)
    val status: StateFlow<RouletteStatus?> = _status.asStateFlow()

    @Throws(Exception::class)
    suspend fun refresh(): RouletteStatus = repo.getStatus().also { _status.value = it }

    /** 보유 스핀 1개 소모 → 결과 반환. 서버(스텁)가 에너지 지급하므로 onEnergyChanged 로 HUD 동기화. */
    @Throws(Exception::class)
    suspend fun spin(): RouletteSpinResult {
        val result = repo.spin()
        onEnergyChanged()
        _status.value = repo.getStatus()
        return result
    }

    /** 광고 시청 → 스핀 크레딧 적립. showAd 는 nonce 로 광고를 띄우고 끝까지 봤으면 true. */
    @Throws(Exception::class)
    suspend fun watchAdForSpin(showAd: suspend (nonce: String) -> Boolean): Boolean {
        val baseline = repo.getStatus().availableSpins
        val nonce = repo.requestAdSpinNonce()
        val watched = showAd(nonce)
        if (!watched) return false
        val credited = repo.awaitSpinCredited(baseline)
        _status.value = repo.getStatus()
        return credited
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd apps/frontend && ./gradlew :shared:testDebugUnitTest --tests "*.RouletteStoreTest"`
Expected: PASS (4 테스트).

- [ ] **Step 5: 커밋**

```bash
git add apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/roulette/RouletteStore.kt apps/frontend/shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/roulette/RouletteStoreTest.kt
git commit -m "feat(roulette): RouletteStore 스핀·광고크레딧 오케스트레이션 추가 (shared)"
```

---

## Task 3: 공유 DI 등록 (SharedModule + IosBridges)

**Files:**
- Modify: `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/di/SharedModule.kt`
- Modify: `apps/frontend/shared/src/iosMain/kotlin/com/nomadclub/cashchat/shared/di/IosBridges.kt`

- [ ] **Step 1: SharedModule 에 등록**

`SharedModule.kt`의 `single { HudStore(...) }` 줄 **아래**에 추가(HudStore 가 먼저 등록돼 있어야 함):
```kotlin
    single<com.nomadclub.cashchat.shared.roulette.RouletteRepository> {
        com.nomadclub.cashchat.shared.roulette.FakeRouletteRepository()
    }
    single {
        val hud = get<HudStore>()
        com.nomadclub.cashchat.shared.roulette.RouletteStore(
            repo = get(),
            onEnergyChanged = { hud.refreshEnergyOnly() },
        )
    }
```

- [ ] **Step 2: IosBridges 에 노출 + Flow 브리지**

`IosBridges.kt`의 `KoinHelper` 클래스에서:
- import 추가(파일 상단 import 블록):
```kotlin
import com.nomadclub.cashchat.shared.roulette.RouletteStore
import com.nomadclub.cashchat.shared.roulette.RouletteStatus
```
- `private val adReward: AdRewardStore by inject()` 줄 아래에:
```kotlin
    private val roulette: RouletteStore by inject()
```
- `fun adRewardStore(): AdRewardStore = adReward` 줄 아래에:
```kotlin
    fun rouletteStore(): RouletteStore = roulette
```

그리고 `FlowCollector` 클래스의 `collectQuota(...)` 함수 아래에 추가:
```kotlin
    fun collectRouletteStatus(store: RouletteStore, onEach: (RouletteStatus?) -> Unit) {
        scope.launch { store.status.collect { onEach(it) } }
    }
```

- [ ] **Step 3: 빌드 확인 (Android + shared 메타데이터)**

Run: `cd apps/frontend && ./gradlew :shared:compileKotlinMetadata :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: iOS shared 프레임워크 재빌드 (헤더에 RouletteStore 노출)**

Run: `cd apps/frontend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && ./gradlew :shared:embedAndSignAppleFrameworkForXcode`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: 커밋**

```bash
git add apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/di/SharedModule.kt apps/frontend/shared/src/iosMain/kotlin/com/nomadclub/cashchat/shared/di/IosBridges.kt
git commit -m "feat(roulette): RouletteStore Koin 등록·iOS 브릿지 노출"
```

---

## Task 4: Android 룰렛 휠 + 다이얼로그 + 진입 카드

**Files:**
- Create: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/rewards/RouletteWheel.kt`
- Create: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/rewards/RouletteDialog.kt`
- Modify: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/di/AppModule.kt`
- Modify: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/rewards/BenefitZoneScreen.kt`

- [ ] **Step 1: `RouletteWheel.kt` 작성 (휠 그리기 + 회전 애니메이션)**

```kotlin
package com.nomadclub.cashchat.feature.rewards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nomadclub.cashchat.shared.roulette.RoulettePrize
import com.nomadclub.cashchat.shared.roulette.RouletteSegment
import kotlin.math.cos
import kotlin.math.sin

private val CREAM = Color(0xFFFFF6DF)
private val WHITEISH = Color(0xFFFFFFFF)
private val DIVIDER = Color(0xFFECEAF5)
private val GOLD = Color(0xFFFFB02E)

private fun labelFor(prize: RoulettePrize): String = when (prize) {
    RoulettePrize.JACKPOT_100 -> "⚡100"
    RoulettePrize.E10 -> "⚡10"
    RoulettePrize.E3 -> "⚡3"
    RoulettePrize.MISS -> "꽝"
}

private fun labelColor(prize: RoulettePrize): Int = when (prize) {
    RoulettePrize.JACKPOT_100 -> 0xFFB07C00.toInt()
    RoulettePrize.MISS -> 0xFF9A95AD.toInt()
    else -> 0xFF1B1B2A.toInt()
}

/**
 * 8칸 룰렛 휠. rotationDeg 만큼 회전해 그린다(상위에서 Animatable 로 제어).
 * 칸 0 의 중심이 회전 0 일 때 12시(상단 포인터)에 오도록 그린다 → 당첨 칸 정지 각도 계산이 단순해진다.
 */
@Composable
fun RouletteWheel(
    segments: List<RouletteSegment>,
    rotationDeg: Float,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(260.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(260.dp)) {
            val n = segments.size
            val sweep = 360f / n
            val d = size.minDimension
            val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f)
            val arcSize = Size(d, d)
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = d / 2f

            rotate(rotationDeg, pivot = Offset(cx, cy)) {
                segments.forEachIndexed { i, seg ->
                    // 칸 i 의 중심이 -90°(상단)에 오도록: startAngle = -90 - sweep/2 + i*sweep
                    val start = -90f - sweep / 2f + i * sweep
                    drawArc(
                        color = if (seg.prize == RoulettePrize.JACKPOT_100) CREAM
                                else if (i % 2 == 0) CREAM else WHITEISH,
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = true,
                        topLeft = topLeft,
                        size = arcSize,
                    )
                    // 칸 구분선
                    val a = Math.toRadians((start).toDouble())
                    drawLine(
                        color = DIVIDER,
                        start = Offset(cx, cy),
                        end = Offset(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat()),
                        strokeWidth = 2f,
                    )
                    // 라벨(칸 중심 각도, 반지름 0.62r)
                    val mid = Math.toRadians((start + sweep / 2f).toDouble())
                    val lx = cx + (r * 0.62f) * cos(mid).toFloat()
                    val ly = cy + (r * 0.62f) * sin(mid).toFloat()
                    drawContext.canvas.nativeCanvas.apply {
                        val paint = android.graphics.Paint().apply {
                            color = labelColor(seg.prize)
                            textSize = 34f
                            isFakeBoldText = true
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                        }
                        save()
                        rotate((start + sweep / 2f) + 90f, lx, ly) // 라벨을 칸 방향으로 세움
                        drawText(labelFor(seg.prize), lx, ly + 12f, paint)
                        restore()
                    }
                }
                // 잭팟 칸 금색 테두리 (칸 0)
                val jStart = -90f - sweep / 2f
                drawArc(
                    color = GOLD,
                    startAngle = jStart,
                    sweepAngle = sweep,
                    useCenter = true,
                    topLeft = topLeft,
                    size = arcSize,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f),
                )
            }
        }
    }
}
```

- [ ] **Step 2: `RouletteDialog.kt` 작성 (VM + 다이얼로그 UI + 회전 제어)**

```kotlin
package com.nomadclub.cashchat.feature.rewards

import android.app.Activity
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nomadclub.cashchat.ads.RewardedAdManager
import com.nomadclub.cashchat.shared.roulette.RouletteSpinResult
import com.nomadclub.cashchat.shared.roulette.RouletteStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import kotlin.coroutines.resume

class RouletteViewModel(val store: RouletteStore) : ViewModel() {
    enum class Phase { IDLE, SPINNING, AD }
    private val _phase = MutableStateFlow(Phase.IDLE)
    val phase: StateFlow<Phase> = _phase.asStateFlow()
    private val _result = MutableSharedFlow<RouletteSpinResult>(extraBufferCapacity = 1)
    val result: SharedFlow<RouletteSpinResult> = _result.asSharedFlow()
    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toast: SharedFlow<String> = _toast.asSharedFlow()
    val status = store.status

    fun load() { viewModelScope.launch { runCatching { store.refresh() } } }

    fun spin() {
        if (_phase.value != Phase.IDLE) return
        val s = store.status.value ?: return
        if (s.availableSpins <= 0) { _toast.tryEmit("스핀이 없어요. 광고를 보고 채워보세요"); return }
        viewModelScope.launch {
            _phase.value = Phase.SPINNING
            val result = runCatching { store.spin() }.getOrNull()
            if (result != null) _result.tryEmit(result)
            else _toast.tryEmit("스핀에 실패했어요")
            _phase.value = Phase.IDLE
        }
    }

    fun watchAdForSpin(showAd: suspend (nonce: String) -> Boolean) {
        if (_phase.value != Phase.IDLE) return
        viewModelScope.launch {
            _phase.value = Phase.AD
            val credited = runCatching { store.watchAdForSpin(showAd) }.getOrDefault(false)
            if (credited) _toast.tryEmit("스핀 1회가 충전됐어요!")
            _phase.value = Phase.IDLE
        }
    }
}

private fun resultText(r: RouletteSpinResult): String =
    if (r.awardedEnergy > 0) "⚡${r.awardedEnergy} 에너지 획득!" else "아쉽지만 꽝! 다시 도전해요"

@Composable
fun RouletteDialog(
    onDismiss: () -> Unit,
    vm: RouletteViewModel = koinViewModel(),
    adManager: RewardedAdManager = koinInject(),
) {
    val status by vm.status.collectAsState()
    val phase by vm.phase.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rotation = remember { Animatable(0f) }
    var lastResultText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { adManager.preload(context); vm.load() }
    LaunchedEffect(Unit) { vm.toast.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } }
    LaunchedEffect(Unit) {
        vm.result.collect { result ->
            // 칸 segmentIndex 가 상단 포인터에 오도록: 칸 i 중심은 (i*45)°(시계방향, 0이 상단).
            // 회전을 음의 방향(시계방향 누적)으로 여러 바퀴 + 목표 각도.
            val sweep = 360f / (status?.segments?.size ?: 8)
            val target = 360f * 5 - result.segmentIndex * sweep
            val base = rotation.value
            rotation.snapTo(base % 360f)
            rotation.animateTo(rotation.value + (target - rotation.value % 360f), tween(2600))
            lastResultText = resultText(result)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.92f).clip(RoundedCornerShape(24.dp)).background(Color.White).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("행운 룰렛", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1B1B2A))
            status?.let {
                Text("오늘 ${it.availableSpins}회 가능 · 광고로 +${it.adSpinsRemaining}",
                    fontSize = 12.sp, color = Color(0xFF6B6979))
            }

            Box(contentAlignment = Alignment.TopCenter) {
                status?.let { RouletteWheel(segments = it.segments, rotationDeg = rotation.value) }
                // 상단 포인터(인디고 삼각형) — Canvas 위 오버레이
                Box(
                    Modifier.size(0.dp).offset(y = 2.dp)
                        .background(Color.Transparent),
                )
                Text("▼", color = Color(0xFF5B5BD6), fontSize = 22.sp, fontWeight = FontWeight.Black)
                // 가운데 GO 버튼
                Box(
                    Modifier.padding(top = 118.dp).size(56.dp).clip(CircleShape).background(Color(0xFF5B5BD6)),
                    contentAlignment = Alignment.Center,
                ) { Text("GO", color = Color.White, fontWeight = FontWeight.Black) }
            }

            lastResultText?.let { Text(it, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5B5BD6)) }

            val canSpin = (status?.availableSpins ?: 0) > 0 && phase == RouletteViewModel.Phase.IDLE
            if (canSpin || (status?.adSpinsRemaining ?: 0) <= 0) {
                Button(
                    onClick = { vm.spin() },
                    enabled = canSpin,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if ((status?.availableSpins ?: 0) > 0) "돌리기 · 오늘 ${status?.availableSpins}회" else "내일 다시 · 자정 리셋") }
            } else {
                Button(
                    onClick = {
                        val activity = context as? Activity ?: return@Button
                        vm.watchAdForSpin { nonce ->
                            suspendCancellableCoroutine { cont ->
                                var rewarded = false
                                adManager.show(
                                    activity = activity,
                                    nonce = nonce,
                                    onRewarded = { rewarded = true },
                                    onDismissed = { if (cont.isActive) cont.resume(rewarded) },
                                    onNotReady = {
                                        if (cont.isActive) {
                                            Toast.makeText(context, "광고를 준비 중이에요. 잠시 후 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
                                            cont.resume(false)
                                        }
                                    },
                                )
                            }
                        }
                    },
                    enabled = phase == RouletteViewModel.Phase.IDLE,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("광고 보고 한 번 더") }
            }

            Text("닫기", color = Color(0xFF9A95AD), modifier = Modifier
                .clip(RoundedCornerShape(8.dp)).padding(8.dp), fontSize = 13.sp)
        }
    }
}
```

> 회전 각도 상수: 칸 중심이 상단(포인터)에 오도록 `target = 360*5 - segmentIndex*sweep`. 칸 0(잭팟)이 회전 0에서 상단이라는 그리기 규약(Step 1)과 일치한다.

- [ ] **Step 3: AppModule 에 ViewModel 등록**

`AppModule.kt`의 `viewModel { ... }` 블록에 추가:
```kotlin
    viewModel { com.nomadclub.cashchat.feature.rewards.RouletteViewModel(get()) }
```

- [ ] **Step 4: 혜택존에 룰렛 진입 카드 추가**

`BenefitZoneScreen.kt`에서, 리워드 광고 카드(`item { RewardAdCard() }`) **아래**에 룰렛 진입 카드를 추가한다. 파일 상단의 `BenefitZoneScreen` Composable 본문 시작부에 다이얼로그 상태를 추가:
```kotlin
    var showRoulette by remember { mutableStateOf(false) }
```
그리고 `item { RewardAdCard() }` 다음 줄에:
```kotlin
            item {
                BenefitInfoCard(
                    icon = "🎡", title = "행운 룰렛", badge = BenefitBadge.NEXT,
                    description = "하루 1회 무료 · 광고로 최대 5회 · 에너지 잭팟까지!",
                    dimmed = false,
                    onClick = { showRoulette = true },
                )
            }
```
그리고 `LazyColumn { ... }` **바깥**(PullToRefreshBox 안, LazyColumn 뒤)에:
```kotlin
        if (showRoulette) {
            RouletteDialog(onDismiss = { showRoulette = false })
        }
```

- [ ] **Step 5: 빌드 확인**

Run: `cd apps/frontend && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. (jlink 실패 시 JBR/JDK21 로 JAVA_HOME 지정 후 재시도.)

- [ ] **Step 6: 커밋**

```bash
git add apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/rewards/RouletteWheel.kt apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/rewards/RouletteDialog.kt apps/frontend/app/src/main/java/com/nomadclub/cashchat/di/AppModule.kt apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/rewards/BenefitZoneScreen.kt
git commit -m "feat(roulette): android 룰렛 휠·다이얼로그·혜택존 진입 카드 추가"
```

---

## Task 5: iOS 룰렛 뷰 + 진입 카드

**Files:**
- Create: `apps/frontend/CashChatIOS/CashChatIOS/BenefitZone/RouletteView.swift`
- Modify: `apps/frontend/CashChatIOS/CashChatIOS/BenefitZoneScreen.swift`

- [ ] **Step 1: `RouletteView.swift` 작성**

```swift
import SwiftUI
import Combine
import CashChatShared

@MainActor
final class RouletteViewModel: ObservableObject {
    @Published var status: RouletteStatus? = nil
    @Published var busy = false
    @Published var rotation: Double = 0
    @Published var resultText: String? = nil
    @Published var toast: String? = nil

    private let store = KoinHelper().rouletteStore()
    private let adManager = RewardedAdManager()
    private let collector = FlowCollector()

    func onAppear() {
        adManager.preload()
        collector.collectRouletteStatus(store: store) { [weak self] s in self?.status = s }
        Task { try? await store.refresh() }
    }
    func onDisappear() { collector.cancel() }

    func spin() {
        guard !busy, let s = status, s.availableSpins > 0 else {
            if (status?.availableSpins ?? 0) == 0 { toast = "스핀이 없어요. 광고를 보고 채워보세요" }
            return
        }
        busy = true
        Task { @MainActor in
            defer { busy = false }
            guard let result = try? await store.spin() else { toast = "스핀에 실패했어요"; return }
            let sweep = 360.0 / Double(s.segments.count)
            // 칸 segmentIndex 중심이 상단 포인터에 오도록 시계방향 5바퀴 + 목표
            let target = 360.0 * 5 - Double(result.segmentIndex) * sweep
            withAnimation(.easeOut(duration: 2.6)) {
                rotation = rotation - rotation.truncatingRemainder(dividingBy: 360) + target
            }
            resultText = result.awardedEnergy > 0 ? "⚡\(result.awardedEnergy) 에너지 획득!" : "아쉽지만 꽝! 다시 도전해요"
        }
    }

    func watchAdForSpin() {
        guard !busy else { return }
        busy = true
        Task { @MainActor in
            defer { busy = false }
            let credited = (try? await store.watchAdForSpin(showAd: { [adManager] nonce in
                await withCheckedContinuation { cont in
                    var rewarded = false
                    adManager.show(nonce: nonce,
                        onRewarded: { _ in rewarded = true },
                        onDismissed: { cont.resume(returning: rewarded) },
                        onNotReady: { cont.resume(returning: false) })
                }
            })) ?? false
            if credited.boolValue { toast = "스핀 1회가 충전됐어요!" }
        }
    }
}

struct RouletteView: View {
    @StateObject private var vm = RouletteViewModel()
    var onClose: () -> Void = {}
    private let indigo = Color(red: 0.36, green: 0.36, blue: 0.84)

    var body: some View {
        VStack(spacing: 14) {
            Text("행운 룰렛").font(.system(size: 20, weight: .heavy))
            if let s = vm.status {
                Text("오늘 \(s.availableSpins)회 가능 · 광고로 +\(s.adSpinsRemaining)")
                    .font(.system(size: 12)).foregroundStyle(.secondary)
            }
            ZStack(alignment: .top) {
                RouletteWheelShape(segments: vm.status?.segments ?? [])
                    .frame(width: 260, height: 260)
                    .rotationEffect(.degrees(vm.rotation))
                Text("▼").font(.system(size: 22, weight: .black)).foregroundStyle(indigo).offset(y: -4)
                Circle().fill(indigo).frame(width: 56, height: 56)
                    .overlay(Text("GO").font(.system(size: 15, weight: .black)).foregroundStyle(.white))
                    .offset(y: 102)
            }
            if let r = vm.resultText { Text(r).font(.system(size: 15, weight: .bold)).foregroundStyle(indigo) }

            let canSpin = (vm.status?.availableSpins ?? 0) > 0
            if canSpin || (vm.status?.adSpinsRemaining ?? 0) == 0 {
                Button(action: { vm.spin() }) {
                    Text(canSpin ? "돌리기 · 오늘 \(vm.status?.availableSpins ?? 0)회" : "내일 다시 · 자정 리셋")
                        .frame(maxWidth: .infinity)
                }.buttonStyle(.borderedProminent).disabled(!canSpin || vm.busy)
            } else {
                Button(action: { vm.watchAdForSpin() }) {
                    Text("광고 보고 한 번 더").frame(maxWidth: .infinity)
                }.buttonStyle(.borderedProminent).disabled(vm.busy)
            }
            Button("닫기") { onClose() }.foregroundStyle(.secondary).font(.system(size: 13))
        }
        .padding(20)
        .onAppear { vm.onAppear() }
        .onDisappear { vm.onDisappear() }
    }
}

/// 8칸 미니멀 2톤 휠. 칸 0 중심이 회전 0 에서 상단(12시)에 온다.
struct RouletteWheelShape: View {
    let segments: [RouletteSegment]
    private func label(_ p: RoulettePrize) -> String {
        switch p { case .jackpot100: return "⚡100"; case .e10: return "⚡10"; case .e3: return "⚡3"; default: return "꽝" }
    }
    var body: some View {
        GeometryReader { geo in
            let n = max(segments.count, 1)
            let sweep = 360.0 / Double(n)
            let r = min(geo.size.width, geo.size.height) / 2
            ZStack {
                ForEach(Array(segments.enumerated()), id: \.offset) { i, seg in
                    let start = -90.0 - sweep/2 + Double(i) * sweep
                    WedgeShape(startDeg: start, sweepDeg: sweep)
                        .fill(seg.prize == .jackpot100 ? Color(red:1,green:0.965,blue:0.874)
                              : (i % 2 == 0 ? Color(red:1,green:0.965,blue:0.874) : .white))
                        .overlay(WedgeShape(startDeg: start, sweepDeg: sweep).stroke(Color(red:0.925,green:0.918,blue:0.96), lineWidth: 1.5))
                    let mid = (start + sweep/2) * .pi / 180
                    Text(label(seg.prize))
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(seg.prize == .miss ? Color(red:0.60,green:0.58,blue:0.68) : (seg.prize == .jackpot100 ? Color(red:0.69,green:0.49,blue:0) : Color(red:0.11,green:0.11,blue:0.16)))
                        .position(x: r + cos(mid) * r * 0.62, y: r + sin(mid) * r * 0.62)
                }
                // 잭팟 칸 금색 테두리
                WedgeShape(startDeg: -90.0 - sweep/2, sweepDeg: sweep)
                    .stroke(Color(red:1,green:0.69,blue:0.18), lineWidth: 3)
            }
        }
    }
}

struct WedgeShape: Shape {
    let startDeg: Double; let sweepDeg: Double
    func path(in rect: CGRect) -> Path {
        var p = Path()
        let c = CGPoint(x: rect.midX, y: rect.midY)
        p.move(to: c)
        p.addArc(center: c, radius: rect.width/2,
                 startAngle: .degrees(startDeg), endAngle: .degrees(startDeg + sweepDeg), clockwise: false)
        p.closeSubpath()
        return p
    }
}
```

> KMP enum 케이스명은 `RoulettePrize.jackpot100/.e10/.e3/.miss`(camelCase 예상). `xcodebuild` 빌드에서 실제 export 케이스명 확인 후 불일치 시 `switch`/비교를 맞춘다. `import CashChatShared`·`import Combine` 필수.

- [ ] **Step 2: 혜택존에 룰렛 진입 카드 추가**

`BenefitZoneScreen.swift`에서 `RewardAdCardView(...)` **아래**에 추가하고, 상단에 상태 추가:
- `struct BenefitZoneScreen` 본문에 `@State private var showRoulette = false` 추가.
- `RewardAdCardView(...)` 블록 다음에:
```swift
                BenefitInfoCardView(icon: "die.face.5.fill", title: "행운 룰렛", badge: .next,
                    description: "하루 1회 무료 · 광고로 최대 5회 · 에너지 잭팟까지!", dimmed: false)
                    .padding(.horizontal, 16)
                    .onTapGesture { showRoulette = true }
```
- `ScrollView { ... }` 에 `.sheet` 추가(예: `.refreshable` 줄 근처, View 체이닝에):
```swift
        .sheet(isPresented: $showRoulette) {
            RouletteView(onClose: { showRoulette = false })
        }
```

- [ ] **Step 3: shared 프레임워크 재빌드 + Xcode 빌드 (에이전트가 직접)**

```bash
cd apps/frontend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && ./gradlew :shared:embedAndSignAppleFrameworkForXcode
xcodebuild -project CashChatIOS/CashChatIOS.xcodeproj -scheme CashChatIOS \
  -destination 'platform=iOS Simulator,name=iPhone 16' build 2>&1 | tail -30
```
Expected: BUILD SUCCEEDED. 실패 시 에러를 근본 원인 파일 기준으로 해석해 수정(특히 enum 케이스명·import).

- [ ] **Step 4: 커밋**

```bash
git add apps/frontend/CashChatIOS/CashChatIOS/BenefitZone/RouletteView.swift apps/frontend/CashChatIOS/CashChatIOS/BenefitZoneScreen.swift
git commit -m "feat(roulette): ios 룰렛 뷰·혜택존 진입 카드 추가"
```

---

## Self-Review 체크

- **Spec coverage:** 메커니즘(무료1+광고4, 가중확률)=Task1 Fake + Task2 Store; 휠 C안=Task4(Android)/Task5(iOS); FE-first 격리(Repository+Fake)=Task1; DI=Task3; 진입점=Task4/5; 테스트=Task1/2. BE 계약은 문서(별도 산출물). 누락 없음.
- **Type consistency:** `RoulettePrize{JACKPOT_100,E10,E3,MISS}`·`RouletteStatus`(availableSpins/adSpinsRemaining/segments)·`RouletteSpinResult`(prize/segmentIndex/awardedEnergy)·`RouletteStore`(refresh/spin/watchAdForSpin)·`RouletteRepository`(getStatus/spin/requestAdSpinNonce/awaitSpinCredited) — Task 전반 일관. iOS 케이스명만 빌드 시 확정(주의 표기).
- **Placeholder scan:** iOS enum 케이스명·각도 보정 외 placeholder 없음. 각도 공식은 그리기 규약과 일치하도록 상수로 명시.

## 후속
- BE `/api/roulette/*` 구현 후 `RemoteRouletteRepository` 추가 + DI 교체(인터페이스 불변).
- 친구 초대(슬라이스 4).
