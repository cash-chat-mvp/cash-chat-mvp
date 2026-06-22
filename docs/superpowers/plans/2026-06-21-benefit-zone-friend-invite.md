# 친구 초대 (FE-first 스텁) 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 혜택존에 "친구 초대"(추천 코드)를 FE-first 스텁으로 추가한다 — 내 코드 공유(OS 공유시트) + 추천 코드 입력(혜택존 + 온보딩), 초대자 코인·가입자 에너지 보상. (Android + iOS)

**Architecture:** 공유 KMM에 `InviteRepository` 인터페이스 + `FakeInviteRepository`(로컬 스텁) + `InviteStore`(상태 + redeem 오케스트레이션). 실제 코드 발급·보상 지급은 BE 몫(스텁은 UI 검증용). `PointsRepository` Local→Remote 격리 패턴을 따른다.

**Tech Stack:** Kotlin Multiplatform, Koin, Jetpack Compose, SwiftUI(ShareLink), kotlin.test. iOS 빌드는 에이전트가 `xcodebuild`로 직접 검증.

**Spec:** `docs/superpowers/specs/2026-06-21-benefit-zone-friend-invite-design.md`
**BE 계약:** `docs/planning/be-api-requests-cc355.md` §5

---

## File Structure

| 파일 | 역할 | 작업 |
|---|---|---|
| `shared/.../invite/InviteModels.kt` | `InviteStatus`/`RedeemResult` | Create |
| `shared/.../invite/InviteRepository.kt` | 인터페이스 | Create |
| `shared/.../invite/FakeInviteRepository.kt` | 로컬 스텁 | Create |
| `shared/.../invite/InviteStore.kt` | 상태 + redeem 오케스트레이션 | Create |
| `shared/commonTest/.../invite/FakeInviteRepositoryTest.kt` | 스텁 테스트 | Create |
| `shared/commonTest/.../invite/InviteStoreTest.kt` | 스토어 테스트 | Create |
| `shared/.../di/SharedModule.kt` | Koin 등록 | Modify |
| `shared/.../di/IosBridges.kt` | `inviteStore()` + `collectInviteStatus` | Modify |
| `app/.../feature/rewards/InviteScreen.kt` | 초대 화면 Composable + VM | Create |
| `app/.../di/AppModule.kt` | `InviteViewModel` 등록 | Modify |
| `app/.../feature/rewards/BenefitZoneScreen.kt` | 친구 초대 진입 카드 | Modify |
| `CashChatIOS/.../BenefitZone/InviteView.swift` | iOS 초대 화면 + VM | Create |
| `CashChatIOS/.../BenefitZoneScreen.swift` | 친구 초대 진입 카드 | Modify |
| `app/.../feature/onboarding/OnboardingScreen.kt` | 추천 코드 입력 필드 | Modify |
| `CashChatIOS/.../ContentView.swift` | 추천 코드 입력 필드 | Modify |

> 경로 접두사 `apps/frontend/`. 패키지 `com.nomadclub.cashchat.shared.invite`. iOS 빌드: `:shared:linkDebugFrameworkIosSimulatorArm64` 후 `xcodebuild -scheme CashChatIOS -destination 'platform=iOS Simulator,name=iPhone 16'`.

---

## Task 1: 공유 모델 + Repository + Fake 스텁

**Files:**
- Create: `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/invite/InviteModels.kt`
- Create: `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/invite/InviteRepository.kt`
- Create: `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/invite/FakeInviteRepository.kt`
- Test: `apps/frontend/shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/invite/FakeInviteRepositoryTest.kt`

- [ ] **Step 1: 모델 + 인터페이스 작성**

`InviteModels.kt`:
```kotlin
package com.nomadclub.cashchat.shared.invite

/** 친구 초대 상태(서버가 진실, 스텁이 모사). 금액·한도는 서버 설정값. */
data class InviteStatus(
    val myCode: String,
    val invitedCount: Int,
    val redeemAvailable: Boolean,
    val rewardCoin: Int,
    val rewardEnergy: Int,
)

/** 추천 코드 입력 결과. */
data class RedeemResult(val success: Boolean, val awardedEnergy: Int, val message: String?)
```

`InviteRepository.kt`:
```kotlin
package com.nomadclub.cashchat.shared.invite

/**
 * 친구 초대 데이터 소스. 지금은 FakeInviteRepository(로컬 스텁), BE 준비 시 RemoteInviteRepository 로 교체.
 * iOS 에서 호출하므로 suspend 는 @Throws.
 */
interface InviteRepository {
    @Throws(Exception::class) suspend fun getInviteStatus(): InviteStatus
    @Throws(Exception::class) suspend fun redeemCode(code: String): RedeemResult
}
```

- [ ] **Step 2: 실패 테스트 작성**

`FakeInviteRepositoryTest.kt`:
```kotlin
package com.nomadclub.cashchat.shared.invite

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FakeInviteRepositoryTest {

    @Test
    fun `getInviteStatus 는 내 코드와 보상값을 준다`() = runTest {
        val status = FakeInviteRepository().getInviteStatus()
        assertEquals("ABC123", status.myCode)
        assertTrue(status.redeemAvailable)
        assertEquals(500, status.rewardCoin)
        assertEquals(10, status.rewardEnergy)
    }

    @Test
    fun `redeemCode - 유효한 코드면 성공하고 에너지 지급`() = runTest {
        val repo = FakeInviteRepository()
        val result = repo.redeemCode("XYZ789")
        assertTrue(result.success)
        assertEquals(10, result.awardedEnergy)
    }

    @Test
    fun `redeemCode - 성공 후 redeemAvailable 가 false`() = runTest {
        val repo = FakeInviteRepository()
        repo.redeemCode("XYZ789")
        assertFalse(repo.getInviteStatus().redeemAvailable)
    }

    @Test
    fun `redeemCode - 자기 코드면 실패`() = runTest {
        val repo = FakeInviteRepository()
        val result = repo.redeemCode("ABC123")
        assertFalse(result.success)
        assertEquals("본인 코드는 사용할 수 없어요", result.message)
    }

    @Test
    fun `redeemCode - 빈 코드면 실패`() = runTest {
        val result = FakeInviteRepository().redeemCode("   ")
        assertFalse(result.success)
        assertEquals("코드를 입력해주세요", result.message)
    }

    @Test
    fun `redeemCode - 이미 사용했으면 실패`() = runTest {
        val repo = FakeInviteRepository()
        repo.redeemCode("XYZ789")
        val result = repo.redeemCode("QWE456")
        assertFalse(result.success)
        assertEquals("이미 추천 코드를 사용했어요", result.message)
    }
}
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `cd apps/frontend && ./gradlew :shared:testDebugUnitTest --tests "*.FakeInviteRepositoryTest"`
Expected: FAIL(컴파일 에러, `FakeInviteRepository` 미정의).

- [ ] **Step 4: `FakeInviteRepository.kt` 구현**

```kotlin
package com.nomadclub.cashchat.shared.invite

/**
 * 로컬 스텁. 고정 코드·보상값 보유. redeemCode 는 형식·자기코드·중복만 검증하고 성공/실패를 모사한다.
 * 실제 적립·서버 검증은 BE 몫 — UI 검증용.
 */
class FakeInviteRepository : InviteRepository {

    private companion object {
        const val MY_CODE = "ABC123"
        const val REWARD_COIN = 500
        const val REWARD_ENERGY = 10
    }

    private var redeemed = false

    override suspend fun getInviteStatus(): InviteStatus = InviteStatus(
        myCode = MY_CODE,
        invitedCount = 3,
        redeemAvailable = !redeemed,
        rewardCoin = REWARD_COIN,
        rewardEnergy = REWARD_ENERGY,
    )

    override suspend fun redeemCode(code: String): RedeemResult {
        val trimmed = code.trim()
        return when {
            trimmed.isEmpty() -> RedeemResult(false, 0, "코드를 입력해주세요")
            trimmed.equals(MY_CODE, ignoreCase = true) -> RedeemResult(false, 0, "본인 코드는 사용할 수 없어요")
            redeemed -> RedeemResult(false, 0, "이미 추천 코드를 사용했어요")
            else -> {
                redeemed = true
                RedeemResult(true, REWARD_ENERGY, null)
            }
        }
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `cd apps/frontend && ./gradlew :shared:testDebugUnitTest --tests "*.FakeInviteRepositoryTest"`
Expected: PASS (6 테스트).

- [ ] **Step 6: 커밋**

```bash
git add apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/invite/ apps/frontend/shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/invite/
git commit -m "feat(invite): 친구 초대 모델·Repository·Fake 스텁 추가 (shared)"
```

---

## Task 2: `InviteStore`

**Files:**
- Create: `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/invite/InviteStore.kt`
- Test: `apps/frontend/shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/invite/InviteStoreTest.kt`

- [ ] **Step 1: 실패 테스트 작성**

`InviteStoreTest.kt`:
```kotlin
package com.nomadclub.cashchat.shared.invite

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InviteStoreTest {

    @Test
    fun `refresh 는 status 를 채운다`() = runTest {
        val store = InviteStore(FakeInviteRepository(), onRewardChanged = {})
        assertEquals(null, store.status.value)
        store.refresh()
        assertEquals("ABC123", store.status.value?.myCode)
    }

    @Test
    fun `redeem 성공 시 보상 콜백 호출·status 갱신`() = runTest {
        var rewardRefreshed = 0
        val store = InviteStore(FakeInviteRepository(), onRewardChanged = { rewardRefreshed++ })
        val result = store.redeem("XYZ789")
        assertTrue(result.success)
        assertEquals(1, rewardRefreshed)
        assertFalse(store.status.value?.redeemAvailable ?: true)
    }

    @Test
    fun `redeem 실패 시 보상 콜백 미호출`() = runTest {
        var rewardRefreshed = 0
        val store = InviteStore(FakeInviteRepository(), onRewardChanged = { rewardRefreshed++ })
        val result = store.redeem("ABC123") // 자기 코드
        assertFalse(result.success)
        assertEquals(0, rewardRefreshed)
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd apps/frontend && ./gradlew :shared:testDebugUnitTest --tests "*.InviteStoreTest"`
Expected: FAIL(`InviteStore` 미정의).

- [ ] **Step 3: `InviteStore.kt` 구현**

```kotlin
package com.nomadclub.cashchat.shared.invite

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 친구 초대 상태 보유 + redeem 오케스트레이션.
 * @param onRewardChanged redeem 성공 시 잔액/에너지가 바뀌었을 수 있어 HUD 등을 갱신하는 콜백.
 * iOS 에서 호출하므로 suspend 는 @Throws.
 */
class InviteStore(
    private val repo: InviteRepository,
    private val onRewardChanged: suspend () -> Unit,
) {
    private val _status = MutableStateFlow<InviteStatus?>(null)
    val status: StateFlow<InviteStatus?> = _status.asStateFlow()

    @Throws(Exception::class)
    suspend fun refresh(): InviteStatus = repo.getInviteStatus().also { _status.value = it }

    /** 추천 코드 입력. 성공 시 보상 콜백 + status 갱신. */
    @Throws(Exception::class)
    suspend fun redeem(code: String): RedeemResult {
        val result = repo.redeemCode(code)
        if (result.success) {
            onRewardChanged()
            _status.value = repo.getInviteStatus()
        }
        return result
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd apps/frontend && ./gradlew :shared:testDebugUnitTest --tests "*.InviteStoreTest"`
Expected: PASS (3 테스트).

- [ ] **Step 5: 커밋**

```bash
git add apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/invite/InviteStore.kt apps/frontend/shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/invite/InviteStoreTest.kt
git commit -m "feat(invite): inviteStore redeem 오케스트레이션 추가 (shared)"
```

---

## Task 3: 공유 DI 등록 (SharedModule + IosBridges)

**Files:**
- Modify: `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/di/SharedModule.kt`
- Modify: `apps/frontend/shared/src/iosMain/kotlin/com/nomadclub/cashchat/shared/di/IosBridges.kt`

- [ ] **Step 1: SharedModule 등록**

`SharedModule.kt`의 `single { RouletteStore(...) }` 블록 **아래**(HudStore 가 먼저 등록돼 있어야 함)에 추가:
```kotlin
    single<com.nomadclub.cashchat.shared.invite.InviteRepository> {
        com.nomadclub.cashchat.shared.invite.FakeInviteRepository()
    }
    single {
        val hud = get<HudStore>()
        com.nomadclub.cashchat.shared.invite.InviteStore(
            repo = get(),
            onRewardChanged = { hud.refreshEnergyOnly() },
        )
    }
```

- [ ] **Step 2: IosBridges 노출**

`IosBridges.kt`에서:
- import 추가:
```kotlin
import com.nomadclub.cashchat.shared.invite.InviteStore
import com.nomadclub.cashchat.shared.invite.InviteStatus
```
- `KoinHelper` 클래스에서 `private val roulette: RouletteStore by inject()` 아래에:
```kotlin
    private val invite: InviteStore by inject()
```
- `fun rouletteStore(): RouletteStore = roulette` 아래에:
```kotlin
    fun inviteStore(): InviteStore = invite
```
- `FlowCollector` 의 `collectRouletteStatus(...)` 아래에:
```kotlin
    fun collectInviteStatus(store: InviteStore, onEach: (InviteStatus?) -> Unit) {
        scope.launch { store.status.collect { onEach(it) } }
    }
```

- [ ] **Step 3: 빌드 확인 (Android + iOS 타깃)**

Run: `cd apps/frontend && ./gradlew :shared:compileKotlinMetadata :app:compileDebugKotlin :shared:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: 커밋**

```bash
git add apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/di/SharedModule.kt apps/frontend/shared/src/iosMain/kotlin/com/nomadclub/cashchat/shared/di/IosBridges.kt
git commit -m "feat(invite): inviteStore Koin 등록·iOS 브릿지 노출"
```

---

## Task 4: Android 초대 화면 + 진입 카드

**Files:**
- Create: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/rewards/InviteScreen.kt`
- Modify: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/di/AppModule.kt`
- Modify: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/rewards/BenefitZoneScreen.kt`

- [ ] **Step 1: `InviteScreen.kt` 작성 (VM + 다이얼로그 UI, 그라데이션 히어로)**

```kotlin
package com.nomadclub.cashchat.feature.rewards

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nomadclub.cashchat.shared.invite.InviteStatus
import com.nomadclub.cashchat.shared.invite.InviteStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

class InviteViewModel(private val store: InviteStore) : ViewModel() {
    val status: StateFlow<InviteStatus?> = store.status
    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toast: SharedFlow<String> = _toast.asSharedFlow()
    var submitting by androidx.compose.runtime.mutableStateOf(false)
        private set

    fun load() { viewModelScope.launch { runCatching { store.refresh() } } }

    fun redeem(code: String) {
        if (submitting) return
        viewModelScope.launch {
            submitting = true
            val result = runCatching { store.redeem(code) }.getOrNull()
            _toast.tryEmit(
                when {
                    result == null -> "잠시 후 다시 시도해주세요"
                    result.success -> "⚡${result.awardedEnergy} 에너지를 받았어요!"
                    else -> result.message ?: "코드를 확인해주세요"
                }
            )
            submitting = false
        }
    }
}

@androidx.compose.runtime.Composable
fun InviteDialog(
    onDismiss: () -> Unit,
    vm: InviteViewModel = koinViewModel(),
) {
    val status by vm.status.collectAsState()
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(vm) { vm.toast.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.92f).clip(RoundedCornerShape(24.dp)).background(Color(0xFFF6F5FA)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            // 그라데이션 히어로
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF7C6CFF), Color(0xFFFF5E8A)))).padding(18.dp),
            ) {
                Text("친구 초대하고 코인 받기 🎁", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                status?.let {
                    Text("친구가 가입하면 나는 🪙+${it.rewardCoin}, 친구는 ⚡+${it.rewardEnergy}!",
                        color = Color.White.copy(alpha = 0.92f), fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
                }
            }
            // 내 코드 카드
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(15.dp)) {
                Text("내 추천 코드", fontSize = 12.sp, color = Color(0xFF6B6979))
                Text(status?.myCode ?: "-", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1B1B2A),
                    modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        val code = status?.myCode ?: return@Button
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "캐시챗에서 만나요! 추천코드 [$code] 입력하면 에너지를 드려요 ⚡")
                        }
                        context.startActivity(Intent.createChooser(send, null))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("친구에게 공유하기") }
                status?.let {
                    Text("지금까지 ${it.invitedCount}명 초대", fontSize = 12.sp, color = Color(0xFF6B6979),
                        modifier = Modifier.padding(top = 9.dp))
                }
            }
            // 추천 코드 입력 카드
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(15.dp)) {
                val available = status?.redeemAvailable ?: false
                Text(if (available) "추천 코드 입력" else "이미 추천 코드를 사용했어요",
                    fontSize = 12.sp, color = Color(0xFF6B6979))
                if (available) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = input, onValueChange = { input = it.uppercase() },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        placeholder = { Text("코드 입력") },
                    )
                    Spacer(Modifier.height(9.dp))
                    Button(onClick = { vm.redeem(input) }, enabled = !vm.submitting && input.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()) { Text("에너지 받기") }
                }
            }
            Text("닫기", color = Color(0xFF9A95AD), fontSize = 13.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(8.dp)).clickable { onDismiss() }.padding(8.dp))
        }
    }
}
```

- [ ] **Step 2: AppModule 등록**

`AppModule.kt`의 `viewModel { ... }` 블록에 추가:
```kotlin
    viewModel { com.nomadclub.cashchat.feature.rewards.InviteViewModel(get()) }
```

- [ ] **Step 3: 혜택존 진입 카드 추가**

`BenefitZoneScreen.kt`에서, 상단 상태에 추가:
```kotlin
    var showInvite by remember { mutableStateOf(false) }
```
룰렛 진입 카드 `item { ... 행운 룰렛 ... }` **다음**에:
```kotlin
            item {
                BenefitInfoCard(
                    icon = "🤝", title = "친구 초대", badge = BenefitBadge.NEXT,
                    description = "친구가 가입하면 나는 코인, 친구는 에너지!",
                    dimmed = false,
                    onClick = { showInvite = true },
                )
            }
```
`LazyColumn` 뒤(PullToRefreshBox 안), 룰렛 다이얼로그 옆에:
```kotlin
        if (showInvite) {
            InviteDialog(onDismiss = { showInvite = false })
        }
```

- [ ] **Step 4: 빌드**

Run: `cd apps/frontend && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. (jlink 실패 시 JBR/JDK21.)

- [ ] **Step 5: 커밋**

```bash
git add apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/rewards/InviteScreen.kt apps/frontend/app/src/main/java/com/nomadclub/cashchat/di/AppModule.kt apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/rewards/BenefitZoneScreen.kt
git commit -m "feat(invite): android 친구 초대 화면·혜택존 진입 카드 추가"
```

---

## Task 5: iOS 초대 화면 + 진입 카드

**Files:**
- Create: `apps/frontend/CashChatIOS/CashChatIOS/BenefitZone/InviteView.swift`
- Modify: `apps/frontend/CashChatIOS/CashChatIOS/BenefitZoneScreen.swift`

- [ ] **Step 1: `InviteView.swift` 작성**

```swift
import SwiftUI
import Combine
import CashChatShared

@MainActor
final class InviteViewModel: ObservableObject {
    @Published var status: InviteStatus? = nil
    @Published var submitting = false
    @Published var toast: String? = nil

    private let store = KoinHelper().inviteStore()
    private let collector = FlowCollector()

    func onAppear() {
        collector.collectInviteStatus(store: store) { [weak self] s in self?.status = s }
        Task { try? await store.refresh() }
    }
    func onDisappear() { collector.cancel() }

    func redeem(_ code: String) {
        guard !submitting else { return }
        submitting = true
        Task { @MainActor in
            defer { submitting = false }
            guard let result = try? await store.redeem(code: code) else { toast = "잠시 후 다시 시도해주세요"; return }
            if result.success {
                toast = "⚡\(Int(result.awardedEnergy)) 에너지를 받았어요!"
            } else {
                toast = result.message ?? "코드를 확인해주세요"
            }
        }
    }
}

struct InviteView: View {
    @StateObject private var vm = InviteViewModel()
    var onClose: () -> Void = {}
    @State private var input = ""

    var body: some View {
        VStack(spacing: 11) {
            // 그라데이션 히어로
            VStack(alignment: .leading, spacing: 5) {
                Text("친구 초대하고 코인 받기 🎁").font(.system(size: 17, weight: .heavy)).foregroundStyle(.white)
                if let s = vm.status {
                    Text("친구가 가입하면 나는 🪙+\(Int(s.rewardCoin)), 친구는 ⚡+\(Int(s.rewardEnergy))!")
                        .font(.system(size: 12)).foregroundStyle(.white.opacity(0.92))
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading).padding(18)
            .background(LinearGradient(colors: [Color(red:0.49,green:0.42,blue:1), Color(red:1,green:0.37,blue:0.54)],
                                       startPoint: .topLeading, endPoint: .bottomTrailing))
            .clipShape(RoundedRectangle(cornerRadius: 16))

            // 내 코드 카드
            VStack(alignment: .leading, spacing: 0) {
                Text("내 추천 코드").font(.system(size: 12)).foregroundStyle(.secondary)
                Text(vm.status?.myCode ?? "-").font(.system(size: 22, weight: .heavy)).padding(.top, 4)
                if let code = vm.status?.myCode {
                    ShareLink(item: "캐시챗에서 만나요! 추천코드 [\(code)] 입력하면 에너지를 드려요 ⚡") {
                        Text("친구에게 공유하기").frame(maxWidth: .infinity)
                    }.buttonStyle(.borderedProminent).padding(.top, 12)
                }
                if let s = vm.status {
                    Text("지금까지 \(Int(s.invitedCount))명 초대").font(.system(size: 12)).foregroundStyle(.secondary).padding(.top, 9)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading).padding(15).background(.white).clipShape(RoundedRectangle(cornerRadius: 16))

            // 추천 코드 입력 카드
            VStack(alignment: .leading, spacing: 8) {
                let available = vm.status?.redeemAvailable ?? false
                Text(available ? "추천 코드 입력" : "이미 추천 코드를 사용했어요")
                    .font(.system(size: 12)).foregroundStyle(.secondary)
                if available {
                    TextField("코드 입력", text: $input)
                        .textInputAutocapitalization(.characters).autocorrectionDisabled()
                        .textFieldStyle(.roundedBorder)
                    Button(action: { vm.redeem(input) }) { Text("에너지 받기").frame(maxWidth: .infinity) }
                        .buttonStyle(.borderedProminent).disabled(vm.submitting || input.isEmpty)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading).padding(15).background(.white).clipShape(RoundedRectangle(cornerRadius: 16))

            Button("닫기") { onClose() }.foregroundStyle(.secondary).font(.system(size: 13))
        }
        .padding(16)
        .onAppear { vm.onAppear() }
        .onDisappear { vm.onDisappear() }
        .safeAreaInset(edge: .bottom) {
            if let t = vm.toast {
                Text(t).font(.system(size: 14, weight: .semibold)).foregroundStyle(.white)
                    .padding(.horizontal, 18).padding(.vertical, 12)
                    .background(Color(red:0.1,green:0.1,blue:0.16).opacity(0.92)).clipShape(Capsule()).padding(.bottom, 8)
            }
        }
        .animation(.easeOut(duration: 0.25), value: vm.toast)
    }
}
```

> `import CashChatShared`·`import Combine` 필수. `InviteStatus`/`RedeemResult` 의 Int 필드는 Int32 export → `Int(...)` 래핑. `store.redeem(code:)`/`getInviteStatus()` 는 Task 1-3 에서 노출됨. `RedeemResult.success`는 Bool, `.message` 는 String?.

- [ ] **Step 2: 혜택존 진입 카드 추가**

`BenefitZoneScreen.swift`에서 `@State private var showInvite = false` 추가, 룰렛 카드 다음에:
```swift
                BenefitInfoCardView(icon: "person.2.fill", title: "친구 초대", badge: .next,
                    description: "친구가 가입하면 나는 코인, 친구는 에너지!", dimmed: false)
                    .padding(.horizontal, 16)
                    .onTapGesture { showInvite = true }
```
그리고 `.sheet` 추가:
```swift
        .sheet(isPresented: $showInvite) {
            InviteView(onClose: { showInvite = false })
        }
```

- [ ] **Step 3: shared 프레임워크 재빌드 + xcodebuild (에이전트 직접)**

```bash
cd apps/frontend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
xcodebuild -project CashChatIOS/CashChatIOS.xcodeproj -scheme CashChatIOS \
  -destination 'platform=iOS Simulator,name=iPhone 16' build 2>&1 | tail -30
```
Expected: `** BUILD SUCCEEDED **`. (시뮬레이터명 없으면 `xcrun simctl list devices available | grep iPhone` 로 확인.) 실패 시 import·Int32 래핑·필드명 점검.

- [ ] **Step 4: 커밋**

```bash
git add apps/frontend/CashChatIOS/CashChatIOS/BenefitZone/InviteView.swift apps/frontend/CashChatIOS/CashChatIOS/BenefitZoneScreen.swift
git commit -m "feat(invite): ios 친구 초대 화면·혜택존 진입 카드 추가"
```

---

## Task 6: 온보딩 추천 코드 입력 (Android + iOS)

> 가입 전이라 스텁은 제출 시 즉시 `store.redeem` 호출(실제 가입 후 적용 타이밍은 BE 몫). 작은 선택적 필드만 추가하며, 로그인 로직은 건드리지 않는다.

**Files:**
- Modify: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/onboarding/OnboardingScreen.kt`
- Modify: `apps/frontend/CashChatIOS/CashChatIOS/ContentView.swift`

- [ ] **Step 1: Android — 로그인 버튼들 아래에 선택적 추천 코드 필드 추가**

`OnboardingScreen.kt`의 "게스트로 시작하기" `Button`을 감싼 `AnimatedVisibility` **다음**(같은 Column 안)에 추가. 파일 상단에 import 추가:
```kotlin
import org.koin.compose.koinInject
import com.nomadclub.cashchat.shared.invite.InviteStore
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
```
그리고 Composable 본문 상단(다른 remember 근처):
```kotlin
    val inviteStore = koinInject<InviteStore>()
    var referral by remember { mutableStateOf("") }
    var referralDone by remember { mutableStateOf(false) }
```
버튼 영역에 추가(게스트 버튼 AnimatedVisibility 다음):
```kotlin
            if (!referralDone) {
                OutlinedTextField(
                    value = referral, onValueChange = { referral = it.uppercase() },
                    singleLine = true, label = { Text("추천 코드 (선택)") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = {
                        val code = referral
                        coroutineScope.launch {
                            val result = runCatching { inviteStore.redeem(code) }.getOrNull()
                            val msg = when {
                                result == null -> "잠시 후 다시 시도해주세요"
                                result.success -> { referralDone = true; "⚡${result.awardedEnergy} 에너지 적용됐어요!" }
                                else -> result.message ?: "코드를 확인해주세요"
                            }
                            snackbarHostState.showSnackbar(msg)
                        }
                    },
                    enabled = referral.isNotBlank(),
                ) { Text("추천 코드 적용", color = Color.White) }
            }
```

- [ ] **Step 2: Android 빌드**

Run: `cd apps/frontend && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: iOS — `ContentView.swift` 로그인 화면에 선택적 추천 코드 필드**

`ContentView.swift`에서 로그인 버튼(Apple/게스트) 영역 아래에 추가. (현재 로그인 UI 블록을 찾아 그 안/아래에 배치)
```swift
// 상단 import 에 이미 CashChatShared 있으면 생략
@State private var referral = ""
@State private var referralDone = false
@State private var referralToast: String?
```
```swift
if !referralDone {
    TextField("추천 코드 (선택)", text: $referral)
        .textInputAutocapitalization(.characters).autocorrectionDisabled()
        .textFieldStyle(.roundedBorder).padding(.horizontal)
    Button("추천 코드 적용") {
        let code = referral
        Task { @MainActor in
            guard let result = try? await KoinHelper().inviteStore().redeem(code: code) else {
                referralToast = "잠시 후 다시 시도해주세요"; return
            }
            if result.success { referralDone = true; referralToast = "⚡\(Int(result.awardedEnergy)) 에너지 적용됐어요!" }
            else { referralToast = result.message ?? "코드를 확인해주세요" }
        }
    }.disabled(referral.isEmpty)
}
```
> `ContentView.swift` 구조에 맞춰 배치 위치를 정하고, 토스트/알림은 기존 패턴에 맞춘다. 구조가 복잡해 배치가 모호하면 DONE_WITH_CONCERNS 로 보고.

- [ ] **Step 4: shared 재빌드 + xcodebuild**

```bash
cd apps/frontend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
xcodebuild -project CashChatIOS/CashChatIOS.xcodeproj -scheme CashChatIOS -destination 'platform=iOS Simulator,name=iPhone 16' build 2>&1 | tail -20
```
Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 5: 커밋**

```bash
git add apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/onboarding/OnboardingScreen.kt apps/frontend/CashChatIOS/CashChatIOS/ContentView.swift
git commit -m "feat(invite): 온보딩 추천 코드 입력 필드 추가 (android·ios)"
```

---

## Self-Review 체크

- **Spec coverage:** 추천코드 방식·보상(초대자 코인/가입자 에너지)=Task1 Fake; 공유시트=Task4/5; 입력(혜택존)=Task4/5; 입력(온보딩)=Task6; FE-first 격리(Repository+Fake)=Task1; DI=Task3; 테스트=Task1/2. BE 계약은 문서. 누락 없음.
- **Type consistency:** `InviteStatus`(myCode/invitedCount/redeemAvailable/rewardCoin/rewardEnergy)·`RedeemResult`(success/awardedEnergy/message)·`InviteStore`(refresh/redeem)·`InviteRepository`(getInviteStatus/redeemCode) 전 태스크 일관. iOS Int32 래핑 주의 표기.
- **Placeholder scan:** iOS ContentView 배치 위치만 구조 의존(주의 표기), 그 외 placeholder 없음.

## 후속
- BE `/api/invite/*` 구현 후 `RemoteInviteRepository` 추가 + DI 교체(인터페이스 불변).
- (선택) 초대 링크/딥링크.
