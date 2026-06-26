import SwiftUI

/// 사용자 요청 버블 옆 에너지 차감 피드백 모델.
struct EnergyFeedback: Equatable {
    let eventId: Int64
    let messageId: String
    let amount: Int
}

/// 완료 보상(🪙/⭐) 토큰 연출 모델.
struct RewardFeedback: Equatable {
    let eventId: Int64
    let messageId: String
    let pointDelta: Int64
    let expDelta: Int64
}

/// 사용자 요청 버블 우상단의 `⚡ -1` 차감 배지.
/// 0.15초 등장(위로 8pt) → 0.5초 유지 → 0.25초 페이드아웃(총 ~0.9초). eventId 로 1회성 재생.
struct ResourceDeltaBadge: View {
    let eventId: Int64
    let amount: Int
    @State private var shown = false

    var body: some View {
        Text("⚡ \(amount)")
            .font(.caption.weight(.semibold))
            .padding(.horizontal, 8).padding(.vertical, 5)
            .frame(minHeight: 28)
            .background(Color.orange.opacity(0.9))
            .foregroundStyle(.white)
            .clipShape(Capsule())
            .shadow(radius: 1, y: 1)
            .opacity(shown ? 1 : 0)
            .offset(y: shown ? 0 : 8)
            .onAppear { runCycle() }
            .onChange(of: eventId) { _ in runCycle() }
    }

    private func runCycle() {
        shown = false
        withAnimation(.easeOut(duration: 0.15)) { shown = true }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.65) {
            withAnimation(.easeIn(duration: 0.25)) { shown = false }
        }
    }
}
