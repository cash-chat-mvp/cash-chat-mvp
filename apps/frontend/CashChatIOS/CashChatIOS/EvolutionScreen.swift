import SwiftUI
import Combine
import QuartzCore
import CashChatShared

@MainActor
final class EvolutionViewModel: ObservableObject {
    enum ScreenState: Equatable {
        case loading
        case loadError(String)
        case content
    }
    enum Phase { case idle, charging, resolving, result }

    @Published var screenState: ScreenState = .loading
    @Published var phase: Phase = .idle
    @Published var level: Int = 1
    @Published var isMaxLevel = false
    @Published var nextCost: Int64? = nil
    @Published var nextRate: Double? = nil
    @Published var currentExp: Int64? = nil
    @Published var capability: TimingCapability = .unknown
    @Published var timingPosition: CGFloat = 0
    @Published var predictedGrade: TimingGrade? = nil
    @Published var result: EvolutionAttemptDto? = nil
    @Published var errorMessage: String? = nil

    private let store = KoinHelper().evolutionStore()
    private let hudStore = KoinHelper().hudStore()

    private var holdStartedAt: CFTimeInterval = 0
    private var tickerTask: Task<Void, Never>? = nil

    var canAfford: Bool {
        guard let cost = nextCost else { return true }
        if let exp = currentExp { return exp >= cost }
        return true
    }

    /// 게이지 구성용 현재 타이밍 세션
    var timingSession: TimingSessionDto? { store.timingSession.value as? TimingSessionDto }

    func load() {
        screenState = .loading
        Task { @MainActor in
            do {
                let s = try await store.refresh()
                apply(s)
                screenState = .content
            } catch {
                screenState = .loadError("진화 정보를 불러오지 못했어요. 다시 시도해주세요")
                return
            }
            // capability 감지는 콘텐츠 표시를 막지 않도록 별도로 수행
            let cap = (try? await store.detectTimingCapability()) ?? TimingCapability.unsupported
            capability = cap
        }
    }

    private func apply(_ s: EvolutionStateDto) {
        level = Int(s.level)
        isMaxLevel = s.isMaxLevel
        nextCost = s.nextAttemptCost?.int64Value
        nextRate = s.nextSuccessRate?.doubleValue
        currentExp = s.currentExp?.int64Value
    }

    private func window() -> TimingWindow? {
        guard let session = store.timingSession.value as? TimingSessionDto else { return nil }
        return TimingWindow(
            minimumHoldMs: session.minimumHoldMs,
            cycleDurationMs: session.cycleDurationMs,
            perfectStart: 0.45, perfectEnd: 0.55,
            greatStart: 0.38, greatEnd: 0.62
        )
    }

    // MARK: 길게 누르기 타이밍

    func beginHold() {
        guard phase == .idle, capability == .supported, let w = window() else { return }
        holdStartedAt = CACurrentMediaTime()
        phase = .charging
        timingPosition = 0
        predictedGrade = .normal
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        tickerTask?.cancel()
        // [weak self] 로 캡처해 뷰가 사라져도(누른 채 이탈 등) 순환 참조로 인한
        // 메모리 누수·백그라운드 루프가 생기지 않게 한다.
        tickerTask = Task { @MainActor [weak self] in
            while let self, !Task.isCancelled, self.phase == .charging {
                let elapsedMs = Int64((CACurrentMediaTime() - self.holdStartedAt) * 1000)
                let pos = self.position(elapsedMs, w)
                self.timingPosition = CGFloat(pos)
                self.predictedGrade = EvolutionTimingKt.localTimingGrade(position: pos, window: w)
                try? await Task.sleep(nanoseconds: 16_000_000)
            }
        }
    }

    func cancelHold() {
        tickerTask?.cancel()
        phase = .idle
        timingPosition = 0
        predictedGrade = nil
    }

    func releaseHold() {
        guard phase == .charging else { return }
        tickerTask?.cancel()
        let elapsedMs = Int64((CACurrentMediaTime() - holdStartedAt) * 1000)
        // 0.6초 이전 해제는 취소 — 경험치를 소모하지 않는다.
        guard let w = window(), elapsedMs >= w.minimumHoldMs,
              let session = store.timingSession.value as? TimingSessionDto else {
            cancelHold()
            return
        }
        runAttempt(TimingAttempt(sessionId: session.sessionId, releasedAtMs: elapsedMs))
    }

    /// 타이밍 미지원(폴백) — 기존 기본 확률 시도
    func attemptLegacy() {
        guard phase == .idle else { return }
        runAttempt(nil)
    }

    private func runAttempt(_ timing: TimingAttempt?) {
        phase = .resolving
        Task { @MainActor in
            do {
                let r = try await store.attempt(timing: timing)
                result = r
                phase = .result
                UINotificationFeedbackGenerator().notificationOccurred(r.success ? .success : .warning)
                if let s = try? await store.refresh() { apply(s) }
                hudStore.refresh()
            } catch {
                phase = .idle
                timingPosition = 0
                predictedGrade = nil
                switch error.apiErrorCode {
                case "INSUFFICIENT_EVOLUTION_EXP", "INSUFFICIENT_POINTS":
                    errorMessage = "경험치가 부족해요. 채팅으로 모아볼까요?"
                case "ALREADY_MAX_LEVEL":
                    errorMessage = "이미 최고 레벨이에요!"
                default:
                    errorMessage = "네트워크 오류 — 다시 시도해주세요"
                }
                _ = try? await store.refresh()
            }
        }
    }

    func dismissResult() {
        phase = .idle
        result = nil
        timingPosition = 0
        predictedGrade = nil
    }

    private func position(_ elapsedMs: Int64, _ w: TimingWindow) -> Float {
        guard w.cycleDurationMs > 0 else { return 0 }
        return Float(elapsedMs % w.cycleDurationMs) / Float(w.cycleDurationMs)
    }
}

private let levelEmojis = [1: "🥚", 2: "🐣", 3: "🐤", 4: "🦅", 5: "🐲"]
private let levelNames = [1: "알", 2: "부화", 3: "유년", 4: "성장", 5: "궁극"]

struct EvolutionScreen: View {
    @StateObject private var vm = EvolutionViewModel()
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    private let accent = Color(red: 0.36, green: 0.42, blue: 0.98)

    var body: some View {
        NavigationStack {
            ZStack {
                switch vm.screenState {
                case .loading:
                    ProgressView()
                case .loadError(let message):
                    VStack(spacing: 12) {
                        Text(message).multilineTextAlignment(.center)
                        Button("다시 시도") { vm.load() }.buttonStyle(.borderedProminent)
                    }.padding(24)
                case .content:
                    contentBody
                }

                if vm.phase == .result, let r = vm.result, r.success, !reduceMotion {
                    EvolutionSuccessParticles(color: evolutionGradeColor(r.timingGrade ?? TimingGrade.perfect))
                        .ignoresSafeArea()
                }
            }
            .navigationTitle("캐릭터 진화")
            .navigationBarTitleDisplayMode(.inline)
            .onAppear { vm.load() }
            .alert("알림", isPresented: Binding(get: { vm.errorMessage != nil }, set: { if !$0 { vm.errorMessage = nil } })) {
                Button("확인") { vm.errorMessage = nil }
            } message: { Text(vm.errorMessage ?? "") }
            .sheet(isPresented: Binding(get: { vm.phase == .result }, set: { if !$0 { vm.dismissResult() } })) {
                if let r = vm.result { resultSheet(r) }
            }
        }
    }

    private var displayLevel: Int {
        if let r = vm.result, r.success { return Int(r.resultLevel) }
        return vm.level
    }

    private var contentBody: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(spacing: 16) {
                    Text(levelEmojis[displayLevel] ?? "🐣")
                        .font(.system(size: 84))
                        .scaleEffect(vm.phase == .charging ? 1.06 : (vm.result?.success == true ? 1.18 : 1.0))
                        .animation(reduceMotion ? nil : .spring(response: 0.4, dampingFraction: 0.6), value: vm.phase)
                    Text("Lv.\(displayLevel) \(levelNames[displayLevel] ?? "")")
                        .font(.largeTitle.weight(.black))
                    stepIndicator
                    if !vm.isMaxLevel {
                        if let exp = vm.currentExp, let cost = vm.nextCost, cost > 0 {
                            VStack(spacing: 6) {
                                HStack {
                                    Text("진화 충전").font(.caption)
                                    Spacer()
                                    Text("⭐ \(exp) / \(cost)").font(.caption)
                                }
                                ProgressView(value: Double(min(exp, cost)), total: Double(cost))
                            }.padding(.horizontal, 24)
                        }
                        HStack(spacing: 10) {
                            statCard("성공 확률", vm.nextRate.map { "\(Int($0 * 100))%" } ?? "—")
                            statCard("진화 비용", vm.nextCost.map { "⭐ \($0)" } ?? "—")
                        }.padding(.horizontal, 20)
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.top, 16)
            }

            // 하단 고정 CTA
            bottomCta
                .padding(20)
                .background(.ultraThinMaterial)
        }
    }

    @ViewBuilder
    private var bottomCta: some View {
        if vm.isMaxLevel {
            Text("🏆 최고 레벨 달성!").font(.headline).foregroundStyle(accent)
        } else if vm.capability == .supported {
            timingControls
        } else {
            Button(action: { vm.attemptLegacy() }) {
                Text(vm.phase == .resolving ? "분석 중…" : (vm.canAfford ? "🎰 진화 시도하기" : "경험치가 부족해요"))
                    .font(.headline).frame(maxWidth: .infinity).padding(.vertical, 16)
                    .background(accent).foregroundStyle(.white).clipShape(RoundedRectangle(cornerRadius: 16))
            }
            .disabled(vm.phase != .idle || !vm.canAfford)
        }
    }

    private var timingControls: some View {
        VStack(spacing: 14) {
            if let session = vm.timingSession {
                EvolutionTimingGauge(
                    window: TimingWindow(
                        minimumHoldMs: session.minimumHoldMs, cycleDurationMs: session.cycleDurationMs,
                        perfectStart: 0.45, perfectEnd: 0.55, greatStart: 0.38, greatEnd: 0.62
                    ),
                    position: vm.timingPosition,
                    predictedGrade: vm.predictedGrade,
                    active: vm.phase == .charging
                )
            }
            let charging = vm.phase == .charging
            Text({
                if vm.phase == .resolving { return "분석 중…" }
                if charging { return "꾹 — 중앙에서 떼세요!" }
                if !vm.canAfford { return "경험치가 부족해요" }
                return "🔋 꾹 눌러 진화 충전"
            }())
            .font(.headline)
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity).padding(.vertical, 16)
            .background(charging ? accent.opacity(0.85) : (vm.canAfford ? accent : Color.gray))
            .clipShape(RoundedRectangle(cornerRadius: 18))
            .scaleEffect(charging ? 0.97 : 1.0)
            .animation(reduceMotion ? nil : .easeOut(duration: 0.12), value: charging)
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        if vm.phase == .idle, vm.canAfford { vm.beginHold() }
                        if abs(value.translation.height) > 80 { vm.cancelHold() }
                    }
                    .onEnded { _ in vm.releaseHold() }
            )
        }
    }

    private var stepIndicator: some View {
        HStack(spacing: 6) {
            ForEach(1...5, id: \.self) { step in
                Circle()
                    .fill(step <= displayLevel ? accent : Color(.systemGray4))
                    .frame(width: step == displayLevel ? 14 : 10, height: step == displayLevel ? 14 : 10)
            }
        }
    }

    private func statCard(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(.caption).foregroundStyle(.secondary)
            Text(value).font(.title3.weight(.bold))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }

    private func resultSheet(_ r: EvolutionAttemptDto) -> some View {
        VStack(spacing: 16) {
            Text(r.success ? "🎉 Lv.\(Int(r.resultLevel)) 달성!" : "아깝다!")
                .font(.title2.weight(.bold))
            if r.success {
                Text("진화 성공! 밥도 보너스로 충전됐어요 ⚡")
            } else {
                Text("이번엔 실패했어요 (-\(r.cost) 경험치). 다시 도전해볼까요?")
                    .multilineTextAlignment(.center)
            }
            // 서버가 내려준 등급·확률을 예상값보다 우선해 노출
            if let grade = r.timingGrade, grade != TimingGrade.normal {
                Text(evolutionGradeLabel(grade)).font(.subheadline.weight(.semibold))
                    .foregroundStyle(evolutionGradeColor(grade))
            }
            if let rate = r.finalSuccessRate?.doubleValue {
                Text("적용 확률 \(Int(rate * 100))%").font(.caption).foregroundStyle(.secondary)
            }
            HStack(spacing: 12) {
                if !r.success {
                    Button("다음에") { vm.dismissResult() }.buttonStyle(.bordered)
                    Button("다시 도전") { vm.dismissResult(); vm.attemptLegacy() }.buttonStyle(.borderedProminent)
                } else {
                    Button("좋아!") { vm.dismissResult() }.buttonStyle(.borderedProminent)
                }
            }
        }
        .padding(28)
        .presentationDetents([.medium])
    }
}
