import SwiftUI
import UIKit
import Combine
import CashChatShared

/// 일부 SF Symbol 이름은 iOS 버전에 따라 없을 수 있어, 없으면 폴백 이름을 사용한다.
private func chatSFSymbol(_ primary: String, fallback: String) -> String {
    UIImage(systemName: primary) == nil ? fallback : primary
}

private let suggestedQuestions = ["오늘 저녁 뭐 먹을까?", "가성비 이어폰 추천해줘", "영어 공부 팁 알려줘"]

struct ChatScreen: View {
    @StateObject private var vm = ChatViewModel()
    @StateObject private var adManager = RewardedAdManagerBox()
    @State private var input = ""
    @State private var showConversations = false
    @State private var showEvolution = false
    @State private var shareItems: String? = nil
    @FocusState private var isInputFocused: Bool

    // 보상 토큰 연출용 좌표/펄스
    @State private var rewardFrames: [String: CGRect] = [:]
    @State private var pointPulse = 0
    @State private var expPulse = 0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private let accent = Color(red: 0.36, green: 0.42, blue: 0.98)

    var body: some View {
        ZStack {
            VStack(spacing: 0) {
                header
                modelSwitcher
                if vm.selectedModel == .cashAi {
                    BannerAdView(slotName: "chat_top")
                        .frame(height: 50)
                } else {
                    gemmaStatusCard
                }
                messageList
                inputBar
            }
            // 최상위 보상 토큰 오버레이 — 헤더 HUD/입력창 위에서도 잘리지 않게 화면 전체에 그린다.
            RewardTokenOverlay(
                reward: vm.selectedModel == .cashAi ? vm.rewardFeedback : nil,
                frames: rewardFrames,
                reduceMotion: reduceMotion,
                onPointArrived: { pointPulse += 1 },
                onExpArrived: { expPulse += 1 }
            )
        }
        .coordinateSpace(name: "chatRoot")
        .onPreferenceChange(RewardFramePreferenceKey.self) { rewardFrames = $0 }
        .background(Color(.systemGroupedBackground))
        .onAppear { vm.load() }
        // 채팅 화면을 떠나면 캐시된 네이티브 광고를 해제한다(스크롤 재진입 시 재사용하던 캐시).
        .onDisappear { ChatNativeAdCache.shared.clear() }
        .sheet(isPresented: $showConversations) {
            conversationListSheet
        }
        .sheet(isPresented: $showEvolution) {
            EvolutionScreen()
        }
        .sheet(isPresented: Binding(get: { shareItems != nil }, set: { if !$0 { shareItems = nil } })) {
            if let text = shareItems { ShareSheet(text: text) }
        }
        .sheet(
            isPresented: Binding(
                get: { vm.selectedModel == .cashAi && vm.energyGateVisible },
                set: { if !$0 { vm.dismissGate() } }
            ),
            onDismiss: { vm.dismissGate() }
        ) {
            EnergyGateSheet(vm: vm, adManager: adManager.manager)
                .presentationDetents([.height(300)])
        }
    }

    private var header: some View {
        HStack(spacing: 8) {
            Button {
                showConversations = true
            } label: {
                Image(systemName: chatSFSymbol("line.3.horizontal", fallback: "line.horizontal.3"))
                    .foregroundStyle(.primary)
            }
            // 캐릭터/레벨 탭 → 진화 화면.
            Button { showEvolution = true } label: {
                HStack(spacing: 6) {
                    Image(systemName: "sparkles").foregroundStyle(accent)
                    if vm.hudLoaded {
                        Text("Lv.\(vm.level)").font(.subheadline.weight(.bold)).foregroundStyle(.primary)
                    }
                }
            }
            Spacer()
            if vm.selectedModel == .cashAi && vm.hudLoaded {
                if let p = vm.points {
                    RewardHudChip(emoji: "🪙", value: "\(p)", pulse: pointPulse)
                        .reportRewardFrame(rewardPointKey)
                }
                if let e = vm.exp {
                    RewardHudChip(emoji: "⭐", value: "\(e)", pulse: expPulse)
                        .reportRewardFrame(rewardExpKey)
                }
                VStack(alignment: .trailing, spacing: 2) {
                    chip("⚡", "\(vm.energy)/\(vm.maxEnergy)", warning: vm.energy == 0)
                    if let iso = vm.nextRecoverAt {
                        RecoveryCountdown(nextRecoverAtIso: iso) { vm.refreshEnergy() }
                    }
                }
            }
            if vm.selectedModel == .cashAi && !vm.items.isEmpty {
                Button { shareItems = exportText(vm.items) } label: {
                    Image(systemName: chatSFSymbol("square.and.arrow.up", fallback: "arrowshape.turn.up.right"))
                        .foregroundStyle(.primary)
                }
            }
            if vm.selectedModel == .cashAi {
                Button {
                    vm.startNew()
                } label: {
                    Image(systemName: chatSFSymbol("square.and.pencil", fallback: "plus.square"))
                        .foregroundStyle(.primary)
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(Color(.systemBackground))
    }

    private var modelSwitcher: some View {
        Picker("채팅 모델", selection: Binding(
            get: { vm.selectedModel },
            set: { vm.selectModel($0) }
        )) {
            Text("Cash AI").tag(ChatModelSelection.cashAi)
            Text("Gemma").tag(ChatModelSelection.gemma)
        }
        .pickerStyle(.segmented)
        .disabled(vm.isStreaming)
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .background(Color(.systemBackground))
    }

    private var gemmaStatusCard: some View {
        let presentation = GemmaDownloadPresentation(
            state: vm.modelDownloadState,
            engineUnavailableReason: vm.gemmaEngineUnavailableReason
        )
        return VStack(alignment: .leading, spacing: 8) {
            Text(presentation.title)
                .font(.subheadline.weight(.semibold))
            Text(presentation.body)
                .font(.caption)
                .foregroundStyle(.secondary)
            if let progress = presentation.progress {
                ProgressView(value: progress)
            }
            HStack {
                if vm.modelDownloadState is ModelDownloadStateDownloading {
                    Button("취소") { vm.cancelGemmaDownload() }
                } else if !(vm.modelDownloadState is ModelDownloadStateReady) {
                    Button("다운로드") { vm.startGemmaDownload() }
                }
                Spacer()
            }
            .font(.caption.weight(.semibold))
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .background(Color(.systemBackground))
    }

    private func chip(_ emoji: String, _ value: String, warning: Bool = false) -> some View {
        Text("\(emoji) \(value)")
            .font(.caption.weight(.semibold))
            .padding(.horizontal, 8).padding(.vertical, 4)
            .background(warning ? Color.red.opacity(0.15) : Color(.secondarySystemGroupedBackground))
            .clipShape(Capsule())
    }

    private var messageList: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 10) {
                    if vm.items.isEmpty {
                        emptyState
                    } else {
                        ForEach(vm.items, id: \.id) { item in
                            row(for: item).id(item.id)
                        }
                    }
                    if vm.isStreaming {
                        HStack { ProgressView(); Spacer() }.padding(.horizontal, 4)
                    }
                }
                .padding()
            }
            .onChange(of: vm.items.count) { _ in
                if let last = vm.items.last {
                    withAnimation { proxy.scrollTo(last.id, anchor: .bottom) }
                }
            }
        }
    }

    private var emptyState: some View {
        VStack(spacing: 10) {
            Text(vm.selectedModel == .cashAi ? "CashAI 비서" : "Gemma 온디바이스")
                .font(.system(size: 26, weight: .bold))
            Text(vm.selectedModel == .cashAi ? "궁금한 것은 무엇이든 물어보세요.\n대화할수록 포인트가 쌓여요!" : "모델과 엔진 준비가 끝나면 기기 안에서 대화할 수 있어요.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            VStack(spacing: 8) {
                ForEach(suggestedQuestions, id: \.self) { q in
                    Button(q) { vm.send(q) }
                        .disabled(!vm.canSend)
                        .font(.caption.weight(.semibold))
                        .padding(.horizontal, 12).padding(.vertical, 8)
                        .background(Color(.secondarySystemGroupedBackground))
                        .clipShape(Capsule())
                }
            }
            .padding(.top, 12)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 60)
    }

    @ViewBuilder
    private func row(for item: ChatItem) -> some View {
        if let u = item as? ChatItemUserMessage {
            HStack {
                Spacer()
                ZStack(alignment: .topTrailing) {
                    Text(u.text)
                        .padding(.horizontal, 14).padding(.vertical, 10)
                        .background(accent).foregroundStyle(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                    if let energy = vm.energyFeedback, energy.messageId == u.id {
                        ResourceDeltaBadge(eventId: energy.eventId, amount: energy.amount)
                            .offset(x: 6, y: -14)
                    }
                }
            }
        } else if let a = item as? ChatItemAssistantMessage {
            if a.gated && !a.isStreaming {
                AdGateCardView(
                    fullText: a.text,
                    teaserChars: vm.gateTeaserChars,
                    rewardCoin: vm.gateRewardCoin,
                    onWatch: {
                        vm.startGateUnlock(messageId: a.id) { nonce in
                            await withCheckedContinuation { cont in
                                // 보상은 onRewarded에서만 확정. 광고를 끝까지 보지 않고 닫으면 unlock 안 함.
                                var rewarded = false
                                adManager.manager.show(
                                    nonce: nonce,
                                    onRewarded: { _ in rewarded = true },
                                    onDismissed: { cont.resume(returning: rewarded) },
                                    onNotReady: { cont.resume(returning: false) }
                                )
                            }
                        }
                    }
                )
            } else {
                HStack {
                    VStack(alignment: .leading, spacing: 6) {
                        markdownText(a.text.isEmpty && a.isStreaming ? "…" : a.text)
                            .padding(.horizontal, 14).padding(.vertical, 10)
                            .background(Color(.secondarySystemGroupedBackground))
                            .foregroundStyle(.primary)
                            .clipShape(RoundedRectangle(cornerRadius: 14))
                            .reportRewardFrame(rewardBubbleKey(a.id))
                        if a.isError {
                            Button {
                                vm.retry()
                            } label: {
                                Label("다시 시도", systemImage: "arrow.clockwise")
                                    .font(.caption.weight(.semibold))
                            }
                            .tint(.orange)
                        }
                    }
                    Spacer()
                }
            }
        } else if let p = item as? ChatItemProductCards {
            VStack(spacing: 8) {
                ForEach(p.products, id: \.trackingUrl) { product in
                    ProductCardView(product: product)
                }
            }
        } else if let adItem = item as? ChatItemNativeAd {
            HStack {
                ChatNativeAdView(adId: adItem.id)
                Spacer(minLength: 0)
            }
        }
    }

    private var inputBar: some View {
        HStack(spacing: 8) {
            TextField(inputPlaceholder, text: $input)
                .textFieldStyle(.roundedBorder)
                .tint(accent)
                .focused($isInputFocused)
                .onSubmit(sendCurrent)
            Button(action: sendCurrent) {
                Image(systemName: chatSFSymbol("paperplane.fill", fallback: "arrow.up.circle.fill"))
            }
            .disabled(
                input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
                vm.isStreaming ||
                !vm.canSend
            )
        }
        .padding(.horizontal, 14).padding(.vertical, 10)
        .background(Color(.systemBackground))
    }

    private var inputPlaceholder: String {
        switch vm.selectedModel {
        case .cashAi: return "메시지를 입력하세요..."
        case .gemma: return vm.gemmaModelReady ? "Gemma에게 물어보세요..." : "Gemma 모델 다운로드 후 사용할 수 있어요"
        }
    }

    private func sendCurrent() {
        let text = input
        input = ""
        vm.send(text)
    }

    /// 어시스턴트 응답의 인라인 마크다운(**굵게**, *기울임*, `코드`, 링크)을 렌더한다.
    /// - inlineOnlyPreservingWhitespace: 줄바꿈/공백을 보존하고 블록 재배치를 막아 채팅에 적합.
    /// - returnPartiallyParsedIfPossible: 스트리밍 중 닫히지 않은 마크다운도 깨지지 않게 처리.
    private func markdownText(_ raw: String) -> Text {
        if let attributed = try? AttributedString(
            markdown: raw,
            options: AttributedString.MarkdownParsingOptions(
                allowsExtendedAttributes: true,
                interpretedSyntax: .inlineOnlyPreservingWhitespace,
                failurePolicy: .returnPartiallyParsedIfPossible
            )
        ) {
            return Text(attributed)
        }
        return Text(raw)
    }

    private var conversationListSheet: some View {
        NavigationStack {
            List {
                Button {
                    vm.startNew()
                    showConversations = false
                } label: {
                    Label("새 대화", systemImage: "plus")
                }
                ForEach(vm.conversations, id: \.conversationId) { c in
                    Button {
                        vm.open(c.conversationId)
                        showConversations = false
                    } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(c.title).font(.body).foregroundStyle(.primary)
                            if let last = c.lastMessage, !last.isEmpty {
                                Text(last).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                            }
                        }
                    }
                }
            }
            .navigationTitle("대화 목록")
            .navigationBarTitleDisplayMode(.inline)
            .task { await vm.loadConversations() }
        }
    }
}

/// 대화 전체를 텍스트로 변환 (공유 시트용).
private func exportText(_ items: [ChatItem]) -> String {
    items.compactMap { item -> String? in
        if let u = item as? ChatItemUserMessage { return "나: \(u.text)" }
        if let a = item as? ChatItemAssistantMessage { return "비서: \(a.text)" }
        return nil
    }.joined(separator: "\n")
}

/// iOS 공유 시트 래퍼.
private struct ShareSheet: UIViewControllerRepresentable {
    let text: String
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: [text], applicationActivities: nil)
    }
    func updateUIViewController(_ vc: UIActivityViewController, context: Context) {}
}

/// 쿠팡 상품 카드 (SSE product 이벤트).
private struct ProductCardView: View {
    let product: ProductDto
    var body: some View {
        HStack(spacing: 10) {
            AsyncImage(url: URL(string: product.imageUrl ?? "")) { img in
                img.resizable().scaledToFill()
            } placeholder: {
                Color(.tertiarySystemGroupedBackground)
            }
            .frame(width: 64, height: 64).clipShape(RoundedRectangle(cornerRadius: 10))
            VStack(alignment: .leading, spacing: 4) {
                Text(product.title).font(.subheadline.weight(.semibold)).lineLimit(2)
                Text("\(product.price)원").font(.subheadline.weight(.bold)).foregroundStyle(.orange)
                if let rating = product.rating?.doubleValue {
                    Text("★ \(rating, specifier: "%.1f")").font(.caption2).foregroundStyle(.secondary)
                }
            }
            Spacer()
        }
        .padding(12)
        .background(Color(.secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .onTapGesture {
            if let url = URL(string: product.trackingUrl) { UIApplication.shared.open(url) }
        }
    }
}

/// Ad Gate 블라인드 답변 카드 — teaser 일부 노출 + 광고 보고 전체 보기.
private struct AdGateCardView: View {
    let fullText: String
    let teaserChars: Int
    let rewardCoin: Int
    let onWatch: () -> Void
    var body: some View {
        let teaser = String(fullText.prefix(teaserChars))
        return VStack(alignment: .leading, spacing: 10) {
            Text(teaser + "…").foregroundStyle(.primary)
            Button(action: onWatch) {
                Label("광고 보고 전체 보기 (+\(rewardCoin))", systemImage: "play.fill")
                    .font(.caption.weight(.bold))
                    .frame(maxWidth: .infinity).padding(.vertical, 10)
                    .background(.orange).foregroundStyle(.white)
                    .clipShape(Capsule())
            }
        }
        .padding(14)
        .background(Color(.secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }
}

/// RewardedAdManager(NSObject)를 SwiftUI @StateObject로 보유하기 위한 박싱 래퍼.
@MainActor
final class RewardedAdManagerBox: ObservableObject {
    let manager = RewardedAdManager()
    init() { manager.preload() }
}

/// 밥 부족 게이트 바텀시트 — 광고 보고 충전 후 막힌 메시지 재전송.
private struct EnergyGateSheet: View {
    @ObservedObject var vm: ChatViewModel
    let adManager: RewardedAdManager

    var body: some View {
        VStack(spacing: 16) {
            Text("🍚 밥이 부족해요").font(.headline)
            Text("광고를 보고 밥을 충전하면\n바로 답변을 이어받을 수 있어요.")
                .font(.subheadline).foregroundStyle(.secondary)
                .multilineTextAlignment(.center)

            switch vm.rewardPhase {
            case .showingAd, .polling:
                ProgressView(vm.rewardPhase == .polling ? "보상 확인 중…" : "광고 준비 중…")
            case .failed:
                Text("보상 적립을 확인하지 못했어요. 다시 시도해 주세요.")
                    .font(.caption).foregroundStyle(.orange)
                watchButton
            case .idle:
                watchButton
            }
            Button("닫기") { vm.dismissGate() }
                .font(.subheadline).tint(.secondary)
        }
        .padding(24)
        // 게이트가 열릴 때 광고를 미리 로드해 둔다(Android EnergyGateBottomSheet 와 동일).
        // 초기 preload 가 실패했더라도 여기서 다시 시도되어 버튼이 동작하게 된다.
        .task { adManager.preload() }
    }

    private var watchButton: some View {
        Button {
            vm.startAdReward { nonce in
                await withCheckedContinuation { cont in
                    // 광고를 끝까지 봐서 보상이 적립된 경우에만 true. (이후 SSV 폴링으로 최종 확정)
                    var rewarded = false
                    adManager.show(
                        nonce: nonce,
                        onRewarded: { _ in rewarded = true },
                        onDismissed: { cont.resume(returning: rewarded) },
                        onNotReady: { cont.resume(returning: false) }
                    )
                }
            }
        } label: {
            Label("광고 보고 밥 충전", systemImage: "play.fill")
                .font(.subheadline.weight(.bold))
                .frame(maxWidth: .infinity).padding(.vertical, 12)
                .background(.orange).foregroundStyle(.white)
                .clipShape(Capsule())
        }
    }
}

/// 다음 에너지 회복까지 카운트다운. 0 도달 시 onFinished로 에너지 재조회.
private struct RecoveryCountdown: View {
    let nextRecoverAtIso: String
    let onFinished: () -> Void
    @State private var remain = ""

    var body: some View {
        Text(remain)
            .font(.caption2)
            .foregroundStyle(.secondary)
            .task(id: nextRecoverAtIso) {
                let fmt = ISO8601DateFormatter()
                fmt.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
                let target = fmt.date(from: nextRecoverAtIso)
                    ?? ISO8601DateFormatter().date(from: nextRecoverAtIso)
                guard let target else { return }
                var reachedZero = false
                while !Task.isCancelled {
                    let sec = max(Int(target.timeIntervalSinceNow), 0)
                    remain = String(format: "%d:%02d 후 ⚡", sec / 60, sec % 60)
                    if sec == 0 { reachedZero = true; break }
                    try? await Task.sleep(for: .seconds(1))
                }
                // 0초 도달로 정상 완료된 경우에만 에너지 재조회 (화면 전환/값 변경 취소 시 호출 방지)
                if reachedZero && !Task.isCancelled {
                    onFinished()
                }
            }
    }
}
