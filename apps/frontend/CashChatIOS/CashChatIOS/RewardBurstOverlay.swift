import SwiftUI

/// 채팅 완료 보상 연출(충실도 B): tick 변경 시 하단(응답 버블 영역)에서 별/코인 입자가
/// 위쪽(HUD 방향)으로 흘러오르며 페이드. HUD 좌표 추적 없이 동작.
/// seeds 가 비어 있는 idle 상태에서는 아무것도 그리지 않는다(첫 보상 전 잔상 방지).
struct RewardBurstOverlay: View {
    let tick: Int
    @State private var animateTick: Int = 0
    @State private var progress: CGFloat = 0
    @State private var seeds: [CGFloat] = []

    var body: some View {
        GeometryReader { geo in
            ZStack {
                ForEach(seeds.indices, id: \.self) { i in
                    let startX = (0.25 + seeds[i] * 0.5) * geo.size.width
                    let y = geo.size.height * (0.8 - 0.45 * progress)
                    Circle()
                        .fill(seeds[i] > 0.5 ? Color(red: 1.0, green: 0.76, blue: 0.03)
                                             : Color(red: 0.49, green: 0.30, blue: 1.0))
                        .frame(width: 8 + seeds[i] * 8, height: 8 + seeds[i] * 8)
                        .position(x: startX, y: y)
                        .opacity(Double(1 - progress))
                }
            }
        }
        .allowsHitTesting(false)
        .onChange(of: tick) { newValue in
            guard newValue > 0, newValue != animateTick else { return }
            animateTick = newValue
            seeds = (0..<8).map { _ in CGFloat.random(in: 0...1) }
            progress = 0
            withAnimation(.easeOut(duration: 0.9)) { progress = 1 }
        }
    }
}
