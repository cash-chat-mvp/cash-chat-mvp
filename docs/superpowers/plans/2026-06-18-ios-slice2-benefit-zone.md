# iOS Slice 2 — 혜택존(리워드 탭) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development 또는 executing-plans. 체크박스(`- [ ]`) 추적.

**Goal:** iOS 리워드 탭의 목업을 **dev iOS 혜택존 UI**로 교체하고 브랜치 데이터 레이어(AttendanceStore/PointsRepository)에 연결한다 — 출석 위젯(주간 7일 히어로), 코인 잔액, 출석 체크인, 혜택 소개 카드.

**Architecture:** dev의 `BenefitZone/AttendanceViewModel.swift`(AttendanceViewModel + AttendanceWidgetView + BenefitInfoCardView)는 우리 브랜치에 이미 있는 `KoinHelper`/`FlowCollector` API를 그대로 쓰므로 **거의 그대로 이식**한다. dev의 `RewardsView` 본체를 standalone `BenefitZoneScreen` 으로 만들고, 탭 컨테이너의 리워드 탭을 교체한다. 탭/네비 골격 유지. **비-SSE라 서버 SSE 이슈와 무관하게 동작.**

**Tech Stack:** KMM, Koin, SwiftUI, Combine.

## 검증된 사실
- 브랜치 `AttendanceStore`: `state: StateFlow<AttendanceUiState>`, `rewardEvents`, `loadMonthly(year,month)`, `checkIn()`. `AttendanceUiState{month, checkedDays:List<Int>, currentStreak, todayChecked, isCheckingIn, nextReward:RewardPreviewDto?, errorMessage}`.
- `RewardPreviewDto{dayCount, coin:Long, bonusItems:List<BonusItemDto>}`, `BonusItemDto{itemCode:String, quantity:Int}`. `CheckInRewardEvent{awardedCoin:Long, bonusItems}`.
- `PointsRepository.balance: StateFlow<Long>`.
- 브랜치에 이미 존재(Slice 0/1b): `KoinHelper().attendanceStore()/pointsRepository()`, `FlowCollector.collectAttendance/collectRewards/collectBalance`.
- dev `BenefitZone/AttendanceViewModel.swift` 가 위 API/필드(`s.nextReward?.coin`, `bonusItems[].itemCode/.quantity`, `value.int64Value`, `checkedDays.map{$0.intValue}`)를 사용 — **전부 호환 확인됨**.
- 현재 리워드 탭: `MainTabContainer` 가 `RewardsView()`(목업) 사용.
- 빌드: `export JAVA_HOME="$(/usr/libexec/java_home -v 21)"` (셸에서 java_home -v 21 실패 시 Android Studio JBR: `/Applications/Android Studio.app/Contents/jbr/Contents/Home`).

## File Structure
- Create: `apps/frontend/CashChatIOS/CashChatIOS/BenefitZone/AttendanceViewModel.swift` — dev 파일 이식(AttendanceViewModel + AttendanceWidgetView + BenefitInfoCardView).
- Create: `apps/frontend/CashChatIOS/CashChatIOS/BenefitZoneScreen.swift` — dev RewardsView 본체의 standalone 버전.
- Modify: `apps/frontend/CashChatIOS/CashChatIOS/ContentView.swift` — 리워드 탭 `RewardsView()` → `BenefitZoneScreen()`.

기존 목업 `RewardsView`/`RewardAdModalView` 는 남겨둔다(미사용, 후속 정리). Xcode 파일시스템 동기화 그룹이 하위폴더(BenefitZone/)를 자동 포함한다.

---

### Task 1: `BenefitZone/AttendanceViewModel.swift` 이식

**Files:** Create `apps/frontend/CashChatIOS/CashChatIOS/BenefitZone/AttendanceViewModel.swift`

- [ ] **Step 1: dev 파일 그대로 생성** (아래 전체 내용)

```swift
import Foundation
import SwiftUI
import Combine
import CashChatShared

@MainActor
final class AttendanceViewModel: ObservableObject {
    @Published var month: Int = 0
    @Published var streak: Int = 0
    @Published var checkedDays: Set<Int> = []
    @Published var todayChecked = false
    @Published var nextRewardCoin: Int64 = 0
    @Published var nextRewardBonus: String = ""
    @Published var balance: Int64 = 0
    @Published var toast: String? = nil
    @Published var isCheckingIn = false

    private let store = KoinHelper().attendanceStore()
    private let points = KoinHelper().pointsRepository()
    private let collector = FlowCollector()
    private var didLoad = false
    private var toastDismissTask: DispatchWorkItem?

    deinit {
        collector.cancel()
        toastDismissTask?.cancel()
    }

    func scheduleToastDismiss(after seconds: Double = 2) {
        toastDismissTask?.cancel()
        let task = DispatchWorkItem { [weak self] in self?.toast = nil }
        toastDismissTask = task
        DispatchQueue.main.asyncAfter(deadline: .now() + seconds, execute: task)
    }

    func load() {
        store.loadMonthly(year: nil, month: nil)
        guard !didLoad else { return }
        didLoad = true

        collector.collectAttendance(store: store) { [weak self] s in
            Task { @MainActor in
                guard let self else { return }
                self.checkedDays = Set(s.checkedDays.map { $0.intValue })
                self.month = Int(s.month)
                self.streak = Int(s.currentStreak)
                self.todayChecked = s.todayChecked
                self.isCheckingIn = s.isCheckingIn
                self.nextRewardCoin = s.nextReward?.coin ?? 0
                self.nextRewardBonus = (s.nextReward?.bonusItems ?? [])
                    .map { "📦 \($0.itemCode) \($0.quantity)개" }
                    .joined(separator: " ")
                if let err = s.errorMessage {
                    self.toast = err
                }
            }
        }

        collector.collectRewards(store: store) { [weak self] ev in
            Task { @MainActor in
                guard let self else { return }
                self.toast = "출석 완료! 🪙+\(ev.awardedCoin)"
            }
        }

        collector.collectBalance(repo: points) { [weak self] value in
            Task { @MainActor in
                guard let self else { return }
                self.balance = value.int64Value
            }
        }
    }

    func checkIn() {
        store.checkIn()
    }
}

struct AttendanceWidgetView: View {
    @ObservedObject var vm: AttendanceViewModel

    private let dayLabels = ["일", "월", "화", "수", "목", "금", "토"]
    private let heroStart = Color(red: 0.36, green: 0.42, blue: 0.98)
    private let heroEnd = Color(red: 0.52, green: 0.40, blue: 0.98)
    private let accent = Color(red: 1.0, green: 0.72, blue: 0.0)

    private struct DayCell {
        let dayOfMonth: Int
        let inMonth: Bool
        let checked: Bool
        let isToday: Bool
    }

    private func weekCells() -> [DayCell] {
        var cal = Calendar(identifier: .gregorian)
        cal.firstWeekday = 1
        cal.timeZone = TimeZone(identifier: "Asia/Seoul") ?? cal.timeZone
        let now = Date()
        let todayComps = cal.dateComponents([.year, .month, .day], from: now)
        let dispMonth = vm.month > 0 ? vm.month : (todayComps.month ?? 1)
        guard let weekInterval = cal.dateInterval(of: .weekOfYear, for: now) else { return [] }
        var result: [DayCell] = []
        for i in 0..<7 {
            guard let date = cal.date(byAdding: .day, value: i, to: weekInterval.start) else { continue }
            let c = cal.dateComponents([.year, .month, .day], from: date)
            let m = c.month ?? 0
            let d = c.day ?? 0
            let inMonth = (m == dispMonth)
            result.append(DayCell(
                dayOfMonth: d,
                inMonth: inMonth,
                checked: inMonth && vm.checkedDays.contains(d),
                isToday: (c.year == todayComps.year && m == todayComps.month && d == todayComps.day)
            ))
        }
        return result
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text("🔥 \(vm.streak)일 연속 출석").font(.system(size: 15, weight: .heavy)).foregroundStyle(.white)
                Spacer()
                Text("\(vm.month)월").font(.system(size: 12)).foregroundStyle(.white.opacity(0.8))
            }

            HStack(spacing: 0) {
                ForEach(Array(weekCells().enumerated()), id: \.offset) { idx, cell in
                    VStack(spacing: 6) {
                        Text(dayLabels[idx])
                            .font(.system(size: 10, weight: cell.isToday ? .heavy : .medium))
                            .foregroundStyle(cell.isToday ? accent : .white.opacity(0.7))
                        ZStack {
                            Circle().fill(cell.checked ? Color.white : (cell.isToday ? accent : Color.white.opacity(0.18)))
                                .frame(width: 30, height: 30)
                            Text(cell.checked ? "✓" : "\(cell.dayOfMonth)")
                                .font(.system(size: cell.checked ? 14 : 11, weight: .bold))
                                .foregroundStyle(cell.checked ? heroStart : (cell.isToday ? Color(red:0.1,green:0.1,blue:0.16) : .white))
                        }
                    }
                    .frame(maxWidth: .infinity)
                }
            }

            Text("🎁 \(vm.todayChecked ? "다음 보상" : "오늘 보상") 🪙+\(vm.nextRewardCoin)" + (vm.nextRewardBonus.isEmpty ? "" : "  \(vm.nextRewardBonus)"))
                .font(.system(size: 12.5))
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 12).padding(.vertical, 10)
                .background(Color.white.opacity(0.16))
                .clipShape(RoundedRectangle(cornerRadius: 12))

            Button(action: { vm.checkIn() }) {
                Text(vm.todayChecked ? "오늘 출석 완료" : "출석 도장 찍기")
                    .font(.system(size: 15, weight: .heavy))
                    .foregroundStyle(Color(red: 0.1, green: 0.1, blue: 0.16))
                    .frame(maxWidth: .infinity, minHeight: 50)
                    .background(vm.todayChecked ? Color.white.opacity(0.25) : accent)
                    .clipShape(Capsule())
            }
            .disabled(vm.todayChecked || vm.isCheckingIn)
        }
        .padding(18)
        .background(LinearGradient(colors: [heroStart, heroEnd], startPoint: .topLeading, endPoint: .bottomTrailing))
        .clipShape(RoundedRectangle(cornerRadius: 22))
    }
}

struct BenefitInfoCardView: View {
    enum Badge { case next, soon
        var text: String { self == .next ? "곧 출시" : "준비중" }
        var bg: Color { self == .next ? Color(red:0.89,green:0.94,blue:1.0) : Color(red:0.94,green:0.93,blue:0.97) }
        var fg: Color { self == .next ? Color(red:0.18,green:0.44,blue:0.88) : Color(red:0.60,green:0.58,blue:0.68) }
    }
    let icon: String
    let title: String
    let badge: Badge
    let description: String
    let dimmed: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 7) {
            HStack(spacing: 8) {
                Text(icon).font(.system(size: 18))
                Text(title).font(.system(size: 15, weight: .bold)).foregroundStyle(Color(red:0.1,green:0.1,blue:0.16))
                Text(badge.text).font(.system(size: 10, weight: .bold)).foregroundStyle(badge.fg)
                    .padding(.horizontal, 8).padding(.vertical, 2)
                    .background(badge.bg).clipShape(Capsule())
            }
            Text(description).font(.system(size: 12.5)).foregroundStyle(Color(red:0.42,green:0.41,blue:0.47))
        }
        .padding(15)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.systemBackground))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(Color(red:0.94,green:0.93,blue:0.97), lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .opacity(dimmed ? 0.72 : 1.0)
    }
}
```

- [ ] **Step 2: KMM은 변경 없음** — Swift만 추가하므로 컴파일은 Task 3 Xcode 빌드에서.

---

### Task 2: `BenefitZoneScreen.swift` 생성 (혜택존 화면)

**Files:** Create `apps/frontend/CashChatIOS/CashChatIOS/BenefitZoneScreen.swift`

- [ ] **Step 1: 파일 생성** (dev RewardsView 본체의 standalone 버전)

```swift
import SwiftUI

struct BenefitZoneScreen: View {
    @StateObject private var attendanceVM = AttendanceViewModel()
    @State private var animateIn = false

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                HStack {
                    Text("혜택존").font(.system(size: 22, weight: .heavy))
                    Spacer()
                    Text("🪙 \(attendanceVM.balance)")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(Color(red: 0.69, green: 0.49, blue: 0.0))
                        .padding(.horizontal, 11).padding(.vertical, 5)
                        .background(Color(red: 1.0, green: 0.97, blue: 0.90))
                        .clipShape(Capsule())
                }
                .padding(.horizontal, 20)
                .padding(.top, 8)

                AttendanceWidgetView(vm: attendanceVM)
                    .padding(.horizontal, 16)

                BenefitInfoCardView(icon: "📺", title: "리워드 광고", badge: .next,
                    description: "광고 1회 시청 → 🪙+40 코인 · 하루 10회까지", dimmed: false)
                    .padding(.horizontal, 16)
                BenefitInfoCardView(icon: "🎯", title: "데일리 미션", badge: .soon,
                    description: "매일 바뀌는 3가지 미션을 완료하고 코인 적립", dimmed: true)
                    .padding(.horizontal, 16)
                BenefitInfoCardView(icon: "🎮", title: "TNK 오퍼월", badge: .soon,
                    description: "앱 설치·설문 참여로 대량 코인 (최대 🪙+1,500)", dimmed: true)
                    .padding(.horizontal, 16)
            }
            .padding(.bottom, 16)
        }
        .background(Color(.systemGroupedBackground))
        .safeAreaInset(edge: .bottom) {
            if let toast = attendanceVM.toast {
                Text(toast)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 18)
                    .padding(.vertical, 12)
                    .background(Color(red: 0.1, green: 0.1, blue: 0.16).opacity(0.92))
                    .clipShape(Capsule())
                    .padding(.bottom, 8)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.easeOut(duration: 0.25), value: attendanceVM.toast)
        .onChange(of: attendanceVM.toast) { _, newValue in
            guard newValue != nil else { return }
            attendanceVM.scheduleToastDismiss()
        }
        .opacity(animateIn ? 1 : 0)
        .offset(y: animateIn ? 0 : 14)
        .animation(.easeOut(duration: 0.34), value: animateIn)
        .onAppear {
            animateIn = false
            attendanceVM.load()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.03) {
                withAnimation(.easeOut(duration: 0.34)) { animateIn = true }
            }
        }
        .onDisappear { animateIn = false }
    }
}
```

---

### Task 3: 리워드 탭 교체 + 빌드 + 런타임

**Files:** Modify `apps/frontend/CashChatIOS/CashChatIOS/ContentView.swift`

- [ ] **Step 1: `MainTabContainer` 리워드 탭 교체**

변경 전:
```swift
                RewardsView()
                    .tabItem { Label(MainTab.rewards.rawValue, systemImage: MainTab.rewards.icon) }
                    .tag(MainTab.rewards)
```
변경 후:
```swift
                BenefitZoneScreen()
                    .tabItem { Label(MainTab.rewards.rawValue, systemImage: MainTab.rewards.icon) }
                    .tag(MainTab.rewards)
```

- [ ] **Step 2: Xcode ⌘B (사용자)** — `AttendanceViewModel`/`AttendanceWidgetView`/`BenefitInfoCardView`/`BenefitZoneScreen` 컴파일. 빌드 단계가 프레임워크 임베드 자동 수행.

- [ ] **Step 3: 런타임 (사용자, 실서버)** — 리워드 탭:
  - 혜택존 화면 표시(주간 7일 히어로 위젯 + 코인 잔액 칩 + 혜택 소개 카드 3개)
  - "출석 도장 찍기" → 출석 처리 → 토스트("출석 완료! 🪙+N") + 코인 잔액 증가 + 위젯에 ✓ 반영
  - 이미 출석한 날은 "오늘 출석 완료"로 비활성

- [ ] **Step 4: 회귀** — 채팅/상점/마이페이지 탭 정상.

---

## Self-Review (작성자 점검 완료)
- **Spec 커버리지:** spec Slice 2(혜택존 = dev iOS UID + 브랜치 데이터 레이어 재배선) 구현. 리워드 광고/미션/오퍼월 카드는 dev와 동일하게 "준비중" placeholder.
- **플레이스홀더:** 없음. 전체 Swift 코드 포함.
- **타입 일관성:** dev 코드가 사용하는 `attendanceStore()/pointsRepository()`, `collectAttendance/Rewards/Balance`, `nextReward.coin`, `bonusItems.itemCode/quantity`, `balance int64Value` 모두 브랜치 헤더와 일치 확인.
- **리스크:** 거의 없음(비-SSE, dev 코드 호환 확인). Xcode `onChange(of:)` 2-파라미터 시그니처는 iOS 17+; 프로젝트 타겟이 iOS 17 미만이면 1-파라미터로 조정.
