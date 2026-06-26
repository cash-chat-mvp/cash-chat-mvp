import SwiftUI

// MARK: - 좌표 측정

/// chatRoot 좌표공간에서 측정한 버블/HUD 프레임을 모으는 프리퍼런스 키.
struct RewardFramePreferenceKey: PreferenceKey {
    static var defaultValue: [String: CGRect] = [:]
    static func reduce(value: inout [String: CGRect], nextValue: () -> [String: CGRect]) {
        value.merge(nextValue(), uniquingKeysWith: { _, new in new })
    }
}

let rewardPointKey = "hud.point"
let rewardExpKey = "hud.exp"
func rewardBubbleKey(_ id: String) -> String { "bubble.\(id)" }

extension View {
    /// chatRoot 좌표공간 기준 프레임을 key 로 보고한다.
    func reportRewardFrame(_ key: String) -> some View {
        background(
            GeometryReader { proxy in
                Color.clear.preference(
                    key: RewardFramePreferenceKey.self,
                    value: [key: proxy.frame(in: .named("chatRoot"))]
                )
            }
        )
    }
}

// MARK: - HUD 칩 (도착 펄스)

/// 보상 토큰 도착 시 1.0→1.15→1.0 펄스하는 HUD 칩.
struct RewardHudChip: View {
    let emoji: String
    let value: String
    let pulse: Int
    @State private var scale: CGFloat = 1

    var body: some View {
        Text("\(emoji) \(value)")
            .font(.caption.weight(.semibold))
            .padding(.horizontal, 8).padding(.vertical, 4)
            .background(Color(.secondarySystemGroupedBackground))
            .clipShape(Capsule())
            .scaleEffect(scale)
            .onChange(of: pulse) { _ in
                withAnimation(.spring(response: 0.2, dampingFraction: 0.4)) { scale = 1.15 }
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.18) {
                    withAnimation(.spring(response: 0.3, dampingFraction: 0.6)) { scale = 1 }
                }
            }
    }
}

// MARK: - 토큰

private let pointAccent = Color(red: 1.0, green: 0.77, blue: 0.24)
private let expAccent = Color(red: 0.56, green: 0.48, blue: 1.0)

/// 완료 보상 토큰 캡슐 — 최소 44pt, 26pt 이모지 + 16pt 굵은 +N.
struct RewardTokenCapsule: View {
    let emoji: String
    let delta: String
    let accent: Color

    var body: some View {
        HStack(spacing: 6) {
            Text(emoji).font(.system(size: 26))
            Text(delta).font(.system(size: 16, weight: .bold)).foregroundStyle(accent)
        }
        .padding(.horizontal, 14)
        .frame(minHeight: 44)
        .background(Color(red: 0.08, green: 0.08, blue: 0.12).opacity(0.82))
        .overlay(Capsule().stroke(accent.opacity(0.9), lineWidth: 1.5))
        .clipShape(Capsule())
        .shadow(color: accent.opacity(0.5), radius: 8)
    }
}

/// 곡선(2차 베지어) 경로 + 팝/페이드를 progress 로 보간하는 모디파이어.
struct RewardTokenTravel: AnimatableModifier {
    var progress: CGFloat
    let start: CGPoint
    let control: CGPoint
    let end: CGPoint

    var animatableData: CGFloat {
        get { progress }
        set { progress = newValue }
    }

    func body(content: Content) -> some View {
        let p = progress
        let one = 1 - p
        let x = one * one * start.x + 2 * one * p * control.x + p * p * end.x
        let y = one * one * start.y + 2 * one * p * control.y + p * p * end.y
        let scale: CGFloat = p < 0.18 ? (0.6 + 0.5 * (p / 0.18)) : (1.1 - 0.25 * ((p - 0.18) / 0.82))
        let opacity: Double = p > 0.85 ? Double(max(0, 1 - (p - 0.85) / 0.15)) : 1
        return content
            .scaleEffect(scale)
            .opacity(opacity)
            .position(x: x, y: y)
    }
}

// MARK: - 오버레이

/// 마지막 AI 답변 버블에서 큰 🪙/⭐ 토큰이 곡선 경로로 상단 HUD 칩까지 약 1.4초 이동하며 흡수된다.
/// 두 토큰은 0.12초 간격으로 출발. 좌표 미측정/Reduce Motion 이면 버블 위치에서 페이드한다(스펙 §3.2).
struct RewardTokenOverlay: View {
    let reward: RewardFeedback?
    let frames: [String: CGRect]
    let reduceMotion: Bool
    let onPointArrived: () -> Void
    let onExpArrived: () -> Void

    @State private var current: RewardFeedback?
    @State private var coinProgress: CGFloat = 0
    @State private var expProgress: CGFloat = 0
    @State private var coinVisible = false
    @State private var expVisible = false

    var body: some View {
        GeometryReader { geo in
            let bubble = current.flatMap { frames[rewardBubbleKey($0.messageId)] }
            let origin = CGPoint(
                x: bubble?.maxX ?? geo.size.width / 2,
                y: bubble?.minY ?? geo.size.height * 0.8
            )
            let pointEnd = frames[rewardPointKey].map { CGPoint(x: $0.midX, y: $0.midY) }
                ?? CGPoint(x: origin.x, y: 44)
            let expEnd = frames[rewardExpKey].map { CGPoint(x: $0.midX, y: $0.midY) }
                ?? CGPoint(x: origin.x, y: 44)
            ZStack {
                if coinVisible, let r = current {
                    RewardTokenCapsule(emoji: "🪙", delta: "+\(r.pointDelta)", accent: pointAccent)
                        .modifier(RewardTokenTravel(
                            progress: reduceMotion ? 0 : coinProgress,
                            start: origin, control: control(origin, pointEnd, 1), end: pointEnd
                        ))
                }
                if expVisible, let r = current {
                    RewardTokenCapsule(emoji: "⭐", delta: "+\(r.expDelta)", accent: expAccent)
                        .modifier(RewardTokenTravel(
                            progress: reduceMotion ? 0 : expProgress,
                            start: origin, control: control(origin, expEnd, -1), end: expEnd
                        ))
                }
            }
            .frame(width: geo.size.width, height: geo.size.height)
        }
        .allowsHitTesting(false)
        // 보상이 짧은 간격으로 연달아 들어와도 .task(id:) 가 이전 연출을 자동 취소하므로
        // 취소 불가능한 타이머가 중첩돼 onPointArrived/onExpArrived 가 중복 호출되는
        // 레이스 컨디션을 방지한다.
        .task(id: reward) {
            guard let r = reward, r != current else { return }
            current = r

            if reduceMotion {
                coinVisible = true; expVisible = true
                onPointArrived()
                try? await Task.sleep(nanoseconds: 120_000_000)
                onExpArrived()
                try? await Task.sleep(nanoseconds: 680_000_000)
                coinVisible = false; expVisible = false
                return
            }

            // 코인 — 즉시 출발 / 별 — 0.12초 뒤 출발, 각각 1.4초 이동
            resetThenAnimate(coin: true)
            try? await Task.sleep(nanoseconds: 120_000_000)
            resetThenAnimate(coin: false)

            try? await Task.sleep(nanoseconds: 1_280_000_000)
            onPointArrived(); coinVisible = false

            try? await Task.sleep(nanoseconds: 120_000_000)
            onExpArrived(); expVisible = false
        }
    }

    private func control(_ a: CGPoint, _ b: CGPoint, _ dir: CGFloat) -> CGPoint {
        CGPoint(x: (a.x + b.x) / 2 + dir * 36, y: min(a.y, b.y) - 90)
    }

    private func resetThenAnimate(coin: Bool) {
        var t = Transaction()
        t.disablesAnimations = true
        withTransaction(t) {
            if coin { coinProgress = 0; coinVisible = true } else { expProgress = 0; expVisible = true }
        }
        withAnimation(.easeInOut(duration: 1.4)) {
            if coin { coinProgress = 1 } else { expProgress = 1 }
        }
    }
}
