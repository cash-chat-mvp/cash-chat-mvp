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
        // 무한 collect 코루틴이 살아남지 않도록 구독을 명시적으로 취소한다(메모리 누수 방지).
        collector.cancel()
        toastDismissTask?.cancel()
    }

    /// 토스트 자동 해제를 예약한다. 연속 토스트 시 이전 타이머를 취소해 타이머 누적을 방지한다.
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
                    .map { "\($0.itemCode) \($0.quantity)개" }
                    .joined(separator: " · ")
                if let err = s.errorMessage {
                    self.toast = err
                }
            }
        }

        collector.collectRewards(store: store) { [weak self] ev in
            Task { @MainActor in
                guard let self else { return }
                self.toast = "출석 완료! +\(ev.awardedCoin) 코인"
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

    /// 오늘이 포함된 주(일~토) 7칸. vm.month 기준 checkedDays로 완료 판정.
    private func weekCells() -> [DayCell] {
        var cal = Calendar(identifier: .gregorian)
        cal.firstWeekday = 1 // Sunday
        // 서버 출석 판정 기준 시간대(Asia/Seoul)로 '오늘'을 계산해 기기 시간대와 무관하게 일치시킨다.
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
                HStack(spacing: 5) {
                    Image(systemName: "flame.fill").foregroundStyle(accent)
                    Text("\(vm.streak)일 연속 출석")
                }
                .font(.system(size: 15, weight: .heavy))
                .foregroundStyle(.white)
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
                            Group {
                                if cell.checked {
                                    Image(systemName: "checkmark").font(.system(size: 13, weight: .bold))
                                } else {
                                    Text("\(cell.dayOfMonth)").font(.system(size: 11, weight: .bold))
                                }
                            }
                            .foregroundStyle(cell.checked ? heroStart : (cell.isToday ? Color(red:0.1,green:0.1,blue:0.16) : .white))
                        }
                    }
                    .frame(maxWidth: .infinity)
                }
            }

            // 이미 출석했다면 nextRewardCoin 은 '다음' 출석 보상이므로 라벨을 구분한다.
            HStack(spacing: 6) {
                Image(systemName: "gift.fill").foregroundStyle(accent)
                (
                    Text("\(vm.todayChecked ? "다음 보상" : "오늘 보상")  ")
                    + Text(Image(systemName: "bitcoinsign.circle.fill"))
                    + Text(" +\(vm.nextRewardCoin)")
                    + Text(vm.nextRewardBonus.isEmpty ? "" : "   \(vm.nextRewardBonus)")
                )
            }
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
            // Android(AttendanceWidget)와 동일하게 체크인 진행 중에도 비활성화해 중복 클릭을 막는다.
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
    let icon: String   // SF Symbol 이름
    let title: String
    let badge: Badge
    let description: String
    let dimmed: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 7) {
            HStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.system(size: 17))
                    .foregroundStyle(Color(red: 0.36, green: 0.42, blue: 0.98))
                    .frame(width: 24)
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
