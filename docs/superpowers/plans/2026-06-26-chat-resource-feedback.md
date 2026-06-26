# Chat Resource Feedback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 사용자 요청 버블에 `⚡ -1` 에너지 차감을 표시하고, AI 답변 완료 시 큰 `🪙 +1`, `⭐ +1` 보상 토큰이 버블에서 HUD로 이동하도록 Android/iOS를 개편한다.

**Architecture:** shared `ChatStore`가 메시지 ID를 포함한 일회성 자원 피드백 이벤트를 발행한다. 플랫폼은 메시지 버블과 HUD 좌표를 측정하고, 최상위 오버레이에서 토큰 이동을 렌더링한다. `Done` 즉시 보상 연출을 시작하고 HUD 갱신은 병렬 수행한다.

**Tech Stack:** Kotlin Multiplatform Flow, Jetpack Compose animation/layout coordinates, SwiftUI PreferenceKey/GeometryEffect, Kotlin tests

## Global Constraints

- 사용자 요청 비용은 경험치가 아니라 에너지 `⚡ -1`이다.
- 에너지 차감 배지는 28–32dp, 약 0.9초 노출한다.
- 완료 보상 배지는 최소 44dp, 아이콘 24–28dp, 숫자 16sp 이상이다.
- 완료 보상은 포인트 `🪙 +1`과 진화 경험치 `⭐ +1`이다.
- 보상 이동 전체 시간은 약 1.4초이며 두 토큰은 0.12초 간격으로 출발한다.
- 오류·취소에서는 완료 보상을 표시하지 않는다.
- 사용자가 과거 메시지를 보는 중이면 강제 스크롤하지 않는다.
- Reduce Motion에서는 이동 대신 버블 배지와 HUD 크로스페이드를 사용한다.

---

## File Structure

- Create `shared/.../chat/ChatResourceFeedback.kt`: 공통 이벤트 모델
- Modify `shared/.../chat/ChatStore.kt`: 메시지 ID 기반 이벤트 발행
- Modify `shared/.../di/IosBridges.kt`: iOS Flow 브리지
- Modify Android `ChatViewModel.kt`, `ChatScreen.kt`, `ChatComponents.kt`
- Replace Android `RewardBurstOverlay.kt` with token overlay
- Create Android `ChatRewardToken.kt`
- Modify iOS `ChatViewModel.swift`, `ChatScreen.swift`
- Replace iOS `RewardBurstOverlay.swift`
- Create iOS `ResourceDeltaBadge.swift`

### Task 1: Shared Resource Feedback Events

**Files:**
- Create: `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/chat/ChatResourceFeedback.kt`
- Modify: `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/chat/ChatStore.kt`
- Test: `apps/frontend/shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/chat/ChatStoreTest.kt`

**Interfaces:**
- Produces: `ChatResourceFeedback`, `ChatStore.resourceFeedback`

- [ ] **Step 1: Add failing event tests**

```kotlin
@Test
fun `first token confirms energy spend for user message`() = runTest {
    val gateway = FakeChatGateway().apply {
        streamResult = {
            flow {
                emit(ChatStreamEvent.Token("응답"))
                emit(ChatStreamEvent.Done)
            }
        }
    }
    val store = ChatStore(gateway, this)
    store.sendMessage("질문")
    testScheduler.advanceUntilIdle()
    val event = store.resourceFeedback.value
    assertIs<ChatResourceFeedback.EnergySpent>(event)
    assertEquals(-1, event.amount)
}

@Test
fun `done publishes rewards for completed assistant`() = runTest {
    val gateway = FakeChatGateway().apply {
        streamResult = {
            flow {
                emit(ChatStreamEvent.Token("응답"))
                emit(ChatStreamEvent.Done)
            }
        }
    }
    val store = ChatStore(gateway, this)
    store.sendMessage("질문")
    testScheduler.advanceUntilIdle()
    val reward = store.lastCompletedReward.value
    assertEquals(1, reward?.pointDelta)
    assertEquals(1, reward?.expDelta)
}
```

- [ ] **Step 2: Verify failure**

Run: `cd apps/frontend && ./gradlew :shared:testDebugUnitTest --tests "*ChatStoreTest*"`

Expected: FAIL because feedback flows do not exist.

- [ ] **Step 3: Add event models**

```kotlin
sealed interface ChatResourceFeedback {
    val eventId: Long
    val messageId: String

    data class EnergySpent(
        override val eventId: Long,
        override val messageId: String,
        val amount: Int = -1,
    ) : ChatResourceFeedback

    data class RewardEarned(
        override val eventId: Long,
        override val messageId: String,
        val pointDelta: Long = 1,
        val expDelta: Long = 1,
    ) : ChatResourceFeedback
}
```

Expose `StateFlow<ChatResourceFeedback?>`. Emit `EnergySpent` once when the first token confirms the request; emit `RewardEarned` on `Done` using the assistant message ID. Clear it in `reset()`.

- [ ] **Step 4: Add negative tests**

Assert no reward on `StreamError`, no energy event on `INSUFFICIENT_ENERGY`, and no duplicate energy event on multiple tokens.

- [ ] **Step 5: Run tests**

Run: `cd apps/frontend && ./gradlew :shared:testDebugUnitTest --tests "*ChatStoreTest*"`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/chat/ChatResourceFeedback.kt apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/chat/ChatStore.kt apps/frontend/shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/chat/ChatStoreTest.kt
git commit -m "feat(chat): publish resource feedback events"
```

### Task 2: iOS Flow Bridge

**Files:**
- Modify: `apps/frontend/shared/src/iosMain/kotlin/com/nomadclub/cashchat/shared/di/IosBridges.kt`

**Interfaces:**
- Consumes: `ChatStore.resourceFeedback`
- Produces: `FlowCollector.collectResourceFeedback`

- [ ] **Step 1: Add the bridge**

```kotlin
fun collectResourceFeedback(
    store: ChatStore,
    onEach: (ChatResourceFeedback?) -> Unit,
) {
    scope.launch { store.resourceFeedback.collect { onEach(it) } }
}
```

- [ ] **Step 2: Compile the shared Apple framework**

Run: `cd apps/frontend && ./gradlew :shared:compileKotlinIosSimulatorArm64`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add apps/frontend/shared/src/iosMain/kotlin/com/nomadclub/cashchat/shared/di/IosBridges.kt
git commit -m "feat(ios): bridge chat resource feedback"
```

### Task 3: Android Energy Badge

**Files:**
- Modify: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/chat/ChatViewModel.kt`
- Modify: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/chat/components/ChatComponents.kt`
- Create: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/chat/components/ResourceDeltaBadge.kt`

**Interfaces:**
- Consumes: `ChatResourceFeedback.EnergySpent`
- Produces: message-local `⚡ -1` animation

- [ ] **Step 1: Track the latest energy event in ViewModel**

Expose the shared flow without delaying it behind HUD refresh:

```kotlin
val resourceFeedback = chatStore.resourceFeedback
```

- [ ] **Step 2: Add the badge component**

```kotlin
@Composable
fun EnergyDeltaBadge(eventId: Long, amount: Int) {
    val alpha = remember { Animatable(0f) }
    val offset = remember { Animatable(8f) }
    LaunchedEffect(eventId) {
        alpha.snapTo(0f)
        offset.snapTo(8f)
        launch { alpha.animateTo(1f, tween(150)) }
        offset.animateTo(0f, tween(150))
        delay(500)
        alpha.animateTo(0f, tween(250))
    }
    Surface(Modifier.alpha(alpha.value).offset(y = offset.value.dp), shape = CircleShape) {
        Text("⚡ $amount", Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
    }
}
```

- [ ] **Step 3: Attach it to the matching user bubble**

Change `MessageBubble` to accept `resourceFeedback`. Wrap the user bubble in `Box` and position the badge at `Alignment.TopEnd`. Only render when `event.messageId == item.id`.

- [ ] **Step 4: Build Android**

Run: `cd apps/frontend && ./gradlew :app:assembleDebug`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/chat
git commit -m "feat(android): show energy spend by user bubble"
```

### Task 4: Android Large Reward Tokens and HUD Pulse

**Files:**
- Replace: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/chat/components/RewardBurstOverlay.kt`
- Create: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/chat/components/ChatRewardToken.kt`
- Modify: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/chat/components/ChatComponents.kt`
- Modify: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/chat/ChatScreen.kt`
- Modify: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/chat/ChatViewModel.kt`

**Interfaces:**
- Consumes: `RewardEarned(messageId, pointDelta, expDelta)`
- Produces: 44dp tokens, measured origin/destinations, HUD pulse

- [ ] **Step 1: Capture coordinates**

Maintain maps of message ID to `LayoutCoordinates` and resource type to HUD center using `onGloballyPositioned`. Convert all positions to root coordinates.

- [ ] **Step 2: Render large tokens**

Implement `ChatRewardToken` with a 44dp minimum surface, 26sp emoji and 16sp bold delta. Animate a quadratic Bézier curve for 1.4 seconds:

```kotlin
val oneMinus = 1f - p
val x = oneMinus * oneMinus * start.x + 2 * oneMinus * p * control.x + p * p * end.x
val y = oneMinus * oneMinus * start.y + 2 * oneMinus * p * control.y + p * p * end.y
```

Start the EXP token after 120ms.

- [ ] **Step 3: Decouple animation from HUD refresh**

On `RewardEarned`, increment the overlay event immediately and launch `hudStore.refreshNow()` concurrently. Feed old/new HUD values into animated chips.

- [ ] **Step 4: Add HUD pulse**

Extend `StatChip` with `pulseTick`. Animate scale `1f → 1.15f → 1f` when each token arrives.

- [ ] **Step 5: Build and manually inspect**

Run: `cd apps/frontend && ./gradlew :shared:testDebugUnitTest :app:assembleDebug`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/chat
git commit -m "feat(android): enlarge chat reward feedback"
```

### Task 5: iOS Energy Badge

**Files:**
- Modify: `apps/frontend/CashChatIOS/CashChatIOS/ChatViewModel.swift`
- Modify: `apps/frontend/CashChatIOS/CashChatIOS/ChatScreen.swift`
- Create: `apps/frontend/CashChatIOS/CashChatIOS/ResourceDeltaBadge.swift`

**Interfaces:**
- Consumes: bridged shared resource feedback
- Produces: user-bubble `⚡ -1`

- [ ] **Step 1: Collect shared events**

Add published event fields:

```swift
@Published var energyFeedback: EnergyFeedback?
@Published var rewardFeedback: RewardFeedback?
```

Map shared subclasses by message ID and event ID in `collectResourceFeedback`.

- [ ] **Step 2: Add `ResourceDeltaBadge`**

Use a 28–32pt capsule with `⚡ -1`, `.transition(.move(edge: .bottom).combined(with: .opacity))`, and remove it after 0.9 seconds keyed by event ID.

- [ ] **Step 3: Attach to user row**

Wrap the user bubble in `ZStack(alignment: .topTrailing)` and offset the badge slightly above the corner. Match `messageId`.

- [ ] **Step 4: Build iOS**

Run:

```bash
cd apps/frontend
xcodebuild -project CashChatIOS/CashChatIOS.xcodeproj -scheme CashChatIOS -sdk iphonesimulator -configuration Debug -derivedDataPath /tmp/cashchat-derived CODE_SIGNING_ALLOWED=NO build
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 5: Commit**

```bash
git add apps/frontend/CashChatIOS/CashChatIOS/ChatViewModel.swift apps/frontend/CashChatIOS/CashChatIOS/ChatScreen.swift apps/frontend/CashChatIOS/CashChatIOS/ResourceDeltaBadge.swift
git commit -m "feat(ios): show energy spend by user bubble"
```

### Task 6: iOS Large Reward Tokens and HUD Pulse

**Files:**
- Replace: `apps/frontend/CashChatIOS/CashChatIOS/RewardBurstOverlay.swift`
- Modify: `apps/frontend/CashChatIOS/CashChatIOS/ChatScreen.swift`
- Modify: `apps/frontend/CashChatIOS/CashChatIOS/ChatViewModel.swift`

**Interfaces:**
- Consumes: `RewardFeedback`
- Produces: coordinate-aware token travel and HUD pulse

- [ ] **Step 1: Add coordinate preferences**

Create `MessageAnchorPreferenceKey` and `HudAnchorPreferenceKey` using `Anchor<CGPoint>` or frames in a named `chatRoot` coordinate space.

- [ ] **Step 2: Replace circles with token capsules**

Render `🪙 +N` and `⭐ +N` at a minimum 44pt height with 26pt icon and 16pt semibold delta. Use `GeometryEffect` or explicit offset interpolation for the curved 1.4-second path.

- [ ] **Step 3: Start immediately and refresh in parallel**

Publish `rewardFeedback` before awaiting `hudStore.refreshNow()`. Trigger point and EXP HUD pulses at their arrival delays.

- [ ] **Step 4: Add reduced motion**

When `accessibilityReduceMotion` is true, show the token at the assistant bubble and crossfade the updated HUD chip without travel.

- [ ] **Step 5: Build iOS**

Run:

```bash
cd apps/frontend
xcodebuild -project CashChatIOS/CashChatIOS.xcodeproj -scheme CashChatIOS -sdk iphonesimulator -configuration Debug -derivedDataPath /tmp/cashchat-derived CODE_SIGNING_ALLOWED=NO build
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 6: Commit**

```bash
git add apps/frontend/CashChatIOS/CashChatIOS/RewardBurstOverlay.swift apps/frontend/CashChatIOS/CashChatIOS/ChatScreen.swift apps/frontend/CashChatIOS/CashChatIOS/ChatViewModel.swift
git commit -m "feat(ios): enlarge chat reward feedback"
```

### Task 7: End-to-End Verification

**Files:**
- Modify only if verification reveals a defect.

- [ ] **Step 1: Run shared tests and Android build**

Run: `cd apps/frontend && ./gradlew :shared:testDebugUnitTest :app:assembleDebug`

Expected: PASS and `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run iOS build**

Run:

```bash
cd apps/frontend
xcodebuild -project CashChatIOS/CashChatIOS.xcodeproj -scheme CashChatIOS -sdk iphonesimulator -configuration Debug -derivedDataPath /tmp/cashchat-derived CODE_SIGNING_ALLOWED=NO build
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 3: Verify behavior on both simulators**

- user request accepted → matching bubble shows `⚡ -1`
- energy insufficient → no `⚡ -1`
- first assistant token does not duplicate energy feedback
- normal completion → one coin and one EXP token
- stream error → no completion reward
- token size remains legible at normal and large text
- offscreen final bubble does not force scroll
- native ad insertion does not become reward origin
- HUD pulses at token arrival
- reduced motion fallback

- [ ] **Step 4: Commit verification fixes**

```bash
git add apps/frontend
git commit -m "fix(chat): address resource feedback verification"
```
