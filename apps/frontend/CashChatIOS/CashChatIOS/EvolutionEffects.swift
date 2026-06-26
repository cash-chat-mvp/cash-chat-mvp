import SwiftUI
import CashChatShared

/// 등급별 강조 색 — 색 외에도 항상 라벨을 함께 노출한다(접근성).
func evolutionGradeColor(_ grade: TimingGrade?) -> Color {
    if grade == TimingGrade.perfect { return Color(red: 1.0, green: 0.77, blue: 0.24) }   // 금색
    if grade == TimingGrade.great { return Color(red: 0.61, green: 0.42, blue: 1.0) }      // 보라
    return Color(red: 0.49, green: 0.53, blue: 0.60)                                       // 중립
}

func evolutionGradeLabel(_ grade: TimingGrade?) -> String {
    if grade == TimingGrade.perfect { return "PERFECT +10%p" }
    if grade == TimingGrade.great { return "GREAT +5%p" }
    if grade == TimingGrade.normal { return "NORMAL" }
    return ""
}

/// 성공 시 방사형 파티클. Reduce Motion에서는 사용하지 않는다.
struct EvolutionSuccessParticles: View {
    let color: Color
    @State private var progress: CGFloat = 0
    private let seeds: [(angle: Double, speed: CGFloat, size: CGFloat)] = (0..<48).map { _ in
        (Double.random(in: 0..<(2 * .pi)), CGFloat.random(in: 0.4...1.0), CGFloat.random(in: 3...7))
    }

    var body: some View {
        GeometryReader { geo in
            let center = CGPoint(x: geo.size.width / 2, y: geo.size.height * 0.36)
            let reach = min(geo.size.width, geo.size.height) * 0.5
            ZStack {
                ForEach(0..<seeds.count, id: \.self) { i in
                    let s = seeds[i]
                    Circle()
                        .fill(color.opacity(Double(1 - progress)))
                        .frame(width: s.size, height: s.size)
                        .position(
                            x: center.x + cos(s.angle) * Double(progress * s.speed * reach),
                            y: center.y + sin(s.angle) * Double(progress * s.speed * reach)
                        )
                }
            }
        }
        .allowsHitTesting(false)
        .onAppear { withAnimation(.easeOut(duration: 1.2)) { progress = 1 } }
    }
}
