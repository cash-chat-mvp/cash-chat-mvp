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
    @Published var balance: Int64 = 0
    @Published var toast: String? = nil

    private let store = KoinHelper().attendanceStore()
    private let points = KoinHelper().pointsRepository()
    private let collector = FlowCollector()
    private var didLoad = false

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
                self.nextRewardCoin = s.nextReward?.coin ?? 0
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

    /// 오늘이 포함된 주(일~토) 7칸. vm.month 기준 checkedDays로 완료 판정.
    private func weekCells() -> [DayCell] {
        var cal = Calendar(identifier: .gregorian)
        cal.firstWeekday = 1 // Sunday
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

            Text("🎁 오늘 보상 🪙+\(vm.nextRewardCoin)")
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
            .disabled(vm.todayChecked)
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
