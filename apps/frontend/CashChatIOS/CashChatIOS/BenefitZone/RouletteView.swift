import SwiftUI
import Combine
import CashChatShared

@MainActor
final class RouletteViewModel: ObservableObject {
    @Published var status: RouletteStatus? = nil
    @Published var busy = false
    @Published var rotation: Double = 0
    @Published var resultText: String? = nil
    @Published var toast: String? = nil

    private let store = KoinHelper().rouletteStore()
    private let adManager = RewardedAdManager()
    private let collector = FlowCollector()

    func onAppear() {
        adManager.preload()
        collector.collectRouletteStatus(store: store) { [weak self] s in self?.status = s }
        Task { try? await store.refresh() }
    }
    func onDisappear() { collector.cancel() }

    func spin() {
        guard !busy, let s = status, Int(s.availableSpins) > 0 else {
            if Int(status?.availableSpins ?? 0) == 0 { toast = "스핀이 없어요. 광고를 보고 채워보세요" }
            return
        }
        busy = true
        Task { @MainActor in
            defer { busy = false }
            guard let result = try? await store.spin() else { toast = "스핀에 실패했어요"; return }
            let segmentCount = status?.segments.count ?? 8
            let sweep = 360.0 / Double(segmentCount)
            let target = 360.0 * 5 - Double(result.segmentIndex) * sweep
            withAnimation(.easeOut(duration: 2.6)) {
                rotation = rotation - rotation.truncatingRemainder(dividingBy: 360) + target
            }
            resultText = Int(result.awardedEnergy) > 0
                ? "⚡\(Int(result.awardedEnergy)) 에너지 획득!"
                : "아쉽지만 꽝! 다시 도전해요"
        }
    }

    /// 광고 시청 → 스핀 크레딧 적립.
    /// KMP watchAdForSpin(showAd:) 는 suspend 람다 파라미터를 KotlinSuspendFunction1 로 export 하여
    /// Swift async 클로저를 직접 전달할 수 없으므로, RewardAdCardView 와 동일하게 개별 단계를 직접 호출한다.
    func watchAdForSpin() {
        guard !busy else { return }
        busy = true
        Task { @MainActor in
            defer { busy = false }
            guard let s = status else { return }
            let baseline = s.availableSpins

            // nonce 발급(서버 검증용) 후 광고 표시.
            guard let nonce = try? await store.prepareAdSpin() else { return }

            var notReady = false
            let watched: Bool = await withCheckedContinuation { cont in
                var rewarded = false
                adManager.show(
                    nonce: nonce,
                    onRewarded: { _ in rewarded = true },
                    onDismissed: { cont.resume(returning: rewarded) },
                    onNotReady: { notReady = true; cont.resume(returning: false) }
                )
            }

            guard watched else {
                if notReady { toast = "광고를 준비 중이에요. 잠시 후 다시 시도해주세요" }
                return
            }

            // 스핀 크레딧 적립(baseline 대비 증가 판정 + status 갱신).
            let credited = (try? await store.creditAdSpin(baselineAvailable: baseline))?.boolValue ?? false
            if credited { toast = "스핀 1회가 충전됐어요!" }
        }
    }
}

struct RouletteView: View {
    @StateObject private var vm = RouletteViewModel()
    var onClose: () -> Void = {}
    private let indigo = Color(red: 0.36, green: 0.36, blue: 0.84)

    var body: some View {
        VStack(spacing: 14) {
            Text("행운 룰렛").font(.system(size: 20, weight: .heavy))
            if let s = vm.status {
                Text("오늘 \(Int(s.availableSpins))회 가능 · 광고로 +\(Int(s.adSpinsRemaining))")
                    .font(.system(size: 12)).foregroundStyle(.secondary)
            }
            ZStack(alignment: .top) {
                RouletteWheelShape(segments: vm.status?.segments ?? [])
                    .frame(width: 260, height: 260)
                    .rotationEffect(.degrees(vm.rotation))
                Text("▼").font(.system(size: 22, weight: .black)).foregroundStyle(indigo).offset(y: -4)
                Circle().fill(indigo).frame(width: 56, height: 56)
                    .overlay(Text("GO").font(.system(size: 15, weight: .black)).foregroundStyle(.white))
                    .offset(y: 102)
            }
            if let r = vm.resultText { Text(r).font(.system(size: 15, weight: .bold)).foregroundStyle(indigo) }

            let canSpin = Int(vm.status?.availableSpins ?? 0) > 0
            let canWatchAd = Int(vm.status?.adSpinsRemaining ?? 0) > 0
            if canSpin || !canWatchAd {
                Button(action: { vm.spin() }) {
                    Text(canSpin ? "돌리기 · 오늘 \(Int(vm.status?.availableSpins ?? 0))회" : "내일 다시 · 자정 리셋")
                        .frame(maxWidth: .infinity)
                }.buttonStyle(.borderedProminent).disabled(!canSpin || vm.busy)
            } else {
                Button(action: { vm.watchAdForSpin() }) {
                    Text("광고 보고 한 번 더").frame(maxWidth: .infinity)
                }.buttonStyle(.borderedProminent).disabled(vm.busy)
            }
            Button("닫기") { onClose() }.foregroundStyle(.secondary).font(.system(size: 13))
        }
        .padding(20)
        .onAppear { vm.onAppear() }
        .onDisappear { vm.onDisappear() }
        .safeAreaInset(edge: .bottom) {
            if let t = vm.toast {
                Text(t)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 18).padding(.vertical, 12)
                    .background(Color(red: 0.1, green: 0.1, blue: 0.16).opacity(0.92))
                    .clipShape(Capsule())
                    .padding(.bottom, 8)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.easeOut(duration: 0.25), value: vm.toast)
        .onChange(of: vm.toast) { _, newValue in
            guard newValue != nil else { return }
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) { vm.toast = nil }
        }
    }
}

/// 8칸 미니멀 2톤 휠. 칸 0 중심이 회전 0 에서 상단(12시)에 온다.
struct RouletteWheelShape: View {
    let segments: [RouletteSegment]

    private func label(_ p: RoulettePrize) -> String {
        if p == .jackpot100 { return "⚡100" }
        if p == .e10 { return "⚡10" }
        if p == .e3 { return "⚡3" }
        return "꽝"
    }

    var body: some View {
        GeometryReader { geo in
            let n = max(segments.count, 1)
            let sweep = 360.0 / Double(n)
            let r = min(geo.size.width, geo.size.height) / 2
            ZStack {
                ForEach(Array(segments.enumerated()), id: \.offset) { i, seg in
                    let start = -90.0 - sweep / 2 + Double(i) * sweep
                    WedgeShape(startDeg: start, sweepDeg: sweep)
                        .fill(seg.prize == .jackpot100
                              ? Color(red: 1, green: 0.965, blue: 0.874)
                              : (i % 2 == 0 ? Color(red: 1, green: 0.965, blue: 0.874) : .white))
                        .overlay(WedgeShape(startDeg: start, sweepDeg: sweep)
                            .stroke(Color(red: 0.925, green: 0.918, blue: 0.96), lineWidth: 1.5))
                    let mid = (start + sweep / 2) * .pi / 180
                    Text(label(seg.prize))
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(
                            seg.prize == .miss
                                ? Color(red: 0.60, green: 0.58, blue: 0.68)
                                : (seg.prize == .jackpot100
                                    ? Color(red: 0.69, green: 0.49, blue: 0)
                                    : Color(red: 0.11, green: 0.11, blue: 0.16))
                        )
                        .position(x: r + cos(mid) * r * 0.62, y: r + sin(mid) * r * 0.62)
                }
                WedgeShape(startDeg: -90.0 - sweep / 2, sweepDeg: sweep)
                    .stroke(Color(red: 1, green: 0.69, blue: 0.18), lineWidth: 3)
            }
        }
    }
}

struct WedgeShape: Shape {
    let startDeg: Double
    let sweepDeg: Double

    func path(in rect: CGRect) -> Path {
        var p = Path()
        let c = CGPoint(x: rect.midX, y: rect.midY)
        p.move(to: c)
        p.addArc(center: c, radius: rect.width / 2,
                 startAngle: .degrees(startDeg),
                 endAngle: .degrees(startDeg + sweepDeg),
                 clockwise: false)
        p.closeSubpath()
        return p
    }
}
