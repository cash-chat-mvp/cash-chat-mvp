import SwiftUI
import Combine
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
                resultMessage = r.success ? "진화 성공! Lv.\(Int(r.resultLevel))" : "진화 실패… 다시 도전!"
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
