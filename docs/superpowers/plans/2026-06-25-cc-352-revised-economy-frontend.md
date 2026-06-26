# CC-352 개정 경제모델(R1/R2) 프론트 대응 — 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 백엔드 개정 경제모델(R1 채팅 완료 보상, R2 진화 경험치 차감)에 맞춰 Android·iOS 프론트의 진화 통화 표시·에러 처리·보유 경험치 표시(전방 호환)·채팅 완료 보상 연출을 대응하고, 채팅 화면 출석 UI/자동출석을 제거한다.

**Architecture:** 공유(`:shared` commonMain) DTO/상태에 nullable 필드를 추가해 백엔드가 나중에 `currentExp`를 보내면 코드 수정 없이 자동 노출되게 한다(전방 호환). 진화 시도 에러는 코드(`INSUFFICIENT_EVOLUTION_EXP`) 기반으로 분기한다. 채팅 완료 신호(`ChatStore.streamCompletedCount`)를 보상 연출(버블 파티클 + HUD 코인 카운트업/경험치 표시) 트리거로 재사용한다(충실도 B: 디커플드). 출석은 혜택존에 완전 구현돼 있어 채팅에서 제거한다.

**Tech Stack:** Kotlin Multiplatform, kotlinx.serialization, Jetpack Compose(Material3), SwiftUI, Koin, kotlin.test(commonTest).

**관련 문서:** 설계 [`docs/superpowers/specs/2026-06-25-cc-352-revised-economy-frontend-design.md`](../specs/2026-06-25-cc-352-revised-economy-frontend-design.md) · BE 요청 [`docs/issues/2026-06-25-cc-352-evolution-be-api-requests.md`](../../issues/2026-06-25-cc-352-evolution-be-api-requests.md)

**커밋 규칙:** 한국어 Conventional Commits. 본문 끝에 `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`.

---

## 파일 구조 (생성/수정)

**공유 (`:shared` commonMain)**
- 수정 `shared/.../core/network/ApiException.kt` — 새 에러 상수
- 수정 `shared/.../evolution/EvolutionApi.kt` — `EvolutionStateDto.currentExp`
- 수정 `shared/.../hud/HudStore.kt` — `HudState.exp` + 매핑
- 테스트 `shared/src/commonTest/.../evolution/EvolutionStateDtoTest.kt`(생성), `core/network/ApiErrorTest.kt`(수정), `hud/HudStoreTest.kt`(생성)

**Android (`:app`)**
- 수정 `feature/chat/evolution/EvolutionViewModel.kt` — 에러 분기
- 수정 `feature/chat/evolution/EvolutionScreen.kt` — 통화 라벨(코인→경험치) + 보유 경험치 표시
- 수정 `feature/chat/ChatViewModel.kt` — 출석 제거
- 수정 `feature/chat/ChatScreen.kt` — 출석 UI 제거 + 보상 연출 오버레이 + HUD 경험치
- 삭제 `feature/chat/AttendanceSheet.kt`(채팅 전용이면)
- 수정 `di/AppModule.kt`(또는 DI 정의 파일) — `ChatViewModel`의 `attendanceApi` 주입 제거
- 생성 `feature/chat/components/RewardBurstOverlay.kt` — 보상 파티클 오버레이

**iOS (`CashChatIOS`)**
- 생성 `CashChatIOS/ApiErrorMapping.swift` — `Error`에서 ApiException 코드 추출
- 수정 `CashChatIOS/EvolutionScreen.swift` — 에러 분기 + 통화 라벨 + 보유 경험치
- 수정 `CashChatIOS/ChatViewModel.swift` — 출석 제거 + 보상 틱
- 수정 `CashChatIOS/ChatScreen.swift` — 출석 UI 제거 + 보상 연출 + HUD 경험치
- 생성 `CashChatIOS/RewardBurstOverlay.swift` — 보상 파티클 오버레이

---

## Phase 1 — 공유(commonMain) 전방 호환 기반

### Task 1: 새 에러 상수 `INSUFFICIENT_EVOLUTION_EXP`

**Files:**
- Modify: `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/core/network/ApiException.kt`
- Test: `apps/frontend/shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/core/network/ApiErrorTest.kt`

- [ ] **Step 1: 실패 테스트 추가**

`ApiErrorTest.kt`에 추가:
```kotlin
    @Test
    fun `진화 경험치 부족 에러를 파싱한다`() {
        val exception = parseApiError(
            httpStatus = 409,
            body = """{ "code": "INSUFFICIENT_EVOLUTION_EXP", "message": "진화 경험치가 부족합니다." }""",
        )
        assertEquals(ApiException.INSUFFICIENT_EVOLUTION_EXP, exception.code)
        assertEquals(409, exception.httpStatus)
    }
```

- [ ] **Step 2: 테스트 실패 확인 (컴파일 에러: 상수 없음)**

Run: `cd apps/frontend && ./gradlew :shared:compileDebugUnitTestKotlinAndroid`
Expected: FAIL — `Unresolved reference: INSUFFICIENT_EVOLUTION_EXP`

- [ ] **Step 3: 상수 추가**

`ApiException.kt`의 `companion object`에 추가(기존 `INSUFFICIENT_POINTS` 유지):
```kotlin
        const val INSUFFICIENT_EVOLUTION_EXP = "INSUFFICIENT_EVOLUTION_EXP"
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd apps/frontend && ./gradlew :shared:testDebugUnitTest --tests "*ApiErrorTest*"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/core/network/ApiException.kt apps/frontend/shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/core/network/ApiErrorTest.kt
git commit -m "feat(shared): INSUFFICIENT_EVOLUTION_EXP 에러 코드 추가

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

### Task 2: `EvolutionStateDto.currentExp` (전방 호환 nullable)

**Files:**
- Modify: `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/evolution/EvolutionApi.kt`
- Test: `apps/frontend/shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/evolution/EvolutionStateDtoTest.kt` (생성)

- [ ] **Step 1: 실패 테스트 작성**

새 파일 `EvolutionStateDtoTest.kt`:
```kotlin
package com.nomadclub.cashchat.shared.evolution

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EvolutionStateDtoTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `currentExp 없는 응답은 null로 역직렬화된다`() {
        val dto = json.decodeFromString<EvolutionStateDto>(
            """{ "level": 2, "isMaxLevel": false, "nextAttemptCost": 1200, "nextSuccessRate": 0.5 }"""
        )
        assertEquals(2, dto.level)
        assertNull(dto.currentExp)
    }

    @Test
    fun `currentExp 있는 응답은 값으로 역직렬화된다`() {
        val dto = json.decodeFromString<EvolutionStateDto>(
            """{ "level": 2, "isMaxLevel": false, "nextAttemptCost": 1200, "nextSuccessRate": 0.5, "currentExp": 3400 }"""
        )
        assertEquals(3400L, dto.currentExp)
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd apps/frontend && ./gradlew :shared:compileDebugUnitTestKotlinAndroid`
Expected: FAIL — `Unresolved reference: currentExp`

- [ ] **Step 3: DTO 필드 추가**

`EvolutionApi.kt`의 `EvolutionStateDto`에 필드 추가:
```kotlin
@Serializable
data class EvolutionStateDto(
    val level: Int,
    val isMaxLevel: Boolean,
    val nextAttemptCost: Long? = null,
    val nextSuccessRate: Double? = null,
    val currentExp: Long? = null, // BE 미배포 시 null → UI 미표시. 배포 시 자동 노출(전방 호환).
)
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd apps/frontend && ./gradlew :shared:testDebugUnitTest --tests "*EvolutionStateDtoTest*"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/evolution/EvolutionApi.kt apps/frontend/shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/evolution/EvolutionStateDtoTest.kt
git commit -m "feat(shared): EvolutionStateDto에 currentExp 전방호환 필드 추가

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

### Task 3: `HudState.exp` + `HudStore` 매핑

**Files:**
- Modify: `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/hud/HudStore.kt`
- Test: `apps/frontend/shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/hud/HudStoreTest.kt` (생성)

- [ ] **Step 1: 실패 테스트 작성**

새 파일 `HudStoreTest.kt`. `HudState`에 `exp` 필드가 있고 기본값 null인지 검증:
```kotlin
package com.nomadclub.cashchat.shared.hud

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HudStoreTest {
    @Test
    fun `HudState exp 기본값은 null이다`() {
        val state = HudState()
        assertNull(state.exp)
    }

    @Test
    fun `HudState는 exp 값을 보관한다`() {
        val state = HudState(exp = 3400L)
        assertEquals(3400L, state.exp)
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd apps/frontend && ./gradlew :shared:compileDebugUnitTestKotlinAndroid`
Expected: FAIL — `No value passed for parameter` 또는 `Unresolved reference: exp`

- [ ] **Step 3: HudState 필드 + 매핑 추가**

`HudStore.kt` `HudState`에 필드 추가:
```kotlin
    val points: Long? = null,
    /** 진화 경험치(R2 비용 통화). BE currentExp 미배포 시 null. */
    val exp: Long? = null,
    /** P1-3 — ISO-8601 원본. 파싱은 플랫폼단(java.time 등)에서. */
    val nextRecoverAt: String? = null,
```

`refreshNow()`의 `_state.value = HudState(...)`에 매핑 추가:
```kotlin
        _state.value = HudState(
            level = evolution.level,
            isMaxLevel = evolution.isMaxLevel,
            energy = energy.energy,
            maxEnergy = energy.maxEnergy,
            points = pointsDeferred?.await(),
            exp = evolution.currentExp,
            nextRecoverAt = energy.nextRecoverAt,
            isLoaded = true,
        )
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd apps/frontend && ./gradlew :shared:testDebugUnitTest --tests "*HudStoreTest*"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/hud/HudStore.kt apps/frontend/shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/hud/HudStoreTest.kt
git commit -m "feat(shared): HudState에 exp 추가하고 currentExp 매핑

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Phase 2 — Android

### Task 4: Android 진화 에러 분기 (경험치 부족)

**Files:**
- Modify: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/chat/evolution/EvolutionViewModel.kt:56-60`

- [ ] **Step 1: 에러 매핑 수정**

`EvolutionViewModel.attempt()`의 `when (e.code)` 블록을 다음으로 교체:
```kotlin
                _errorMessage.value = when (e.code) {
                    ApiException.INSUFFICIENT_EVOLUTION_EXP,
                    ApiException.INSUFFICIENT_POINTS -> "경험치가 부족해요. 채팅으로 모아볼까요?"
                    ApiException.ALREADY_MAX_LEVEL -> "이미 최고 레벨이에요!"
                    else -> e.message
                }
```

- [ ] **Step 2: 컴파일 확인**

Run: `cd apps/frontend && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/chat/evolution/EvolutionViewModel.kt
git commit -m "fix(evolution): 진화 시도 경험치 부족 에러 코드 대응

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

### Task 5: Android 진화 화면 통화 라벨(코인→경험치) + 보유 경험치 표시

**Files:**
- Modify: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/chat/evolution/EvolutionScreen.kt`

- [ ] **Step 1: 비용 StatRow 라벨 정정 (line 195)**

```kotlin
                    StatRow("다음 진화 비용", "⭐ %,d 경험치".format(evolution.nextAttemptCost ?: 0))
```

- [ ] **Step 2: 보유 경험치 행 추가 (line 195 비용 행 바로 뒤, 성공확률 StatRow 앞)**

`currentExp`가 있을 때만 노출:
```kotlin
                    evolution.currentExp?.let { exp ->
                        Spacer(Modifier.height(6.dp))
                        StatRow("보유 경험치", "⭐ %,d".format(exp))
                    }
```

- [ ] **Step 3: 진화 버튼 충분 여부 게이팅 (line 224-229)**

`Button`의 `enabled`와 라벨을 경험치 충분 여부로 분기:
```kotlin
                    val cost = evolution.nextAttemptCost ?: 0L
                    val canAfford = evolution.currentExp?.let { it >= cost } ?: true
                    Button(
                        onClick = { viewModel.attempt() },
                        enabled = phase == EvolutionViewModel.Phase.IDLE && canAfford,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(
                            when {
                                phase != EvolutionViewModel.Phase.IDLE -> "두근두근..."
                                !canAfford -> "경험치가 부족해요"
                                else -> "🎰 진화 시도하기"
                            }
                        )
                    }
```

- [ ] **Step 4: 실패 결과 카드 통화 정정 (line 270-273)**

```kotlin
                        Text(
                            if (attemptResult.success) "진화 성공! 밥도 보너스로 충전됐어요 ⚡"
                            else "이번엔 실패했어요 (-%,d 경험치). 다시 도전해볼까요?".format(attemptResult.cost),
                        )
```

- [ ] **Step 5: 진화 기록 supportingContent 통화 정정 (line 244)**

```kotlin
                                    supportingContent = { Text("⭐${record.cost} · ${record.attemptedAt.take(10)}") },
```

- [ ] **Step 6: 컴파일 확인**

Run: `cd apps/frontend && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: 커밋**

```bash
git add apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/chat/evolution/EvolutionScreen.kt
git commit -m "feat(evolution): 진화 비용 통화를 경험치로 표기하고 보유 경험치/충분여부 노출

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

### Task 6: Android 채팅 출석 UI/자동출석 제거

**Files:**
- Modify: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/chat/ChatViewModel.kt`
- Modify: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/chat/ChatScreen.kt`
- Delete: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/chat/AttendanceSheet.kt` (채팅 외 참조 없을 때)
- Modify: DI 정의 (`ChatViewModel` 생성자에서 `attendanceApi` 제거 반영)

- [ ] **Step 1: 사전 조사 — AttendanceSheet/CheckInRewardDialog 참조 확인**

Run:
```bash
cd apps/frontend && grep -rn "AttendanceCalendar\|AttendanceSheet\|CheckInRewardDialog" app/src/main --include="*.kt"
```
Expected: 정의 위치(`AttendanceSheet.kt`)와 사용처(`ChatScreen.kt`)만 나오는지 확인. 채팅 외 사용처가 있으면 파일 삭제 대신 해당 심볼만 보존.

- [ ] **Step 2: `ChatViewModel.kt`에서 출석 제거**

- 생성자 파라미터 `private val attendanceApi: AttendanceApi,` 삭제
- import 삭제: `AttendanceApi`, `CheckInDto`, `MonthlyAttendanceDto`
- 필드 삭제: `_attendance`/`attendance`, `_checkInResult`/`checkInResult` (line 30-34)
- `init {}`의 자동 출석 `viewModelScope.launch { ... 자동 출석 ... }` 블록(line 38-52) 삭제
- 메서드 `dismissCheckIn()` (line 133) 삭제
- `pointsRepository`가 출석 외에 쓰이지 않으면(사전 grep으로 확인) 생성자에서 제거. 다른 사용처 있으면 유지.

- [ ] **Step 3: `ChatScreen.kt`에서 출석 UI 제거**

- import 삭제: `androidx.compose.material.icons.filled.CalendarMonth`, `androidx.compose.material3.ModalBottomSheet`
- 상태 제거: `val attendance by ...`(line 92), `val checkInResult by ...`(line 93), `var showAttendance by remember ...`(line 95)
- 톱바 캘린더 버튼 블록 삭제(line 158-162):
```kotlin
            if (attendance != null) {
                IconButton(onClick = { showAttendance = true }) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = "출석 캘린더")
                }
            }
```
- 하단 출석 결과/시트 블록 삭제(line 311-321): `checkInResult?.let { ... CheckInRewardDialog ... }`, `if (showAttendance) { ... AttendanceCalendar ... }`

- [ ] **Step 4: `AttendanceSheet.kt` 처리**

Step 1에서 채팅 전용으로 확인됐으면 파일 삭제:
```bash
cd apps/frontend && git rm app/src/main/java/com/nomadclub/cashchat/feature/chat/AttendanceSheet.kt
```
(채팅 외 참조가 있으면 삭제하지 말고 해당 참조만 유지.)

- [ ] **Step 5: DI 수정**

Run: `cd apps/frontend && grep -rn "ChatViewModel(" app/src/main --include="*.kt"`
`ChatViewModel`을 생성하는 Koin 모듈 정의에서 `attendanceApi`(및 제거했다면 `pointsRepository`) 인자를 제거.

- [ ] **Step 6: 컴파일 확인**

Run: `cd apps/frontend && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (잔여 참조 에러 없으면 통과 — 에러 나면 해당 import/사용처 정리)

- [ ] **Step 7: 커밋**

```bash
git add -A apps/frontend/app/src/main/java/com/nomadclub/cashchat
git commit -m "refactor(chat): 채팅 화면 출석 UI와 자동 출석 제거(혜택존으로 일원화)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

### Task 7: Android 채팅 완료 보상 연출 (버블 파티클 + HUD 경험치/코인)

**Files:**
- Create: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/chat/components/RewardBurstOverlay.kt`
- Modify: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/chat/ChatViewModel.kt`
- Modify: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/chat/ChatScreen.kt`

- [ ] **Step 1: ViewModel에 보상 틱 노출**

`ChatViewModel.kt`에 추가 (스트림 완료 신호 재사용 — HUD 전체 갱신으로 exp/points도 반영):
```kotlin
    private val _rewardBurstTick = MutableStateFlow(0)
    val rewardBurstTick = _rewardBurstTick.asStateFlow()
```
`init`의 `streamCompletedCount` 수집 블록(현재 line 53-55)을 교체:
```kotlin
        viewModelScope.launch {
            chatStore.streamCompletedCount.collect {
                if (it > 0) {
                    runCatching { hudStore.refreshNow() } // 코인/경험치/에너지 일괄 재조회
                    _rewardBurstTick.value += 1            // 보상 연출 트리거
                }
            }
        }
```
(주의: `HudStore.refreshNow()`는 `@Throws suspend` — `runCatching` 내 호출. import 불필요, 같은 store 인스턴스.)

- [ ] **Step 2: RewardBurstOverlay 컴포저블 생성**

`RewardBurstOverlay.kt`:
```kotlin
package com.nomadclub.cashchat.feature.chat.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 채팅 완료 보상 연출(충실도 B): tick 이 바뀔 때마다 화면 하단(응답 버블 영역)에서
 * 별/코인 입자가 위쪽(HUD 방향)으로 흘러오르며 페이드한다. HUD 좌표 추적 없이 동작.
 */
@Composable
fun RewardBurstOverlay(tick: Int, modifier: Modifier = Modifier) {
    var seedParticles by remember { mutableStateOf<List<Triple<Float, Float, Float>>>(emptyList()) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(tick) {
        if (tick <= 0) return@LaunchedEffect
        // 각 입자: (시작 x비율 0..1, 수평 드리프트 -0.1..0.1, 크기 시드 0..1)
        seedParticles = List(8) {
            Triple(0.25f + Random.nextFloat() * 0.5f, (Random.nextFloat() - 0.5f) * 0.2f, Random.nextFloat())
        }
        progress.snapTo(0f)
        progress.animateTo(1f, tween(900, easing = LinearOutSlowInEasing))
    }

    if (progress.value in 0f..1f && seedParticles.isNotEmpty() && progress.value < 1f) {
        Box(modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize()) {
                val p = progress.value
                seedParticles.forEach { (startXRatio, drift, seed) ->
                    val x = (startXRatio + drift * p) * size.width
                    // 하단 0.8 지점에서 위로 0.35 지점까지 상승
                    val y = size.height * (0.8f - 0.45f * p)
                    drawCircle(
                        color = (if (seed > 0.5f) Color(0xFFFFC107) else Color(0xFF7C4DFF))
                            .copy(alpha = (1f - p).coerceIn(0f, 1f)),
                        radius = 4.dp.toPx() + seed * 4.dp.toPx(),
                        center = Offset(x, y),
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 3: ChatScreen에서 오버레이 + HUD 경험치 칩 연결**

`ChatScreen.kt`:
- 상단 collect 추가(다른 collect 옆):
```kotlin
    val rewardTick by viewModel.rewardBurstTick.collectAsStateWithLifecycle()
```
- HUD 톱바의 코인 칩 옆(line 185 근처)에 경험치 칩 추가(값 있을 때만):
```kotlin
            hud.points?.let { StatChip("🪙", "%,d".format(it)) }
            hud.exp?.let { StatChip("⭐", "%,d".format(it)) }
```
- 메시지 리스트 `Box(Modifier.weight(1f)) { ... }`(line 208) 안, 리스트 렌더 뒤에 오버레이 추가(같은 Box 내 마지막 자식):
```kotlin
            RewardBurstOverlay(tick = rewardTick)
```

- [ ] **Step 4: 컴파일 확인**

Run: `cd apps/frontend && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: APK 빌드로 회귀 확인**

Run: `cd apps/frontend && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 커밋**

```bash
git add apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/chat
git commit -m "feat(chat): 채팅 완료 보상 획득 연출(버블 파티클 + HUD 경험치/코인) 추가

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Phase 3 — iOS

### Task 8: iOS ApiException 코드 추출 헬퍼

**Files:**
- Create: `apps/frontend/CashChatIOS/CashChatIOS/ApiErrorMapping.swift`

- [ ] **Step 1: 헬퍼 작성**

KMM에서 Kotlin `ApiException`은 throw 시 `NSError.userInfo["KotlinException"]`로 전달된다.
```swift
import Foundation
import CashChatShared

extension Error {
    /// KMM이 던진 Kotlin ApiException의 code를 추출한다(없으면 nil).
    var apiErrorCode: String? {
        let ns = self as NSError
        return (ns.userInfo["KotlinException"] as? ApiException)?.code
    }
}
```

- [ ] **Step 2: 컴파일 확인 (다음 Task에서 일괄 빌드)**

이 파일 단독 빌드는 생략하고 Task 9·11과 함께 `xcodebuild`로 검증한다.

- [ ] **Step 3: 커밋**

```bash
git add apps/frontend/CashChatIOS/CashChatIOS/ApiErrorMapping.swift
git commit -m "feat(ios): Error에서 ApiException code 추출 헬퍼 추가

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

### Task 9: iOS 진화 화면 — 에러 분기 + 통화 라벨 + 보유 경험치

**Files:**
- Modify: `apps/frontend/CashChatIOS/CashChatIOS/EvolutionScreen.swift`

- [ ] **Step 1: ViewModel에 currentExp 추가 + 매핑 + 게이팅**

`EvolutionViewModel`(EvolutionScreen.swift 내) 수정:
- published 추가:
```swift
    @Published var currentExp: Int64? = nil
```
- `apply(_:)`에 추가:
```swift
        currentExp = s.currentExp?.int64Value
```
- `canAttempt` 수정(경험치 충분 여부 반영):
```swift
    var canAttempt: Bool {
        guard isLoaded, !isAttempting, !isMaxLevel, let cost = nextCost else { return false }
        if let exp = currentExp { return exp >= cost }
        return true
    }
```

- [ ] **Step 2: attempt() 에러 분기**

`attempt()`의 `catch`를 코드 분기로 교체:
```swift
            } catch {
                switch error.apiErrorCode {
                case "INSUFFICIENT_EVOLUTION_EXP", "INSUFFICIENT_POINTS":
                    resultMessage = "경험치가 부족해요. 채팅으로 모아볼까요?"
                case "ALREADY_MAX_LEVEL":
                    resultMessage = "이미 최고 레벨이에요!"
                default:
                    resultMessage = "진화 시도에 실패했어요."
                }
            }
```

- [ ] **Step 3: 비용 라벨 + 보유 경험치 표시 (body)**

`if let cost = vm.nextCost { Text("비용 🪙\(cost)") ... }` 블록을 교체:
```swift
                    if let cost = vm.nextCost {
                        Text("비용 ⭐\(cost) 경험치").font(.subheadline).foregroundStyle(.secondary)
                    }
                    if let exp = vm.currentExp {
                        Text("보유 경험치 ⭐\(exp)").font(.subheadline).foregroundStyle(.secondary)
                    }
```

- [ ] **Step 4: 빌드 확인**

Run:
```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
cd apps/frontend && ./gradlew :shared:embedAndSignAppleFrameworkForXcode
cd CashChatIOS && xcodebuild -project CashChatIOS.xcodeproj -scheme CashChatIOS -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' build CODE_SIGNING_ALLOWED=NO | tail -5
```
Expected: `** BUILD SUCCEEDED **`

- [ ] **Step 5: 커밋**

```bash
git add apps/frontend/CashChatIOS/CashChatIOS/EvolutionScreen.swift
git commit -m "feat(ios/evolution): 진화 통화 경험치 표기 + 보유 경험치/충분여부 + 경험치부족 에러 대응

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

### Task 10: iOS 채팅 출석 UI/자동출석 제거

**Files:**
- Modify: `apps/frontend/CashChatIOS/CashChatIOS/ChatViewModel.swift`
- Modify: `apps/frontend/CashChatIOS/CashChatIOS/ChatScreen.swift`

- [ ] **Step 1: 사전 조사 — AttendanceSheet 정의/참조 확인**

Run: `grep -rn "struct AttendanceSheet\|AttendanceSheet()" apps/frontend/CashChatIOS`
Expected: `ChatScreen.swift` 내 정의(line 443)와 사용(line 41)만. (혜택존은 `AttendanceWidgetView` 별도 사용.)

- [ ] **Step 2: `ChatViewModel.swift` 출석 제거**

- published 삭제(line 24-29): `attendanceMonth`, `attendanceStreak`, `attendanceCheckedDays`, `attendanceTodayChecked`, `checkInToast`
- 필드 삭제: `private let attendanceStore = ...`(line 42), `private var hasAttemptedAutoCheckIn`(line 47)
- `load()`의 출석 블록 삭제: `attendanceStore.loadMonthly(...)` + `collector.collectAttendance {...}`(line 91-104) + `collector.collectRewards {...}`(line 105-107)

- [ ] **Step 3: `ChatScreen.swift` 출석 UI 제거**

- `@State private var showAttendance = false`(line 19) 삭제
- `.sheet(isPresented: $showAttendance) { AttendanceSheet() }`(line 40-42) 삭제
- header의 출석 버튼 삭제(line 89-92):
```swift
            Button { showAttendance = true } label: {
                Image(systemName: chatSFSymbol("calendar", fallback: "calendar.circle"))
                    .foregroundStyle(.primary)
            }
```
- `.overlay(alignment: .top) { if let toast = vm.checkInToast { ... } }`(line 53-67) 삭제
- `.animation(.easeInOut, value: vm.checkInToast)`(line 68) 삭제
- `AttendanceSheet` 구조체 정의(line 442-451) 삭제

- [ ] **Step 4: 빌드 확인**

Run:
```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
cd apps/frontend && ./gradlew :shared:embedAndSignAppleFrameworkForXcode
cd CashChatIOS && xcodebuild -project CashChatIOS.xcodeproj -scheme CashChatIOS -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' build CODE_SIGNING_ALLOWED=NO | tail -5
```
Expected: `** BUILD SUCCEEDED **`

- [ ] **Step 5: 커밋**

```bash
git add apps/frontend/CashChatIOS/CashChatIOS/ChatViewModel.swift apps/frontend/CashChatIOS/CashChatIOS/ChatScreen.swift
git commit -m "refactor(ios/chat): 채팅 화면 출석 UI와 자동 출석 제거(혜택존으로 일원화)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

### Task 11: iOS 채팅 완료 보상 연출 (버블 파티클 + HUD 경험치/코인)

**Files:**
- Create: `apps/frontend/CashChatIOS/CashChatIOS/RewardBurstOverlay.swift`
- Modify: `apps/frontend/CashChatIOS/CashChatIOS/ChatViewModel.swift`
- Modify: `apps/frontend/CashChatIOS/CashChatIOS/ChatScreen.swift`

- [ ] **Step 1: ViewModel에 exp + 보상 틱 추가, 완료 시 전체 HUD 갱신**

`ChatViewModel.swift`:
- published 추가(HUD 영역):
```swift
    @Published var exp: Int64? = nil
    @Published var rewardBurstTick: Int = 0
```
- `collectHud` 콜백에 추가(`self.points = ...` 옆):
```swift
                self.exp = s.exp?.int64Value
```
- `collectStreamCompleted` 콜백(line 84-89)을 교체(완료 시 전체 HUD 갱신 + 보상 틱):
```swift
        collector.collectStreamCompleted(store: store) { [weak self] count in
            Task { @MainActor in
                guard let self, count.intValue > 0 else { return }
                try? await self.hudStore.refreshNow()  // 코인/경험치/에너지 일괄 갱신
                self.rewardBurstTick += 1               // 보상 연출 트리거
            }
        }
```

- [ ] **Step 2: RewardBurstOverlay 뷰 생성**

`RewardBurstOverlay.swift`:
```swift
import SwiftUI

/// 채팅 완료 보상 연출(충실도 B): tick 변경 시 하단(응답 버블 영역)에서 별/코인 입자가
/// 위쪽(HUD 방향)으로 흘러오르며 페이드. HUD 좌표 추적 없이 동작.
struct RewardBurstOverlay: View {
    let tick: Int
    @State private var animateTick: Int = 0
    @State private var progress: CGFloat = 0
    private let seeds: [CGFloat] = (0..<8).map { _ in CGFloat.random(in: 0...1) }

    var body: some View {
        GeometryReader { geo in
            ZStack {
                ForEach(seeds.indices, id: \.self) { i in
                    let startX = (0.25 + seeds[i] * 0.5) * geo.size.width
                    let y = geo.size.height * (0.8 - 0.45 * progress)
                    Circle()
                        .fill(seeds[i] > 0.5 ? Color(red: 1.0, green: 0.76, blue: 0.03)
                                             : Color(red: 0.49, green: 0.30, blue: 1.0))
                        .frame(width: 8 + seeds[i] * 8, height: 8 + seeds[i] * 8)
                        .position(x: startX, y: y)
                        .opacity(Double(1 - progress))
                }
            }
        }
        .allowsHitTesting(false)
        .onChange(of: tick) { newValue in
            guard newValue > 0, newValue != animateTick else { return }
            animateTick = newValue
            progress = 0
            withAnimation(.easeOut(duration: 0.9)) { progress = 1 }
        }
    }
}
```

- [ ] **Step 3: ChatScreen에 오버레이 + HUD 경험치 칩 연결**

`ChatScreen.swift`:
- header의 코인 칩 옆(line 94-96)에 경험치 칩 추가:
```swift
                if let p = vm.points {
                    chip("🪙", "\(p)")
                }
                if let e = vm.exp {
                    chip("⭐", "\(e)")
                }
```
- `messageList`를 오버레이로 감싸기. `var body`의 `messageList` 호출(line 30)을 교체:
```swift
            messageList
                .overlay { RewardBurstOverlay(tick: vm.rewardBurstTick) }
```

- [ ] **Step 4: 빌드 확인**

Run:
```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
cd apps/frontend && ./gradlew :shared:embedAndSignAppleFrameworkForXcode
cd CashChatIOS && xcodebuild -project CashChatIOS.xcodeproj -scheme CashChatIOS -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' build CODE_SIGNING_ALLOWED=NO | tail -5
```
Expected: `** BUILD SUCCEEDED **`

- [ ] **Step 5: 커밋**

```bash
git add apps/frontend/CashChatIOS/CashChatIOS/RewardBurstOverlay.swift apps/frontend/CashChatIOS/CashChatIOS/ChatViewModel.swift apps/frontend/CashChatIOS/CashChatIOS/ChatScreen.swift
git commit -m "feat(ios/chat): 채팅 완료 보상 획득 연출(버블 파티클 + HUD 경험치/코인) 추가

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Phase 4 — 통합 검증

### Task 12: 전체 빌드·테스트 + 스모크

- [ ] **Step 1: 공유 테스트 전체**

Run: `cd apps/frontend && ./gradlew :shared:testDebugUnitTest`
Expected: PASS (Task 1·2·3 신규 테스트 포함)

- [ ] **Step 2: Android APK 빌드**

Run: `cd apps/frontend && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: iOS 빌드**

Run:
```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
cd apps/frontend/CashChatIOS && xcodebuild -project CashChatIOS.xcodeproj -scheme CashChatIOS -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' build CODE_SIGNING_ALLOWED=NO | tail -5
```
Expected: `** BUILD SUCCEEDED **`

- [ ] **Step 4: 시뮬레이터 스모크(육안)**

Android 에뮬레이터/iOS 시뮬레이터에서:
- 진화 화면: 비용이 "⭐ 경험치"로 표기되는지, (BE `currentExp` 미배포 상태에서는) 보유 경험치 행/게이팅이 숨겨지고 기존처럼 동작하는지
- 채팅: 응답 완료 시 버블 영역에서 파티클이 위로 떠오르고 HUD 코인이 갱신되는지
- 채팅 톱바·하단에 출석 버튼/시트/토스트가 더 이상 없는지
- 혜택존: 출석 도장 찍기가 정상 동작하는지(회귀 없음)

- [ ] **Step 5: 최종 상태 확인**

Run: `cd apps/frontend && git status && git log --oneline -12`
Expected: 작업 트리 clean, 커밋 히스토리에 Task 1~11 반영.

---

## 자체 점검 결과 (작성자)

- **스펙 커버리지**: A(Task 5,9) B(Task 1,4,8,9) C(Task 2,3,5,9,7,11) D(변경 없음·BE 문서) E(Task 7,11) F(Task 6,10) G(이미 커밋됨) — 전 항목 매핑됨.
- **전방 호환**: `currentExp`/`exp` 모두 nullable 기본 null, UI는 값 존재 시에만 노출 → BE 미배포 상태에서도 안전.
- **타입 일관성**: `currentExp: Long?`(Kotlin) ↔ `s.currentExp?.int64Value`(Swift), `HudState.exp: Long?` ↔ `s.exp?.int64Value`. `rewardBurstTick`/`RewardBurstOverlay(tick:)` 시그니처 일치.
- **하위 호환**: `INSUFFICIENT_POINTS` 상수·분기 유지.
