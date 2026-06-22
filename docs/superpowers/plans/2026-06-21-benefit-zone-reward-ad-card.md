# 혜택존 리워드 광고 카드 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 혜택존의 "리워드 광고" placeholder 카드를, 기존 `/api/ads/reward` 흐름을 재사용하는 실제 동작 카드(그라데이션 히어로 디자인)로 교체한다 — Android + iOS.

**Architecture:** 기존 리워드 광고→에너지 적립 흐름의 두 번째 진입점이다. KMM `AdRewardStore`에 `usedToday` baseline 판정 시퀀스를 캡슐화한 `runRewardFlow()` 헬퍼를 추가하고, 혜택존 카드(Android Composable / iOS SwiftUI)가 이를 호출한다. 채팅 보상 경로(`ChatViewModel` 인라인)는 무손상으로 둔다.

**Tech Stack:** Kotlin Multiplatform, Ktor, Jetpack Compose (Material3), Koin, SwiftUI, AdMob Rewarded(SSV). 테스트: kotlin.test + kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-06-21-benefit-zone-reward-ad-card-design.md`

---

## File Structure

| 파일 | 역할 | 작업 |
|---|---|---|
| `shared/src/commonMain/.../ads/AdRewardStore.kt` | `RewardOutcome` enum + `runRewardFlow()` 추가 | Modify |
| `shared/src/commonTest/.../ads/AdRewardStoreTest.kt` | `runRewardFlow` 3 케이스 | Modify |
| `app/src/main/.../feature/rewards/RewardAdCard.kt` | 그라데이션 히어로 Composable + `BenefitRewardViewModel` | Create |
| `app/src/main/.../di/AppModule.kt` | `BenefitRewardViewModel` 등록 | Modify |
| `app/src/main/.../feature/rewards/BenefitZoneScreen.kt` | placeholder → `RewardAdCard` | Modify |
| `CashChatIOS/.../BenefitZone/RewardAdCardView.swift` | iOS 그라데이션 히어로 카드 + VM | Create |
| `CashChatIOS/.../BenefitZoneScreen.swift` | placeholder → `RewardAdCardView` | Modify |

> 경로 접두사: `apps/frontend/`. 작업 디렉토리는 `apps/frontend/`로 두고 `./gradlew` 실행. iOS Swift 빌드(⌘B)는 사용자가 Xcode에서 확인(에이전트 환경 불가).

---

## Task 1: 공유 헬퍼 `AdRewardStore.runRewardFlow`

**Files:**
- Modify: `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/ads/AdRewardStore.kt`
- Test: `apps/frontend/shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/ads/AdRewardStoreTest.kt`

- [ ] **Step 1: 실패하는 테스트 작성**

`AdRewardStoreTest.kt`의 마지막 `}`(클래스 끝) 직전에 아래 3개 테스트를 추가한다. 기존 테스트의 `AdRewardStore(...)` 생성 패턴을 그대로 따른다.

```kotlin
    @Test
    fun `runRewardFlow - 광고 미시청이면 NOT_WATCHED 이고 폴링하지 않는다`() = runTest {
        var fetchCalls = 0
        val store = AdRewardStore(
            fetchQuota = { fetchCalls++; AdRewardQuotaDto(3, 10, 7, "2026-06-11T00:00:00+09:00") },
            issueNonce = { IssueNonceDto("n", "x") },
            scope = this,
            pollDelaysMillis = List(5) { 0L },
        )
        val outcome = store.runRewardFlow(showAd = { false })
        assertEquals(RewardOutcome.NOT_WATCHED, outcome)
        // baseline 조회 1회만 — awaitRewardApplied 폴링은 호출되지 않는다.
        assertEquals(1, fetchCalls)
    }

    @Test
    fun `runRewardFlow - 시청 후 적립 횟수가 늘면 APPLIED`() = runTest {
        var fetchCalls = 0
        val store = AdRewardStore(
            fetchQuota = {
                fetchCalls++
                // 1회차(baseline)=3, 이후 폴링에서 4로 증가 → 적립 관측
                if (fetchCalls >= 2) AdRewardQuotaDto(4, 10, 6, "2026-06-11T00:00:00+09:00")
                else AdRewardQuotaDto(3, 10, 7, "2026-06-11T00:00:00+09:00")
            },
            issueNonce = { IssueNonceDto("n", "x") },
            scope = this,
            pollDelaysMillis = List(5) { 0L },
        )
        assertEquals(RewardOutcome.APPLIED, store.runRewardFlow(showAd = { true }))
    }

    @Test
    fun `runRewardFlow - 시청했으나 적립이 끝까지 안 보이면 PENDING`() = runTest {
        val store = AdRewardStore(
            fetchQuota = { AdRewardQuotaDto(3, 10, 7, "2026-06-11T00:00:00+09:00") },
            issueNonce = { IssueNonceDto("n", "x") },
            scope = this,
            pollDelaysMillis = List(5) { 0L },
        )
        assertEquals(RewardOutcome.PENDING, store.runRewardFlow(showAd = { true }))
    }
```

- [ ] **Step 2: 테스트가 실패(컴파일 에러)하는지 확인**

Run: `cd apps/frontend && ./gradlew :shared:testDebugUnitTest --tests "*.AdRewardStoreTest"`
Expected: FAIL — `RewardOutcome` / `runRewardFlow` 미정의로 컴파일 실패.

- [ ] **Step 3: 최소 구현 추가**

`AdRewardStore.kt`에서 `class AdRewardStore(` 선언 **직전**(파일 상단 import 아래)에 enum을 추가한다:

```kotlin
/** 혜택존 카드·채팅 게이트가 공유하는 보상 플로우 결과. */
enum class RewardOutcome { APPLIED, PENDING, NOT_WATCHED }
```

그리고 `AdRewardStore` 클래스 본문 안, `awaitRewardApplied(...)` 함수 **아래**에 헬퍼를 추가한다:

```kotlin
    /**
     * 보상 플로우 한 사이클: quota baseline 확보 → nonce 발급 → 광고 표시(콜백) → 적립 폴링.
     * 채팅 게이트와 혜택존 카드의 공통 진입점. usedToday baseline 판정으로 패시브 회복과 광고 보상을 격리한다.
     * @param showAd nonce 를 받아 광고를 표시하고, 끝까지 시청(보상 적립 콜백)했으면 true 를 반환하는 호출자 콜백.
     */
    @Throws(Exception::class)
    suspend fun runRewardFlow(showAd: suspend (nonce: String) -> Boolean): RewardOutcome {
        val baseline = refreshQuota().usedToday
        val nonce = requestNonce()
        val watched = showAd(nonce)
        if (!watched) return RewardOutcome.NOT_WATCHED
        return if (awaitRewardApplied(baseline)) RewardOutcome.APPLIED else RewardOutcome.PENDING
    }
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd apps/frontend && ./gradlew :shared:testDebugUnitTest --tests "*.AdRewardStoreTest"`
Expected: PASS (기존 3 + 신규 3 = 6 테스트).

- [ ] **Step 5: 커밋**

```bash
git add apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/ads/AdRewardStore.kt \
        apps/frontend/shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/ads/AdRewardStoreTest.kt
git commit -m "feat(ads): AdRewardStore.runRewardFlow 공유 보상 플로우 헬퍼 추가"
```

---

## Task 2: Android `BenefitRewardViewModel` + `RewardAdCard` Composable

**Files:**
- Create: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/rewards/RewardAdCard.kt`
- Modify: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/di/AppModule.kt:68` 부근(viewModel 등록 블록)

- [ ] **Step 1: `RewardAdCard.kt` 생성 (ViewModel + Composable)**

전체 파일을 아래 내용으로 생성한다.

```kotlin
package com.nomadclub.cashchat.feature.rewards

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nomadclub.cashchat.ads.RewardedAdManager
import com.nomadclub.cashchat.shared.ads.AdRewardStore
import com.nomadclub.cashchat.shared.ads.RewardOutcome
import com.nomadclub.cashchat.shared.hud.HudStore
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

/** 혜택존 리워드 광고 카드 상태 홀더. 채팅 경로와 무관하게 독립 동작한다. */
class BenefitRewardViewModel(
    val adRewardStore: AdRewardStore,
    private val hudStore: HudStore,
) : ViewModel() {

    enum class Phase { IDLE, BUSY }

    private val _phase = MutableStateFlow(Phase.IDLE)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toast: SharedFlow<String> = _toast.asSharedFlow()

    val quota = adRewardStore.quota

    fun loadQuota() {
        viewModelScope.launch { runCatching { adRewardStore.refreshQuota() } }
    }

    /** showAd: nonce 를 받아 광고를 표시하고 끝까지 시청했으면 true 를 반환. */
    fun watchAd(showAd: suspend (nonce: String) -> Boolean) {
        if (_phase.value != Phase.IDLE) return
        viewModelScope.launch {
            _phase.value = Phase.BUSY
            val outcome = runCatching { adRewardStore.runRewardFlow(showAd) }
                .getOrDefault(RewardOutcome.NOT_WATCHED)
            runCatching { hudStore.refreshEnergyOnly() }
            runCatching { adRewardStore.refreshQuota() }
            when (outcome) {
                RewardOutcome.APPLIED -> _toast.tryEmit("에너지를 충전했어요!")
                RewardOutcome.PENDING -> _toast.tryEmit("보상 확인 중이에요. 잠시 후 다시 확인해주세요")
                RewardOutcome.NOT_WATCHED -> {}
            }
            _phase.value = Phase.IDLE
        }
    }
}

@Composable
fun RewardAdCard(
    modifier: Modifier = Modifier,
    vm: BenefitRewardViewModel = koinViewModel(),
    adManager: RewardedAdManager = koinInject(),
) {
    val quota by vm.quota.collectAsState()
    val phase by vm.phase.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        adManager.preload(context)
        vm.loadQuota()
    }
    LaunchedEffect(Unit) {
        vm.toast.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    val remaining = quota?.remaining
    val limitReached = remaining == 0
    val busy = phase == BenefitRewardViewModel.Phase.BUSY

    val gradient = if (limitReached) {
        Brush.linearGradient(listOf(Color(0xFFBFA9A0), Color(0xFFA89AA0)))
    } else {
        Brush.linearGradient(listOf(Color(0xFFFF8A4C), Color(0xFFFF5E8A)))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(gradient)
            .clickable(enabled = !limitReached && !busy) {
                val activity = context as? Activity ?: return@clickable
                vm.watchAd { nonce ->
                    suspendCancellableCoroutine { cont ->
                        var rewarded = false
                        adManager.show(
                            activity = activity,
                            nonce = nonce,
                            onRewarded = { rewarded = true },
                            onDismissed = { if (cont.isActive) cont.resume(rewarded) },
                            onNotReady = {
                                if (cont.isActive) {
                                    Toast.makeText(
                                        context,
                                        "광고를 준비 중이에요. 잠시 후 다시 시도해주세요.",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    cont.resume(false)
                                }
                            },
                        )
                    }
                }
            }
            .padding(16.dp),
    ) {
        // 우상단 한도 배지
        Text(
            text = when {
                remaining == null -> "불러오는 중…"
                limitReached -> "오늘 한도 도달 · 자정 리셋"
                else -> "오늘 ${remaining}회 남음"
            },
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .clip(RoundedCornerShape(99.dp))
                .background(Color.White.copy(alpha = 0.22f))
                .padding(horizontal = 9.dp, vertical = 4.dp),
        )

        Column {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) { Text("⚡", fontSize = 20.sp) }

            Spacer(Modifier.height(10.dp))
            Text("리워드 광고", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Spacer(Modifier.height(3.dp))
            Text(
                "광고 보고 에너지 충전하기",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.92f),
            )

            Spacer(Modifier.height(13.dp))
            // 흰색 CTA 필
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(Color.White.copy(alpha = if (limitReached) 0.5f else 1f))
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFFFF5E8A))
                        Text("보상 확인 중...", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF5E8A))
                    }
                } else {
                    Text(
                        if (limitReached) "내일 다시 만나요" else "▶  광고 보기",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF5E8A),
                    )
                }
            }
        }
    }
}
```

> 참고: `koinViewModel`은 `org.koin.androidx.compose.koinViewModel` 사용(이미 의존성 있음 — 다른 화면에서 사용 중인지 확인하고, 없으면 `import androidx.lifecycle.viewmodel.compose.viewModel` 대신 koin 방식 유지). `BenefitInfoCard.kt`와 같은 패키지에 둔다.

- [ ] **Step 2: Koin에 ViewModel 등록**

`AppModule.kt`의 `viewModel { ... }` 블록(68행 부근, `ChatViewModel` 등록 근처)에 추가:

```kotlin
    viewModel { com.nomadclub.cashchat.feature.rewards.BenefitRewardViewModel(get(), get()) }
```

(`get()` 2개 = `AdRewardStore`, `HudStore` — 둘 다 `SharedModule`에 single 등록되어 있음.)

- [ ] **Step 3: 컴파일 확인**

Run: `cd apps/frontend && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (실패 시 `koinViewModel` import 경로 또는 `get()` 개수 점검.)

- [ ] **Step 4: 커밋**

```bash
git add apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/rewards/RewardAdCard.kt \
        apps/frontend/app/src/main/java/com/nomadclub/cashchat/di/AppModule.kt
git commit -m "feat(ads): android 혜택존 리워드 광고 카드 Composable·ViewModel 추가"
```

---

## Task 3: Android `BenefitZoneScreen` 연동

**Files:**
- Modify: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/rewards/BenefitZoneScreen.kt:117-124`

- [ ] **Step 1: placeholder 카드 교체**

`BenefitZoneScreen.kt`에서 리워드 광고 `BenefitInfoCard` item(현재 `icon = "📺", title = "리워드 광고"`, `onClick = "곧 만나요!" 토스트`)을 아래로 교체한다:

```kotlin
            item { RewardAdCard() }
```

(같은 패키지이므로 추가 import 불필요. `📺` BenefitInfoCard 블록 전체 — `item { BenefitInfoCard( icon = "📺", ... ) }` — 를 위 한 줄로 대체.)

- [ ] **Step 2: 빌드 확인**

Run: `cd apps/frontend && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

> jlink 실패 시 메모리 노트대로 JBR로 JAVA_HOME 지정.

- [ ] **Step 3: (선택) 에뮬레이터 육안 확인**

`local.properties`에 테스트 리워드 광고 단위 ID가 있는 Play Store 포함 AVD에서 실행 → 혜택존에 그라데이션 카드 노출, 탭 시 테스트 광고 표시 → 닫으면 "에너지를 충전했어요!" 토스트, 배지 "오늘 N회 남음" 감소 확인.

- [ ] **Step 4: 커밋**

```bash
git add apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/rewards/BenefitZoneScreen.kt
git commit -m "feat(ads): android 혜택존에 리워드 광고 카드 연동"
```

---

## Task 4: iOS `RewardAdCardView` + VM

**Files:**
- Create: `apps/frontend/CashChatIOS/CashChatIOS/BenefitZone/RewardAdCardView.swift`

> iOS는 새 `.swift` 파일이 Xcode 동기화 그룹으로 자동 포함됨(메모리 노트 참고). Swift 빌드(⌘B)는 사용자가 확인.

- [ ] **Step 1: `RewardAdCardView.swift` 생성**

```swift
import SwiftUI
import shared

/// 혜택존 리워드 광고 카드 상태 홀더. 채팅 경로와 무관하게 독립 동작.
@MainActor
final class RewardAdCardViewModel: ObservableObject {
    @Published var remaining: Int? = nil
    @Published var busy = false
    @Published var toast: String? = nil

    private let adRewardStore = KoinHelper().adRewardStore()
    private let hudStore = KoinHelper().hudStore()
    private let adManager = RewardedAdManager()

    func onAppear() {
        adManager.preload()
        Task { await loadQuota() }
    }

    func loadQuota() async {
        if let q = try? await adRewardStore.refreshQuota() {
            remaining = Int(q.remaining)
        }
    }

    func watchAd() {
        guard !busy else { return }
        busy = true
        Task { @MainActor in
            let outcome = try? await adRewardStore.runRewardFlow(showAd: { [adManager] nonce in
                await withCheckedContinuation { cont in
                    var rewarded = false
                    adManager.show(
                        nonce: nonce,
                        onRewarded: { _ in rewarded = true },
                        onDismissed: { cont.resume(returning: KotlinBoolean(bool: rewarded)) },
                        onNotReady: { cont.resume(returning: KotlinBoolean(bool: false)) }
                    )
                }
            })
            try? await hudStore.refreshEnergyOnly()
            await loadQuota()
            switch outcome {
            case RewardOutcome.applied: toast = "에너지를 충전했어요!"
            case RewardOutcome.pending: toast = "보상 확인 중이에요. 잠시 후 다시 확인해주세요"
            default: break
            }
            busy = false
        }
    }
}

struct RewardAdCardView: View {
    @StateObject private var vm = RewardAdCardViewModel()

    private var limitReached: Bool { vm.remaining == 0 }

    private var gradient: LinearGradient {
        let colors = limitReached
            ? [Color(red: 0.75, green: 0.66, blue: 0.63), Color(red: 0.66, green: 0.60, blue: 0.63)]
            : [Color(red: 1.0, green: 0.54, blue: 0.30), Color(red: 1.0, green: 0.37, blue: 0.54)]
        return LinearGradient(colors: colors, startPoint: .topLeading, endPoint: .bottomTrailing)
    }

    private var badgeText: String {
        if vm.remaining == nil { return "불러오는 중…" }
        return limitReached ? "오늘 한도 도달 · 자정 리셋" : "오늘 \(vm.remaining!)회 남음"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                ZStack {
                    RoundedRectangle(cornerRadius: 12).fill(.white.opacity(0.25)).frame(width: 40, height: 40)
                    Text("⚡").font(.system(size: 20))
                }
                Spacer()
                Text(badgeText)
                    .font(.system(size: 10, weight: .bold)).foregroundStyle(.white)
                    .padding(.horizontal, 9).padding(.vertical, 4)
                    .background(.white.opacity(0.22)).clipShape(Capsule())
            }
            Text("리워드 광고").font(.system(size: 16, weight: .heavy)).foregroundStyle(.white).padding(.top, 10)
            Text("광고 보고 에너지 충전하기").font(.system(size: 12, weight: .medium)).foregroundStyle(.white.opacity(0.92)).padding(.top, 3)

            ZStack {
                RoundedRectangle(cornerRadius: 11).fill(.white.opacity(limitReached ? 0.5 : 1.0))
                if vm.busy {
                    HStack(spacing: 8) {
                        ProgressView().tint(Color(red: 1.0, green: 0.37, blue: 0.54))
                        Text("보상 확인 중...").font(.system(size: 13, weight: .bold))
                    }.foregroundStyle(Color(red: 1.0, green: 0.37, blue: 0.54))
                } else {
                    Text(limitReached ? "내일 다시 만나요" : "▶  광고 보기")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(Color(red: 1.0, green: 0.37, blue: 0.54))
                }
            }
            .frame(height: 40).padding(.top, 13)
        }
        .padding(16)
        .background(gradient)
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .contentShape(Rectangle())
        .onTapGesture { if !limitReached && !vm.busy { vm.watchAd() } }
        .onAppear { vm.onAppear() }
    }
}
```

> **주의:** `RewardOutcome.applied/.pending` 케이스명은 KMP가 export하는 실제 enum 케이스명으로 확정 필요(Xcode 자동완성으로 확인 — KMP는 보통 `.applied` camelCase로 노출). `KoinHelper().adRewardStore()/hudStore()`는 iOS `ChatViewModel.swift`에서 이미 사용 중이라 노출 보장됨. `KotlinBoolean(bool:)` 변환도 기존 iOS 코드 패턴 확인 후 동일하게 사용.

- [ ] **Step 2: shared 프레임워크 재빌드 (헤더에 runRewardFlow/RewardOutcome 반영)**

Run: `cd apps/frontend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && ./gradlew :shared:embedAndSignAppleFrameworkForXcode`
Expected: BUILD SUCCESSFUL. (생성 헤더 `CashChatShared.h`에 `runRewardFlow`, `RewardOutcome` 심볼 포함.)

- [ ] **Step 3: 커밋**

```bash
git add apps/frontend/CashChatIOS/CashChatIOS/BenefitZone/RewardAdCardView.swift
git commit -m "feat(ads): ios 혜택존 리워드 광고 카드 뷰·뷰모델 추가"
```

---

## Task 5: iOS `BenefitZoneScreen.swift` 연동

**Files:**
- Modify: `apps/frontend/CashChatIOS/CashChatIOS/BenefitZoneScreen.swift:33-35`

- [ ] **Step 1: placeholder 카드 교체**

`BenefitZoneScreen.swift`에서 리워드 광고 `BenefitInfoCardView`(`icon: "tv.fill", title: "리워드 광고", ...`) 블록을 아래로 교체:

```swift
                RewardAdCardView()
                    .padding(.horizontal, 16)
```

- [ ] **Step 2: 카드 토스트를 화면 토스트로 연결 (선택, 권장)**

`RewardAdCardView`의 보상 토스트를 기존 혜택존 하단 토스트(`attendanceVM.toast`)와 일관되게 표시하려면, `RewardAdCardView(onToast: { attendanceVM.toast = $0 })` 형태로 클로저를 주입하고 VM의 `toast` 변경 시 호출하도록 바꾼다. 범위를 줄이려면 Step 1만 적용하고 카드 내부 토스트는 생략(에너지는 다음 채팅에서 HUD 반영). **결정 기본값: Step 1만 — 카드 자체 피드백은 CTA 상태(보상 확인 중)로 충분.**

- [ ] **Step 3: 사용자 Xcode 빌드 확인**

Xcode에서 ⌘B → 빌드 성공 및 시뮬레이터에서 혜택존 그라데이션 카드 노출·광고 표시·"오늘 N회 남음" 갱신 확인. (에이전트는 shared 빌드까지만 검증.)

- [ ] **Step 4: 커밋**

```bash
git add apps/frontend/CashChatIOS/CashChatIOS/BenefitZoneScreen.swift
git commit -m "feat(ads): ios 혜택존에 리워드 광고 카드 연동"
```

---

## Self-Review 체크

- **Spec coverage:** §3.1 헬퍼=Task1, §3.2 디자인=Task2/4, §3.3 Android=Task2/3, §3.4 iOS=Task4/5, §4 카피=Task2/4, §5 테스트=Task1. 누락 없음.
- **Type consistency:** `RewardOutcome{APPLIED,PENDING,NOT_WATCHED}` (Task1) ↔ Android `when` (Task2) ↔ iOS `switch` (Task4) 일치. `runRewardFlow(showAd: suspend (nonce)->Boolean): RewardOutcome` 시그니처 동일. `refreshEnergyOnly()`/`refreshQuota()`/`quota` 모두 기존 정의 사용.
- **Placeholder scan:** iOS Swift 케이스명(`RewardOutcome.applied` 등)·전각 괄호는 Step1 주의문에 명시(빌드 시 확정). 그 외 placeholder 없음.

## 후속

- 채팅 `ChatViewModel` 인라인 시퀀스를 `runRewardFlow`로 흡수하는 정리 슬라이스(선택, 별도).
