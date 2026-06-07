import Foundation
import SwiftUI
import Combine
import CashChatShared

@MainActor
final class AttendanceViewModel: ObservableObject {
    @Published var month: Int = 0
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

    private let columns = Array(repeating: GridItem(.flexible(), spacing: 8), count: 7)

    private var todayNum: Int {
        vm.checkedDays.max().map { vm.todayChecked ? $0 : $0 + 1 } ?? 1
    }

    private func daysInMonth(_ month: Int) -> Int {
        var comp = DateComponents(); comp.year = 2026; comp.month = month
        guard month >= 1, month <= 12, let date = Calendar.current.date(from: comp),
              let range = Calendar.current.range(of: .day, in: .month, for: date) else { return 31 }
        return range.count
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("\(vm.month)월 출석체크")
                .font(.system(size: 18, weight: .black))

            LazyVGrid(columns: columns, spacing: 8) {
                ForEach(1...daysInMonth(vm.month), id: \.self) { day in
                    dayCell(for: day)
                }
            }

            HStack {
                Text("오늘 보상: 🪙+\(vm.nextRewardCoin)")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.secondary)
                Spacer()
                Button(vm.todayChecked ? "오늘 출석 완료" : "출석 도장 찍기") {
                    vm.checkIn()
                }
                .buttonStyle(.borderedProminent)
                .tint(vm.todayChecked ? .gray : Color(red: 0.36, green: 0.42, blue: 0.98))
                .disabled(vm.todayChecked)
            }
        }
        .padding(18)
        .background(
            LinearGradient(
                colors: [Color(red: 0.91, green: 0.88, blue: 1.0), Color(red: 0.98, green: 0.98, blue: 1.0)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        )
        .clipShape(RoundedRectangle(cornerRadius: 20))
    }

    @ViewBuilder
    private func dayCell(for day: Int) -> some View {
        let isChecked = vm.checkedDays.contains(day)
        let isToday = day == todayNum

        Text("\(day)")
            .font(.system(size: 13, weight: .bold))
            .foregroundStyle(isChecked || isToday ? .white : .secondary)
            .frame(maxWidth: .infinity)
            .frame(height: 32)
            .background(cellColor(isChecked: isChecked, isToday: isToday))
            .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    private func cellColor(isChecked: Bool, isToday: Bool) -> Color {
        if isChecked {
            return Color(red: 0.36, green: 0.42, blue: 0.98) // #5C6BFA
        } else if isToday {
            return Color(red: 1.0, green: 0.72, blue: 0.0) // #FFB800
        } else {
            return Color(red: 0.88, green: 0.86, blue: 0.94) // #E0DCEF
        }
    }
}
