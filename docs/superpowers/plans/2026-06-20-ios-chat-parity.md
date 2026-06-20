# iOS 채팅 파리티 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** iOS 채팅 화면을 Android와 기능 동등 수준으로 끌어올린다 — HUD·출석·에너지 게이트+리워드 광고·Ad Gate/상품 카드·진화 연동.

**Architecture:** shared(KMM) 로직은 전부 재사용한다. iOS `ChatViewModel`을 확장해 `HudStore`/`AdRewardStore`/`AttendanceStore`를 감싸고(Android `ChatViewModel`과 1:1), AdMob은 `RewardedAdManager.swift`로 격리한다. `FlowCollector`(shared/iosMain)에 HUD/gateInfo/streamCompleted/quota 구독 브리지를 추가한다.

**Tech Stack:** Kotlin 2.0.21 / KMM, Swift / SwiftUI, Ktor, Google-Mobile-Ads-SDK(iOS, SPM), Koin.

---

## 검증 방식 안내 (중요)

iOS Swift UI/ViewModel은 이 프로젝트에 단위 테스트 하니스가 없다(공유 로직만 Kotest). 따라서:
- **shared/iosMain 브리지 변경**은 프레임워크 빌드 성공으로 1차 검증한다.
- **iOS Swift 코드**는 Xcode 빌드 성공 + 시뮬레이터 e2e로 검증한다.
- **SSE 의존 기능(Slice E, D의 재전송 스트림)**은 컴파일까지 보장하고, 실동작은 SSE 경로 동작 확인 후 실기기에서 검증한다.

각 슬라이스 작업 전 공통 준비:
```bash
cd apps/frontend
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
```
shared/iosMain을 수정한 슬라이스는 반드시 프레임워크를 재빌드한다:
```bash
./gradlew :shared:embedAndSignAppleFrameworkForXcode
```

## File Structure

| 파일 | 책임 | 슬라이스 |
|---|---|---|
| `shared/src/iosMain/.../di/IosBridges.kt` (수정) | `FlowCollector`에 collectHud/collectGateInfo/collectStreamCompleted/collectQuota 추가 | A·B·D·E |
| `CashChatIOS/CashChatIOS/Ads/RewardedAdManager.swift` (생성) | GADRewardedAd preload/show + SSV nonce | A |
| `CashChatIOS/CashChatIOS/AppConfig.swift` (수정) | `admobRewardedAdUnitId`/`admobAppId` 노출 | A |
| `CashChatIOS/CashChatIOS/Secrets.swift` (수정) | 테스트 광고 ID 값 | A |
| `CashChatIOS/CashChatIOS/CashChatIOSApp.swift` (수정) | `MobileAds.shared.start()` | A |
| `CashChatIOS/CashChatIOS/Info.plist` (수정) | `GADApplicationIdentifier` | A |
| `CashChatIOS/CashChatIOS/ChatViewModel.swift` (수정) | hud/attendance/rewardPhase 상태 + startAdReward/startGateUnlock | B·C·D·E |
| `CashChatIOS/CashChatIOS/ChatScreen.swift` (수정) | HUD 헤더, 출석 시트, 게이트 시트, 상품/게이트 카드, 추천칩, 공유 | B·C·D·E·G |
| `CashChatIOS/CashChatIOS/EvolutionScreen.swift` (생성) | EvolutionStore 연동 진화 화면 | F |

---

## Slice A — AdMob iOS 기반

### Task A1: shared 브리지 — collectHud/collectQuota 추가

**Files:**
- Modify: `apps/frontend/shared/src/iosMain/kotlin/com/nomadclub/cashchat/shared/di/IosBridges.kt`

- [ ] **Step 1: import 추가**

`IosBridges.kt` 상단 import 블록에 추가:

```kotlin
import com.nomadclub.cashchat.shared.ads.AdRewardQuotaDto
import com.nomadclub.cashchat.shared.hud.HudState
import com.nomadclub.cashchat.shared.hud.HudStore
import com.nomadclub.cashchat.shared.chat.ChatStore
```

(`AdRewardStore`, `ChatStore`는 이미 import됨 — 중복 추가하지 말 것.)

- [ ] **Step 2: FlowCollector에 메서드 추가**

`FlowCollector` 클래스 안 `cancel()` 위에 추가:

```kotlin
    fun collectHud(store: HudStore, onEach: (HudState) -> Unit) {
        scope.launch { store.state.collect { onEach(it) } }
    }

    fun collectStreamCompleted(store: ChatStore, onEach: (Int) -> Unit) {
        scope.launch { store.streamCompletedCount.collect { onEach(it) } }
    }

    fun collectGateInfo(store: ChatStore, onEach: (ChatStore.GateInfo?) -> Unit) {
        scope.launch { store.gateInfo.collect { onEach(it) } }
    }

    fun collectQuota(store: AdRewardStore, onEach: (AdRewardQuotaDto?) -> Unit) {
        scope.launch { store.quota.collect { onEach(it) } }
    }
```

- [ ] **Step 3: 프레임워크 빌드로 검증**

Run:
```bash
cd apps/frontend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && ./gradlew :shared:embedAndSignAppleFrameworkForXcode
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add apps/frontend/shared/src/iosMain/kotlin/com/nomadclub/cashchat/shared/di/IosBridges.kt
git commit -m "feat(ios): FlowCollector에 HUD/gateInfo/streamCompleted/quota 구독 브리지 추가"
```

### Task A2: AppConfig/Secrets에 광고 ID 추가

**Files:**
- Modify: `apps/frontend/CashChatIOS/CashChatIOS/AppConfig.swift`
- Modify: `apps/frontend/CashChatIOS/CashChatIOS/Secrets.swift`

- [ ] **Step 1: Secrets.swift에 테스트 광고 ID 추가**

`Secrets` enum/struct 안에 추가 (Google 공식 테스트 ID):

```swift
    // AdMob 테스트 ID (Google 공식). 릴리즈 시 실 ID로 교체.
    static let admobAppId = "ca-app-pub-3940256099942544~1458002511"
    static let admobRewardedAdUnitId = "ca-app-pub-3940256099942544/1712485313"
```

> 주의: `Secrets.swift`는 .gitignore일 수 있다. `git check-ignore CashChatIOS/CashChatIOS/Secrets.swift`로 확인하고, ignore면 커밋하지 말고 사용자에게 로컬 추가를 안내한다. 추적 중이면 함께 커밋.

- [ ] **Step 2: AppConfig.swift에 노출 프로퍼티 추가**

`AppConfig` enum 안 `apiBaseUrl` 아래에 추가:

```swift
    static let admobAppId: String = required(Secrets.admobAppId, key: "admobAppId")
    static let admobRewardedAdUnitId: String = required(Secrets.admobRewardedAdUnitId, key: "admobRewardedAdUnitId")
```

- [ ] **Step 3: Commit** (Secrets가 추적 대상일 때만 포함)

```bash
git add apps/frontend/CashChatIOS/CashChatIOS/AppConfig.swift
git commit -m "feat(ios): AppConfig에 AdMob 광고 ID 노출"
```

### Task A3: Google-Mobile-Ads-SDK 의존성 + Info.plist + 초기화

> 이 태스크는 Xcode 프로젝트 파일을 다룬다. SPM 추가는 Xcode GUI에서 수행해야 하므로, 실행자는 아래 절차를 Xcode에서 진행하고 결과 빌드로 검증한다.

**Files:**
- Modify: `apps/frontend/CashChatIOS/CashChatIOS.xcodeproj/...` (SPM 의존성)
- Modify: `apps/frontend/CashChatIOS/CashChatIOS/Info.plist`
- Modify: `apps/frontend/CashChatIOS/CashChatIOS/CashChatIOSApp.swift`

- [ ] **Step 1: SPM으로 SDK 추가**

Xcode → File → Add Package Dependencies → `https://github.com/googleads/swift-package-manager-google-mobile-ads.git` → 최신 안정 버전 → `GoogleMobileAds` 라이브러리를 `CashChatIOS` 타깃에 추가.

- [ ] **Step 2: Info.plist에 GADApplicationIdentifier 추가**

`Info.plist`에 키 추가:

```xml
<key>GADApplicationIdentifier</key>
<string>ca-app-pub-3940256099942544~1458002511</string>
```

(가능하면 `AppConfig.admobAppId`와 동일 값. plist는 정적이므로 테스트 앱 ID 직접 기입.)

- [ ] **Step 3: 앱 시작 시 MobileAds 초기화**

`CashChatIOSApp.swift` 상단에 `import GoogleMobileAds` 추가하고, `init()` 또는 `WindowGroup` 생성 시점에 1회 호출:

```swift
import GoogleMobileAds
// ...
init() {
    MobileAds.shared.start(completionHandler: nil)
}
```

> SDK 버전에 따라 심볼명이 `GADMobileAds.sharedInstance().start(...)`일 수 있다. 빌드 에러 시 SDK 버전의 정식 심볼로 맞춘다.

- [ ] **Step 4: 빌드 검증**

Xcode에서 시뮬레이터 타깃으로 빌드(⌘B). Expected: 빌드 성공, 콘솔에 AdMob 초기화 로그.

- [ ] **Step 5: Commit**

```bash
git add apps/frontend/CashChatIOS/CashChatIOS.xcodeproj apps/frontend/CashChatIOS/CashChatIOS/Info.plist apps/frontend/CashChatIOS/CashChatIOS/CashChatIOSApp.swift
git commit -m "feat(ios): Google-Mobile-Ads-SDK 연동 및 초기화"
```

### Task A4: RewardedAdManager.swift 구현

**Files:**
- Create: `apps/frontend/CashChatIOS/CashChatIOS/Ads/RewardedAdManager.swift`

- [ ] **Step 1: RewardedAdManager 작성**

Android `RewardedAdManager.kt`와 동형. SSV는 `customData=nonce`.

```swift
import Foundation
import GoogleMobileAds
import UIKit

/// AdMob 보상형 광고 사전 로드/노출 관리. Android RewardedAdManager와 동형.
/// SSV(서버 검증)용 nonce는 customData로 전달한다.
@MainActor
final class RewardedAdManager: NSObject {
    private var rewardedAd: RewardedAd?
    private var isLoading = false
    private let adUnitId = AppConfig.admobRewardedAdUnitId

    private var onRewarded: ((Int) -> Void)?
    private var onDismissed: (() -> Void)?

    /// 광고 미리 로드. 이미 로드됐거나 로딩 중이면 무시.
    func preload() {
        guard rewardedAd == nil, !isLoading else { return }
        isLoading = true
        RewardedAd.load(with: adUnitId, request: Request()) { [weak self] ad, error in
            guard let self else { return }
            self.isLoading = false
            if let error {
                print("리워드 광고 로드 실패: \(error.localizedDescription)")
                self.rewardedAd = nil
                return
            }
            self.rewardedAd = ad
        }
    }

    var isReady: Bool { rewardedAd != nil }

    /// 광고 노출. 준비 안 됐으면 onNotReady. 닫힘 시 항상 onDismissed.
    func show(
        nonce: String?,
        onRewarded: @escaping (Int) -> Void,
        onDismissed: @escaping () -> Void,
        onNotReady: @escaping () -> Void = {}
    ) {
        guard let ad = rewardedAd else { onNotReady(); return }
        if let nonce {
            let options = ServerSideVerificationOptions()
            options.customData = nonce
            ad.serverSideVerificationOptions = options
        }
        self.onRewarded = onRewarded
        self.onDismissed = onDismissed
        ad.fullScreenContentDelegate = self

        guard let root = Self.topViewController() else { onDismissed(); return }
        rewardedAd = nil // 중복 노출 방지
        ad.present(from: root) { [weak self] in
            let amount = ad.adReward.amount.intValue
            self?.onRewarded?(amount)
        }
    }

    private static func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
        var top = scene?.windows.first(where: { $0.isKeyWindow })?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}

extension RewardedAdManager: FullScreenContentDelegate {
    func adDidDismissFullScreenContent(_ ad: FullScreenPresentingAd) {
        onDismissed?()
        onDismissed = nil
        onRewarded = nil
        preload()
    }
    func ad(_ ad: FullScreenPresentingAd, didFailToPresentFullScreenContentWithError error: Error) {
        print("리워드 광고 노출 실패: \(error.localizedDescription)")
        onDismissed?()
        onDismissed = nil
        onRewarded = nil
        preload()
    }
}
```

> SDK 버전에 따라 타입명이 다를 수 있다(구버전: `GADRewardedAd`, `GADRequest`, `GADServerSideVerificationOptions`, `GADFullScreenContentDelegate`). 빌드 에러 시 설치된 SDK 버전의 심볼로 맞춘다 — 로직은 동일하게 유지.

- [ ] **Step 2: 빌드 검증**

Xcode 빌드(⌘B). Expected: 성공. (실제 노출은 Slice D에서 게이트와 함께 검증.)

- [ ] **Step 3: Commit**

```bash
git add apps/frontend/CashChatIOS/CashChatIOS/Ads/RewardedAdManager.swift
git commit -m "feat(ios): RewardedAdManager 구현 (preload/show + SSV nonce)"
```

---

## Slice B — HUD 헤더

### Task B1: ChatViewModel에 HUD 상태 연결

**Files:**
- Modify: `apps/frontend/CashChatIOS/CashChatIOS/ChatViewModel.swift`

- [ ] **Step 1: HUD 상태 프로퍼티 + 스토어 추가**

`ChatViewModel`에 `@Published` 프로퍼티와 store 추가:

```swift
    @Published var level: Int = 1
    @Published var isMaxLevel = false
    @Published var energy: Int = 0
    @Published var maxEnergy: Int = 0
    @Published var points: Int64? = nil
    @Published var nextRecoverAt: String? = nil
    @Published var hudLoaded = false

    private let hudStore = KoinHelper().hudStore()
```

- [ ] **Step 2: load()에서 HUD 구독 + refresh**

`load()`의 `didLoad` 가드 이후에 추가:

```swift
        hudStore.refresh()
        collector.collectHud(store: hudStore) { [weak self] s in
            Task { @MainActor in
                guard let self else { return }
                self.level = Int(s.level)
                self.isMaxLevel = s.isMaxLevel
                self.energy = Int(s.energy)
                self.maxEnergy = Int(s.maxEnergy)
                self.points = s.points?.int64Value
                self.nextRecoverAt = s.nextRecoverAt
                self.hudLoaded = s.isLoaded
            }
        }
        collector.collectStreamCompleted(store: store) { [weak self] count in
            Task { @MainActor in
                guard let self, count.intValue > 0 else { return }
                self.hudStore.refreshEnergyOnly()
            }
        }
```

> `s.points`는 Kotlin `Long?` → Swift `KotlinLong?`이므로 `.int64Value`. `s.level`/`s.energy`는 non-null Int → `KotlinInt`가 아니라 그대로 `Int32`로 노출되니 `Int(...)`로 캐스팅.

- [ ] **Step 3: refreshEnergy 메서드 추가 (회복 카운트다운 종료용)**

```swift
    func refreshEnergy() {
        hudStore.refreshEnergyOnly()
    }
```

> `refreshEnergyOnly`는 suspend가 아니라면 그대로 호출. suspend라면 `Task { try? await ... }`로 감싼다. (HudStore.refreshEnergyOnly는 suspend이므로 `Task { try? await self.hudStore.refreshEnergyOnly() }`로 감싼다 — Step 2/3 모두 이 형태로 작성.)

- [ ] **Step 4: 빌드 검증**

Xcode 빌드(⌘B). Expected: 성공.

- [ ] **Step 5: Commit**

```bash
git add apps/frontend/CashChatIOS/CashChatIOS/ChatViewModel.swift
git commit -m "feat(ios): ChatViewModel에 HUD 상태(에너지/레벨/포인트) 연결"
```

### Task B2: ChatScreen 헤더에 HUD 표시

**Files:**
- Modify: `apps/frontend/CashChatIOS/CashChatIOS/ChatScreen.swift`

- [ ] **Step 1: header를 HUD 칩 포함으로 교체**

기존 `header` computed property의 가운데 `Text("CashAI 비서")` 영역을 캐릭터/레벨로, 우측에 에너지/포인트 칩을 추가한다. 기존 햄버거 버튼/새 대화 버튼은 유지:

```swift
    private var header: some View {
        HStack(spacing: 8) {
            Button { showConversations = true } label: {
                Image(systemName: chatSFSymbol("line.3.horizontal", fallback: "line.horizontal.3"))
                    .foregroundStyle(.primary)
            }
            // 캐릭터 탭 → 진화 화면 (Slice F에서 showEvolution 토글)
            Button { showEvolution = true } label: {
                HStack(spacing: 6) {
                    Image(systemName: "sparkles")
                        .foregroundStyle(accent)
                    if vm.hudLoaded {
                        Text("Lv.\(vm.level)").font(.subheadline.weight(.bold)).foregroundStyle(.primary)
                    }
                }
            }
            Spacer()
            if vm.hudLoaded {
                if let p = vm.points {
                    chip("🪙", "\(p)")
                }
                VStack(alignment: .trailing, spacing: 2) {
                    chip("⚡", "\(vm.energy)/\(vm.maxEnergy)", warning: vm.energy == 0)
                    if let iso = vm.nextRecoverAt {
                        RecoveryCountdown(nextRecoverAtIso: iso) { vm.refreshEnergy() }
                    }
                }
            }
            Button { vm.startNew() } label: {
                Image(systemName: chatSFSymbol("square.and.pencil", fallback: "plus.square"))
                    .foregroundStyle(.primary)
            }
        }
        .padding(.horizontal, 16).padding(.vertical, 10)
        .background(Color(.systemBackground))
    }

    private func chip(_ emoji: String, _ value: String, warning: Bool = false) -> some View {
        Text("\(emoji) \(value)")
            .font(.caption.weight(.semibold))
            .padding(.horizontal, 8).padding(.vertical, 4)
            .background(warning ? Color.red.opacity(0.15) : Color(.secondarySystemGroupedBackground))
            .clipShape(Capsule())
    }
```

- [ ] **Step 2: RecoveryCountdown 뷰 + showEvolution 상태 추가**

`ChatScreen`에 `@State private var showEvolution = false` 추가(Slice F에서 시트 연결). 파일 하단에 카운트다운 뷰 추가:

```swift
/// 다음 에너지 회복까지 카운트다운. 0 도달 시 onFinished로 에너지 재조회.
private struct RecoveryCountdown: View {
    let nextRecoverAtIso: String
    let onFinished: () -> Void
    @State private var remain = ""

    var body: some View {
        Text(remain)
            .font(.caption2)
            .foregroundStyle(.secondary)
            .task(id: nextRecoverAtIso) {
                let fmt = ISO8601DateFormatter()
                fmt.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
                let target = fmt.date(from: nextRecoverAtIso)
                    ?? ISO8601DateFormatter().date(from: nextRecoverAtIso)
                guard let target else { return }
                while !Task.isCancelled {
                    let sec = max(Int(target.timeIntervalSinceNow), 0)
                    remain = String(format: "%d:%02d 후 ⚡", sec / 60, sec % 60)
                    if sec == 0 { break }
                    try? await Task.sleep(for: .seconds(1))
                }
                onFinished()
            }
    }
}
```

- [ ] **Step 3: 빌드 + 시뮬레이터 검증**

Xcode 빌드 후 시뮬레이터 실행. Expected: 헤더에 `Lv.N`, ⚡에너지 칩/회복 카운트다운, (POINT_BALANCE 활성 시) 🪙칩 표시. 메시지 전송 후 에너지 칩 갱신.

- [ ] **Step 4: Commit**

```bash
git add apps/frontend/CashChatIOS/CashChatIOS/ChatScreen.swift
git commit -m "feat(ios): 채팅 헤더에 HUD(에너지/레벨/포인트/회복 카운트다운) 표시"
```

---

## Slice C — 채팅 내 출석

### Task C1: ChatViewModel에 출석 자동 체크인

**Files:**
- Modify: `apps/frontend/CashChatIOS/CashChatIOS/ChatViewModel.swift`

- [ ] **Step 1: AttendanceStore 연동 + 상태 추가**

`AttendanceViewModel`(BenefitZone)이 쓰는 `attendanceStore()`를 재사용한다. `ChatViewModel`에 추가:

```swift
    @Published var attendanceMonth: Int = 0
    @Published var attendanceStreak: Int = 0
    @Published var attendanceCheckedDays: Set<Int> = []
    @Published var attendanceTodayChecked = false
    @Published var checkInToast: String? = nil

    private let attendanceStore = KoinHelper().attendanceStore()
```

- [ ] **Step 2: load()에서 출석 구독 + 자동 체크인**

`load()`에 추가:

```swift
        attendanceStore.loadMonthly(year: nil, month: nil)
        collector.collectAttendance(store: attendanceStore) { [weak self] s in
            Task { @MainActor in
                guard let self else { return }
                self.attendanceMonth = Int(s.month)
                self.attendanceStreak = Int(s.currentStreak)
                self.attendanceCheckedDays = Set(s.checkedDays.map { $0.intValue })
                let wasUnchecked = !self.attendanceTodayChecked
                self.attendanceTodayChecked = s.todayChecked
                // 월간 로드 후 미출석이면 1회 자동 체크인
                if !s.todayChecked && wasUnchecked && !s.isCheckingIn {
                    self.attendanceStore.checkIn()
                }
            }
        }
        collector.collectRewards(store: attendanceStore) { [weak self] ev in
            Task { @MainActor in self?.checkInToast = "출석 완료! +\(ev.awardedCoin) 코인" }
        }
```

> 중복 체크인 방지: `checkIn`은 서버가 409 ALREADY_CHECKED_IN을 멱등 처리하고, `AttendanceStore`가 `isCheckingIn`으로 가드하므로 안전. `wasUnchecked` 가드로 콜백 재진입 시 반복 호출만 막는다.

- [ ] **Step 3: 빌드 검증**

Xcode 빌드(⌘B). Expected: 성공.

- [ ] **Step 4: Commit**

```bash
git add apps/frontend/CashChatIOS/CashChatIOS/ChatViewModel.swift
git commit -m "feat(ios): 채팅 진입 시 자동 출석 체크인 연동"
```

### Task C2: ChatScreen에 출석 캘린더 시트 + 보상 토스트

**Files:**
- Modify: `apps/frontend/CashChatIOS/CashChatIOS/ChatScreen.swift`

- [ ] **Step 1: 캘린더 버튼 + 시트 + 토스트 추가**

헤더의 햄버거 옆 또는 우측에 출석 버튼 추가하고, body에 sheet/overlay 연결. 기존 `BenefitZone/AttendanceViewModel.swift`의 `AttendanceWidgetView`는 `AttendanceViewModel`을 받으므로, 채팅에서는 가벼운 주간 표시를 위해 별도 `AttendanceViewModel` 인스턴스를 시트에서 사용한다:

```swift
    // header 안 적절한 위치(Spacer 뒤, 칩 앞)에 추가:
            Button { showAttendance = true } label: {
                Image(systemName: chatSFSymbol("calendar", fallback: "calendar.circle"))
                    .foregroundStyle(.primary)
            }
```

`ChatScreen`에 상태 추가: `@State private var showAttendance = false`.
body의 `.sheet(isPresented: $showConversations)` 아래에 추가:

```swift
        .sheet(isPresented: $showAttendance) {
            AttendanceSheet()
        }
        .overlay(alignment: .top) {
            if let toast = vm.checkInToast {
                Text(toast)
                    .font(.subheadline.weight(.semibold))
                    .padding(.horizontal, 16).padding(.vertical, 10)
                    .background(.orange).foregroundStyle(.white)
                    .clipShape(Capsule())
                    .padding(.top, 8)
                    .transition(.move(edge: .top).combined(with: .opacity))
                    .task {
                        try? await Task.sleep(for: .seconds(2))
                        vm.checkInToast = nil
                    }
            }
        }
        .animation(.easeInOut, value: vm.checkInToast)
```

- [ ] **Step 2: AttendanceSheet 래퍼 추가**

`ChatScreen.swift` 하단에 추가(기존 `AttendanceWidgetView` 재사용):

```swift
/// 출석 캘린더 시트 — BenefitZone의 AttendanceWidgetView 재사용.
private struct AttendanceSheet: View {
    @StateObject private var vm = AttendanceViewModel()
    var body: some View {
        NavigationStack {
            ScrollView {
                AttendanceWidgetView(vm: vm).padding()
            }
            .navigationTitle("출석 체크")
            .navigationBarTitleDisplayMode(.inline)
            .onAppear { vm.load() }
        }
    }
}
```

- [ ] **Step 3: 빌드 + 시뮬레이터 검증**

Expected: 채팅 진입 시 자동 출석 토스트(미출석일 때), 캘린더 버튼 탭 시 주간 출석 위젯 표시.

- [ ] **Step 4: Commit**

```bash
git add apps/frontend/CashChatIOS/CashChatIOS/ChatScreen.swift
git commit -m "feat(ios): 채팅 출석 캘린더 시트 및 체크인 보상 토스트"
```

---

## Slice D — 에너지 게이트 + 리워드 광고 재전송

### Task D1: ChatViewModel에 리워드 보상 플로우

**Files:**
- Modify: `apps/frontend/CashChatIOS/CashChatIOS/ChatViewModel.swift`

- [ ] **Step 1: rewardPhase + AdRewardStore 연동**

```swift
    enum RewardPhase { case idle, showingAd, polling, failed }
    @Published var rewardPhase: RewardPhase = .idle

    private let adRewardStore = KoinHelper().adRewardStore()
```

`load()`의 energyGate 구독부에서 게이트가 보일 때 quota 새로고침:

```swift
        // 기존 collectEnergyGate 콜백 안에 추가:
        //   if visible.boolValue { Task { try? await self.adRewardStore.refreshQuota() } }
```

(기존 `collectEnergyGate` 클로저를 아래로 교체)

```swift
        collector.collectEnergyGate(store: store) { [weak self] visible in
            Task { @MainActor in
                guard let self else { return }
                self.energyGateVisible = visible.boolValue
                if visible.boolValue { try? await self.adRewardStore.refreshQuota() }
            }
        }
```

- [ ] **Step 2: startAdReward 구현 (Android 미러)**

```swift
    /// 게이트 CTA: baseline 적립횟수 → nonce → 광고 표시 → 적립 폴링 → 성공 시 재전송.
    /// showAd는 nonce를 받아 광고를 띄우고, 적립이 관측될 baseline 기준 성공 여부와 무관하게
    /// "광고를 끝까지 봤는지"를 반환한다(닫힘=true, 미준비=false).
    func startAdReward(showAd: @escaping (_ nonce: String) async -> Bool) {
        Task { @MainActor in
            rewardPhase = .showingAd
            var applied = false
            do {
                let baseline = try await adRewardStore.refreshQuota().usedToday
                let nonce = try await adRewardStore.requestNonce()
                if await showAd(nonce) {
                    rewardPhase = .polling
                    applied = try await adRewardStore.awaitRewardApplied(baselineUsedToday: baseline).boolValue
                }
            } catch {
                applied = false
            }
            try? await hudStore.refreshEnergyOnly()
            _ = try? await adRewardStore.refreshQuota()
            if applied {
                rewardPhase = .idle
                store.retryBlocked()
            } else {
                rewardPhase = .failed
            }
        }
    }

    func dismissGate() {
        rewardPhase = .idle
        store.dismissEnergyGate()
    }
```

> `awaitRewardApplied`는 `Int` 인자 + `Boolean` 반환 suspend → Swift `try await ...(baselineUsedToday: Int32)` 후 `KotlinBoolean`이면 `.boolValue`. `baseline`(`usedToday`)은 `Int32`. 빌드 시 타입 경고에 맞춰 캐스팅.

- [ ] **Step 3: 빌드 검증**

Xcode 빌드(⌘B). Expected: 성공.

- [ ] **Step 4: Commit**

```bash
git add apps/frontend/CashChatIOS/CashChatIOS/ChatViewModel.swift
git commit -m "feat(ios): 에너지 게이트 리워드 광고 보상 플로우(startAdReward)"
```

### Task D2: 게이트 바텀시트 UI + RewardedAdManager 연결

**Files:**
- Modify: `apps/frontend/CashChatIOS/CashChatIOS/ChatScreen.swift`

- [ ] **Step 1: RewardedAdManager 보유 + preload**

`ChatScreen`에 추가:

```swift
    @StateObject private var adManager = RewardedAdManagerBox()
```

`RewardedAdManager`는 `NSObject`라 `@StateObject`에 직접 못 쓰므로 박싱 래퍼를 파일 하단에 둔다:

```swift
@MainActor
final class RewardedAdManagerBox: ObservableObject {
    let manager = RewardedAdManager()
    init() { manager.preload() }
}
```

- [ ] **Step 2: 기존 energyBanner를 바텀시트로 교체**

body의 `if vm.energyGateVisible { energyBanner }`를 제거하고, `.sheet`로 교체:

```swift
        .sheet(isPresented: $vm.energyGateVisible, onDismiss: { vm.dismissGate() }) {
            EnergyGateSheet(vm: vm, adManager: adManager.manager)
                .presentationDetents([.height(280)])
        }
```

> `vm.energyGateVisible`는 `@Published`이므로 바인딩 가능. 단 시트 dismiss와 store 상태 동기화를 위해 onDismiss에서 `dismissGate()` 호출.

- [ ] **Step 3: EnergyGateSheet 작성**

파일 하단에 추가:

```swift
private struct EnergyGateSheet: View {
    @ObservedObject var vm: ChatViewModel
    let adManager: RewardedAdManager

    var body: some View {
        VStack(spacing: 16) {
            Text("🍚 밥이 부족해요").font(.headline)
            Text("광고를 보고 밥을 충전하면\n바로 답변을 이어받을 수 있어요.")
                .font(.subheadline).foregroundStyle(.secondary)
                .multilineTextAlignment(.center)

            switch vm.rewardPhase {
            case .showingAd, .polling:
                ProgressView(vm.rewardPhase == .polling ? "보상 확인 중…" : "광고 준비 중…")
            case .failed:
                Text("보상 적립을 확인하지 못했어요. 다시 시도해 주세요.")
                    .font(.caption).foregroundStyle(.orange)
                watchButton
            case .idle:
                watchButton
            }
            Button("닫기") { vm.dismissGate() }
                .font(.subheadline).tint(.secondary)
        }
        .padding(24)
    }

    private var watchButton: some View {
        Button {
            vm.startAdReward { nonce in
                await withCheckedContinuation { cont in
                    adManager.show(
                        nonce: nonce,
                        onRewarded: { _ in },
                        onDismissed: { cont.resume(returning: true) },
                        onNotReady: { cont.resume(returning: false) }
                    )
                }
            }
        } label: {
            Label("광고 보고 밥 충전", systemImage: "play.fill")
                .font(.subheadline.weight(.bold))
                .frame(maxWidth: .infinity).padding(.vertical, 12)
                .background(.orange).foregroundStyle(.white)
                .clipShape(Capsule())
        }
    }
}
```

- [ ] **Step 4: 빌드 + 시뮬레이터 검증**

Expected: 밥 부족(에너지 0) 상태에서 메시지 전송 시 게이트 시트, "광고 보고 밥 충전" → 테스트 리워드 광고 노출 → 닫힘 후 폴링 → (서버 적립 시) 재전송. *재전송 스트림은 SSE 동작 시 검증.*

- [ ] **Step 5: Commit**

```bash
git add apps/frontend/CashChatIOS/CashChatIOS/ChatScreen.swift
git commit -m "feat(ios): 에너지 게이트 바텀시트 + 리워드 광고 연결"
```

---

## Slice E — Ad Gate 블라인드 카드 + 상품 카드

### Task E1: ChatViewModel에 gateInfo + startGateUnlock

**Files:**
- Modify: `apps/frontend/CashChatIOS/CashChatIOS/ChatViewModel.swift`

- [ ] **Step 1: gateInfo 상태 + 구독 + 해제 메서드**

```swift
    @Published var gateTeaserChars: Int = 80
    @Published var gateRewardCoin: Int = 30
```

`load()`에 추가:

```swift
        collector.collectGateInfo(store: store) { [weak self] info in
            Task { @MainActor in
                guard let self, let info else { return }
                self.gateTeaserChars = Int(info.teaserChars)
                self.gateRewardCoin = Int(info.rewardCoin)
            }
        }
```

해제 메서드:

```swift
    /// Ad Gate 해제: nonce 발급 → 광고 → 성공 시 해당 메시지 blur 해제.
    func startGateUnlock(messageId: String, showAd: @escaping (_ nonce: String) async -> Bool) {
        Task { @MainActor in
            var watched = false
            do {
                let nonce = try await adRewardStore.requestNonce()
                watched = await showAd(nonce)
            } catch { watched = false }
            if watched { store.unlockGatedMessage(messageId: messageId) }
        }
    }
```

- [ ] **Step 2: 빌드 검증** — Xcode 빌드(⌘B). Expected: 성공.

- [ ] **Step 3: Commit**

```bash
git add apps/frontend/CashChatIOS/CashChatIOS/ChatViewModel.swift
git commit -m "feat(ios): gateInfo 구독 및 Ad Gate 해제(startGateUnlock)"
```

### Task E2: ChatScreen row에 상품 카드 + Ad Gate 카드

**Files:**
- Modify: `apps/frontend/CashChatIOS/CashChatIOS/ChatScreen.swift`

- [ ] **Step 1: row(for:)에 분기 추가**

기존 `row(for:)`의 `else if let a = item as? ChatItemAssistantMessage` 분기에서, gated 처리 추가. 그리고 product 분기를 새로 추가. 기존 주석 `// ChatItemProductCards 는 Slice 1d 에서 처리.`를 실제 구현으로 대체:

```swift
        } else if let p = item as? ChatItemProductCards {
            VStack(spacing: 8) {
                ForEach(p.products, id: \.trackingUrl) { product in
                    ProductCardView(product: product)
                }
            }
        }
```

`ChatItemAssistantMessage` 분기 안에서, `a.gated && !a.isStreaming`이면 AdGateCard로 대체:

```swift
        } else if let a = item as? ChatItemAssistantMessage {
            if a.gated && !a.isStreaming {
                AdGateCardView(
                    fullText: a.text,
                    teaserChars: vm.gateTeaserChars,
                    rewardCoin: vm.gateRewardCoin,
                    onWatch: {
                        vm.startGateUnlock(messageId: a.id) { nonce in
                            await withCheckedContinuation { cont in
                                adManager.manager.show(
                                    nonce: nonce,
                                    onRewarded: { _ in },
                                    onDismissed: { cont.resume(returning: true) },
                                    onNotReady: { cont.resume(returning: false) }
                                )
                            }
                        }
                    }
                )
            } else {
                // 기존 어시스턴트 말풍선 렌더 유지
                ...
            }
        }
```

> 기존 어시스턴트 말풍선 코드(`HStack { VStack ... markdownText ... if a.isError { retry } }`)는 `else` 블록 안으로 그대로 이동한다.

- [ ] **Step 2: ProductCardView + AdGateCardView 작성**

파일 하단에 추가:

```swift
private struct ProductCardView: View {
    let product: ProductDto
    var body: some View {
        HStack(spacing: 10) {
            AsyncImage(url: URL(string: product.imageUrl ?? "")) { img in
                img.resizable().scaledToFill()
            } placeholder: {
                Color(.tertiarySystemGroupedBackground)
            }
            .frame(width: 64, height: 64).clipShape(RoundedRectangle(cornerRadius: 10))
            VStack(alignment: .leading, spacing: 4) {
                Text(product.title).font(.subheadline.weight(.semibold)).lineLimit(2)
                Text("\(product.price)원").font(.subheadline.weight(.bold)).foregroundStyle(.orange)
                if let rating = product.rating?.doubleValue {
                    Text("★ \(rating, specifier: "%.1f")").font(.caption2).foregroundStyle(.secondary)
                }
            }
            Spacer()
        }
        .padding(12)
        .background(Color(.secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .onTapGesture {
            if let url = URL(string: product.trackingUrl) { UIApplication.shared.open(url) }
        }
    }
}

private struct AdGateCardView: View {
    let fullText: String
    let teaserChars: Int
    let rewardCoin: Int
    let onWatch: () -> Void
    var body: some View {
        let teaser = String(fullText.prefix(teaserChars))
        return VStack(alignment: .leading, spacing: 10) {
            Text(teaser + "…").foregroundStyle(.primary)
            Button(action: onWatch) {
                Label("광고 보고 전체 보기 (+\(rewardCoin))", systemImage: "play.fill")
                    .font(.caption.weight(.bold))
                    .frame(maxWidth: .infinity).padding(.vertical, 10)
                    .background(.orange).foregroundStyle(.white)
                    .clipShape(Capsule())
            }
        }
        .padding(14)
        .background(Color(.secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }
}
```

> `product.rating`은 Kotlin `Double?` → `KotlinDouble?` → `.doubleValue`. `product.price`는 `Long` → Swift `Int64`로 그대로 보간.

- [ ] **Step 3: 빌드 검증** — Xcode 빌드(⌘B). Expected: 성공. (실동작은 SSE product/gate 이벤트 수신 시 검증.)

- [ ] **Step 4: Commit**

```bash
git add apps/frontend/CashChatIOS/CashChatIOS/ChatScreen.swift
git commit -m "feat(ios): 상품 카드 및 Ad Gate 블라인드 카드 렌더링"
```

---

## Slice F — 진화 화면 + 캐릭터 탭

### Task F1: EvolutionScreen.swift 작성

**Files:**
- Create: `apps/frontend/CashChatIOS/CashChatIOS/EvolutionScreen.swift`

- [ ] **Step 1: EvolutionViewModel + EvolutionScreen 작성**

```swift
import SwiftUI
import CashChatShared

@MainActor
final class EvolutionViewModel: ObservableObject {
    @Published var level: Int = 1
    @Published var isMaxLevel = false
    @Published var nextCost: Int64? = nil
    @Published var nextRate: Double? = nil
    @Published var isAttempting = false
    @Published var resultMessage: String? = nil

    private let store = KoinHelper().evolutionStore()

    func load() {
        Task { @MainActor in
            if let s = try? await store.refresh() { apply(s) }
        }
    }

    private func apply(_ s: EvolutionStateDto) {
        level = Int(s.level)
        isMaxLevel = s.isMaxLevel
        nextCost = s.nextAttemptCost?.int64Value
        nextRate = s.nextSuccessRate?.doubleValue
    }

    func attempt() {
        guard !isAttempting, !isMaxLevel else { return }
        isAttempting = true
        Task { @MainActor in
            defer { isAttempting = false }
            do {
                let r = try await store.attempt()
                resultMessage = r.success ? "진화 성공! Lv.\(r.resultLevel)" : "진화 실패… 다시 도전!"
                if let s = try? await store.refresh() { apply(s) }
            } catch {
                resultMessage = "진화 시도에 실패했어요."
            }
        }
    }
}

struct EvolutionScreen: View {
    @StateObject private var vm = EvolutionViewModel()
    private let accent = Color(red: 0.36, green: 0.42, blue: 0.98)

    var body: some View {
        NavigationStack {
            VStack(spacing: 20) {
                Image(systemName: "sparkles").font(.system(size: 72)).foregroundStyle(accent)
                Text("Lv.\(vm.level)").font(.largeTitle.weight(.black))
                if vm.isMaxLevel {
                    Text("최고 레벨에 도달했어요!").foregroundStyle(.secondary)
                } else {
                    if let rate = vm.nextRate {
                        Text("성공 확률 \(Int(rate * 100))%").foregroundStyle(.secondary)
                    }
                    if let cost = vm.nextCost {
                        Text("비용 🪙\(cost)").font(.subheadline).foregroundStyle(.secondary)
                    }
                    Button(action: { vm.attempt() }) {
                        Text(vm.isAttempting ? "진화 중…" : "진화 시도")
                            .font(.headline).frame(maxWidth: .infinity).padding(.vertical, 14)
                            .background(accent).foregroundStyle(.white).clipShape(Capsule())
                    }
                    .disabled(vm.isAttempting)
                    .padding(.horizontal, 32)
                }
                if let msg = vm.resultMessage {
                    Text(msg).font(.subheadline.weight(.semibold)).foregroundStyle(accent)
                }
                Spacer()
            }
            .padding(.top, 40)
            .navigationTitle("캐릭터 진화")
            .navigationBarTitleDisplayMode(.inline)
            .onAppear { vm.load() }
        }
    }
}
```

> `nextAttemptCost`/`nextSuccessRate`는 Kotlin nullable `Long?`/`Double?` → `KotlinLong?`/`KotlinDouble?` → `.int64Value`/`.doubleValue`.

- [ ] **Step 2: 빌드 검증** — Xcode 빌드(⌘B). Expected: 성공.

- [ ] **Step 3: Commit**

```bash
git add apps/frontend/CashChatIOS/CashChatIOS/EvolutionScreen.swift
git commit -m "feat(ios): 진화 화면(EvolutionStore 연동) 추가"
```

### Task F2: 캐릭터 탭 → 진화 시트 연결

**Files:**
- Modify: `apps/frontend/CashChatIOS/CashChatIOS/ChatScreen.swift`

- [ ] **Step 1: showEvolution 시트 연결**

(Slice B에서 `showEvolution` 상태와 캐릭터 버튼을 이미 추가함.) body의 sheet 블록 아래에 추가:

```swift
        .sheet(isPresented: $showEvolution) {
            EvolutionScreen()
        }
```

- [ ] **Step 2: 빌드 + 시뮬레이터 검증**

Expected: 헤더의 캐릭터/Lv 영역 탭 → 진화 화면 시트, 레벨/성공률/비용 표시, 진화 시도 동작.

- [ ] **Step 3: Commit**

```bash
git add apps/frontend/CashChatIOS/CashChatIOS/ChatScreen.swift
git commit -m "feat(ios): 채팅 헤더 캐릭터 탭 → 진화 화면 연결"
```

---

## Slice G — 마감 (추천 질문 칩 + 대화 내보내기)

### Task G1: 빈 상태 추천 질문 칩

**Files:**
- Modify: `apps/frontend/CashChatIOS/CashChatIOS/ChatScreen.swift`

- [ ] **Step 1: emptyState에 추천 칩 추가**

상단에 상수 추가:

```swift
private let suggestedQuestions = ["오늘 저녁 뭐 먹을까?", "가성비 이어폰 추천해줘", "영어 공부 팁 알려줘"]
```

기존 `emptyState`의 VStack 마지막에 추가:

```swift
            VStack(spacing: 8) {
                ForEach(suggestedQuestions, id: \.self) { q in
                    Button(q) { vm.send(q) }
                        .font(.caption.weight(.semibold))
                        .padding(.horizontal, 12).padding(.vertical, 8)
                        .background(Color(.secondarySystemGroupedBackground))
                        .clipShape(Capsule())
                }
            }
            .padding(.top, 12)
```

- [ ] **Step 2: 빌드 + 시뮬레이터 검증** — 빈 채팅에서 칩 탭 시 전송.

- [ ] **Step 3: Commit**

```bash
git add apps/frontend/CashChatIOS/CashChatIOS/ChatScreen.swift
git commit -m "feat(ios): 채팅 빈 상태 추천 질문 칩"
```

### Task G2: 대화 내보내기 (공유 시트)

**Files:**
- Modify: `apps/frontend/CashChatIOS/CashChatIOS/ChatScreen.swift`

- [ ] **Step 1: 헤더 새 대화 버튼 옆에 공유 버튼 추가**

```swift
            // header의 startNew 버튼 앞에 추가:
            if !vm.items.isEmpty {
                Button { shareItems = exportText(vm.items) } label: {
                    Image(systemName: chatSFSymbol("square.and.arrow.up", fallback: "arrowshape.turn.up.right"))
                        .foregroundStyle(.primary)
                }
            }
```

`ChatScreen`에 상태 추가: `@State private var shareItems: String? = nil`.
body에 추가:

```swift
        .sheet(isPresented: Binding(get: { shareItems != nil }, set: { if !$0 { shareItems = nil } })) {
            if let text = shareItems { ShareSheet(text: text) }
        }
```

- [ ] **Step 2: exportText + ShareSheet 작성**

파일 하단에 추가:

```swift
private func exportText(_ items: [ChatItem]) -> String {
    items.compactMap { item -> String? in
        if let u = item as? ChatItemUserMessage { return "나: \(u.text)" }
        if let a = item as? ChatItemAssistantMessage { return "비서: \(a.text)" }
        return nil
    }.joined(separator: "\n")
}

private struct ShareSheet: UIViewControllerRepresentable {
    let text: String
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: [text], applicationActivities: nil)
    }
    func updateUIViewController(_ vc: UIActivityViewController, context: Context) {}
}
```

- [ ] **Step 3: 빌드 + 시뮬레이터 검증** — 대화 있을 때 공유 버튼 → iOS 공유 시트.

- [ ] **Step 4: Commit**

```bash
git add apps/frontend/CashChatIOS/CashChatIOS/ChatScreen.swift
git commit -m "feat(ios): 대화 내보내기(공유 시트)"
```

---

## 최종 검증

- [ ] `cd apps/frontend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && ./gradlew :shared:embedAndSignAppleFrameworkForXcode` 성공
- [ ] Xcode 시뮬레이터 빌드 성공, 경고 없는 깨끗한 빌드
- [ ] 시뮬레이터 e2e: HUD 표시·출석·진화·추천칩·공유 동작
- [ ] 게이트/광고/카드/재전송은 SSE 경로 동작 확인 후 실기기 종단 검증
- [ ] `project-ios-feature-parity` 메모리 진행 상태 갱신(슬라이스 1c~1e, HUD 완료 반영)

---

## Self-Review 결과

- **Spec 커버리지:** Slice A(AdMob)=Task A1~A4, B(HUD)=B1~B2, C(출석)=C1~C2, D(게이트+광고)=D1~D2, E(카드)=E1~E2, F(진화)=F1~F2, G(마감)=G1~G2. 스펙 전 슬라이스 매핑됨.
- **플레이스홀더:** SDK 심볼 버전차/Secrets ignore 여부 등은 "TODO"가 아니라 실행자가 즉시 판정할 분기로 명시.
- **타입 일관성:** KMM→Swift 변환(KotlinLong?/KotlinDouble?/KotlinBoolean, Int 캐스팅) 각 사용처에 주석으로 명시. `startAdReward`/`startGateUnlock`/`unlockGatedMessage(messageId:)`/`retryBlocked()` 시그니처는 shared 정의(`ChatStore`)와 일치.
