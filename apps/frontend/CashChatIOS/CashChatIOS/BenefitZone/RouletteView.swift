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

    /// 무료 첫 스핀.
    func spin() {
        guard !busy, let s = status, s.freeSpinAvailable else { return }
        busy = true
        Task { @MainActor in
            defer { busy = false }
            guard let result = try? await store.spin() else { toast = "스핀에 실패했어요"; return }
            animate(result)
        }
    }

    /// 당첨 칸이 상단 포인터에 멈추도록 휠을 회전시키고 결과 문구를 표시.
    private func animate(_ result: RouletteSpinResult) {
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

    /// 광고 게이트 스핀: 광고를 보고 끝까지 시청하면 즉시 스핀.
    /// KMP suspend-lambda 파라미터는 Swift 클로저로 못 넘기므로 prepareAdSpin → 광고 → spinWithAd 를 직접 호출한다.
    func spinWithAd() {
        guard !busy, let s = status, Int(s.remaining) > 0 else {
            if Int(status?.remaining ?? 0) == 0 { toast = "오늘 룰렛을 다 돌렸어요. 자정에 리셋돼요" }
            return
        }
        busy = true
        Task { @MainActor in
            defer { busy = false }
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

            // 광고 시청 완료 → 즉시 스핀.
            guard let result = try? await store.spinWithAd() else { toast = "스핀에 실패했어요"; return }
            animate(result)
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
                Text(s.freeSpinAvailable ? "오늘 \(Int(s.remaining))회 · 첫 회 무료!"
                                         : "오늘 \(Int(s.remaining))회 남음 · 광고 보고 돌리기")
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

            let remaining = Int(vm.status?.remaining ?? 0)
            let freeAvailable = vm.status?.freeSpinAvailable ?? false
            if remaining <= 0 {
                Button(action: {}) { Text("내일 다시 · 자정 리셋").frame(maxWidth: .infinity) }
                    .buttonStyle(.borderedProminent).disabled(true)
            } else if freeAvailable {
                Button(action: { vm.spin() }) { Text("돌리기 (무료)").frame(maxWidth: .infinity) }
                    .buttonStyle(.borderedProminent).disabled(vm.busy)
            } else {
                Button(action: { vm.spinWithAd() }) { Text("광고 보고 돌리기").frame(maxWidth: .infinity) }
                    .buttonStyle(.borderedProminent).disabled(vm.busy)
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
