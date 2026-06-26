import SwiftUI
import CashChatShared

/// 타이밍 게이지 — 누르는 동안 마커가 좌우로 순환하며 PERFECT/GREAT 구간에서 떼면 보너스.
/// 순수 표시 컴포넌트로 position/predictedGrade는 ViewModel이 계산해 내려준다.
struct EvolutionTimingGauge: View {
    let window: TimingWindow
    let position: CGFloat
    let predictedGrade: TimingGrade?
    let active: Bool

    var body: some View {
        VStack(spacing: 6) {
            GeometryReader { geo in
                let w = geo.size.width
                let trackH: CGFloat = 12
                ZStack(alignment: .leading) {
                    Capsule().fill(Color(.systemGray5)).frame(height: trackH)
                    Rectangle()
                        .fill(Color(red: 0.61, green: 0.42, blue: 1.0).opacity(0.35))
                        .frame(width: CGFloat(window.greatEnd - window.greatStart) * w, height: trackH)
                        .offset(x: CGFloat(window.greatStart) * w)
                    Rectangle()
                        .fill(Color(red: 1.0, green: 0.77, blue: 0.24).opacity(0.55))
                        .frame(width: CGFloat(window.perfectEnd - window.perfectStart) * w, height: trackH)
                        .offset(x: CGFloat(window.perfectStart) * w)
                    if active {
                        Circle()
                            .fill(evolutionGradeColor(predictedGrade))
                            .overlay(Circle().fill(.white).padding(4))
                            .overlay(Circle().fill(evolutionGradeColor(predictedGrade)).padding(7))
                            .frame(width: 26, height: 26)
                            .offset(x: min(max(position, 0), 1) * w - 13)
                    }
                }
                .frame(height: 28)
            }
            .frame(height: 28)
            Text(active && predictedGrade != nil ? evolutionGradeLabel(predictedGrade) : "꾹 누르고 중앙에서 떼면 보너스!")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(active ? evolutionGradeColor(predictedGrade) : Color.secondary)
        }
    }
}
