import Foundation
import SwiftUI
import Combine
import CashChatShared

enum ChatModelSelection {
    case cashAi
    case gemma
}

struct GemmaDownloadPresentation {
    let title: String
    let body: String
    let progress: Double?

    init(state: ModelDownloadState?, engineUnavailableReason: String?) {
        switch state {
        case let ready as ModelDownloadStateReady:
            if let engineUnavailableReason {
                title = "Gemma 엔진 준비 필요"
                body = engineUnavailableReason
            } else {
                title = "Gemma 모델 준비됨"
                body = "온디바이스 대화를 시작할 수 있어요. \(ready.localPath)"
            }
            progress = nil
        case let downloading as ModelDownloadStateDownloading:
            let fraction = downloading.totalBytes > 0
                ? Double(downloading.receivedBytes) / Double(downloading.totalBytes)
                : 0
            title = "Gemma 모델 다운로드 중"
            body = "\(Int((fraction * 100).rounded()))% 완료"
            progress = min(max(fraction, 0), 1)
        case is ModelDownloadStateVerifying:
            title = "Gemma 모델 확인 중"
            body = "파일 무결성을 확인하고 있어요."
            progress = nil
        case let failed as ModelDownloadStateFailed:
            title = "Gemma 모델 준비 실패"
            body = failed.reason
            progress = nil
        default:
            title = "Gemma 모델 다운로드 필요"
            body = "처음 한 번 모델 파일을 내려받아야 해요."
            progress = nil
        }
    }
}

/// 브랜치 shared `ChatStore` 를 감싸는 iOS 채팅 ViewModel.
/// Flow 구독은 FlowCollector(메인 디스패처)로 브리지하고, deinit 에서 취소해 누수를 막는다.
@MainActor
final class ChatViewModel: ObservableObject {
    @Published var items: [ChatItem] = []
    @Published var isStreaming = false
    @Published var energyGateVisible = false
    @Published var conversations: [ConversationSummaryDto] = []
    @Published var selectedModel: ChatModelSelection = .cashAi
    @Published var modelDownloadState: ModelDownloadState? = nil
    // 엔진(LiteRT-LM Swift)이 주입되므로 더 이상 "미포함" 사유가 아니다. 다운로드만 끝나면 대화 가능.
    @Published var gemmaEngineUnavailableReason: String? = nil
    @Published var gemmaSendBlockedMessage: String? = nil

    /// Gemma 모드 입력 가능 여부 — 모델 파일이 준비(다운로드+검증 완료)됐을 때.
    var gemmaModelReady: Bool { modelDownloadState is ModelDownloadStateReady }

    /// 현재 모드에서 메시지를 보낼 수 있는지. Cash AI 는 항상, Gemma 는 모델 준비 후.
    var canSend: Bool { selectedModel == .cashAi || gemmaModelReady }

    // HUD (에너지/레벨/포인트) — Android ChatScreen 톱바와 동일 정보.
    @Published var level: Int = 1
    @Published var isMaxLevel = false
    @Published var energy: Int = 0
    @Published var maxEnergy: Int = 0
    @Published var points: Int64? = nil
    @Published var exp: Int64? = nil
    @Published var nextRecoverAt: String? = nil
    @Published var hudLoaded = false

    // 자원 피드백 — 사용자 버블 ⚡-1 차감, 완료 보상 🪙/⭐ 토큰 연출.
    @Published var energyFeedback: EnergyFeedback? = nil
    @Published var rewardFeedback: RewardFeedback? = nil

    // 에너지 게이트 리워드 광고 보상 단계 (Android RewardPhase 미러).
    enum RewardPhase { case idle, showingAd, polling, failed }
    @Published var rewardPhase: RewardPhase = .idle

    // Ad Gate(블라인드 답변) 정보.
    @Published var gateTeaserChars: Int = 80
    @Published var gateRewardCoin: Int = 30

    private let store = KoinHelper().chatStore()
    private let chatModeStore = KoinHelper().chatModeStore()
    private let modelDownloadStore = KoinHelper().modelDownloadStore()
    private let localChatStore = KoinHelper().localChatStore()
    private let chatApi = KoinHelper().chatApi()
    private let hudStore = KoinHelper().hudStore()
    private let adRewardStore = KoinHelper().adRewardStore()
    private let collector = FlowCollector()
    private var didLoad = false

    // `items`/`isStreaming` 은 활성 모드(Cash AI ↔ Gemma)의 값을 보여준다.
    // 각 스토어 구독은 항상 캐시에 쌓고, 활성 모드일 때만 표시 프로퍼티로 반영한다.
    private var cashItems: [ChatItem] = []
    private var localItems: [ChatItem] = []
    private var cashStreaming = false
    private var localStreaming = false

    private func syncActiveMode() {
        if selectedModel == .gemma {
            items = localItems
            isStreaming = localStreaming
        } else {
            items = cashItems
            isStreaming = cashStreaming
        }
    }

    deinit {
        collector.cancel()
    }

    /// 화면 진입 시 1회 호출 — Flow 구독 시작.
    func load() {
        guard !didLoad else { return }
        didLoad = true
        collector.collectChatItems(store: store) { [weak self] list in
            Task { @MainActor in
                guard let self else { return }
                self.cashItems = list
                if self.selectedModel == .cashAi { self.items = list }
            }
        }
        collector.collectChatMode(store: chatModeStore) { [weak self] mode in
            Task { @MainActor in
                guard let self else { return }
                self.selectedModel = mode == ChatModelMode.gemmaLocal ? .gemma : .cashAi
                self.syncActiveMode()
            }
        }
        modelDownloadStore.refresh()
        collector.collectModelDownloadState(store: modelDownloadStore) { [weak self] state in
            Task { @MainActor in
                self?.modelDownloadState = state
                if state is ModelDownloadStateReady {
                    self?.gemmaSendBlockedMessage = nil
                }
            }
        }
        collector.collectIsStreaming(store: store) { [weak self] streaming in
            Task { @MainActor in
                guard let self else { return }
                self.cashStreaming = streaming.boolValue
                if self.selectedModel == .cashAi { self.isStreaming = streaming.boolValue }
            }
        }
        // Gemma 온디바이스 채팅 — 활성 모드일 때만 표시에 반영.
        collector.collectLocalChatItems(store: localChatStore) { [weak self] list in
            Task { @MainActor in
                guard let self else { return }
                self.localItems = list
                if self.selectedModel == .gemma { self.items = list }
            }
        }
        collector.collectLocalStreaming(store: localChatStore) { [weak self] streaming in
            Task { @MainActor in
                guard let self else { return }
                self.localStreaming = streaming.boolValue
                if self.selectedModel == .gemma { self.isStreaming = streaming.boolValue }
            }
        }
        collector.collectEnergyGate(store: store) { [weak self] visible in
            Task { @MainActor in
                guard let self else { return }
                self.energyGateVisible = visible.boolValue
                if visible.boolValue { try? await self.adRewardStore.refreshQuota() }
            }
        }
        hudStore.refresh()
        collector.collectHud(store: hudStore) { [weak self] s in
            Task { @MainActor in
                guard let self else { return }
                self.level = Int(s.level)
                self.isMaxLevel = s.isMaxLevel
                self.energy = Int(s.energy)
                self.maxEnergy = Int(s.maxEnergy)
                self.points = s.points?.int64Value
                self.exp = s.exp?.int64Value
                self.nextRecoverAt = s.nextRecoverAt
                self.hudLoaded = s.isLoaded
            }
        }
        // 스트림 정상 종료 시 전체 HUD 재조회(보상 토큰 연출은 rewardFeedback 이 독립 구동).
        collector.collectStreamCompleted(store: store) { [weak self] count in
            Task { @MainActor in
                guard let self, count.intValue > 0 else { return }
                try? await self.hudStore.refreshNow()
            }
        }
        collector.collectGateInfo(store: store) { [weak self] info in
            Task { @MainActor in
                guard let self, let info else { return }
                self.gateTeaserChars = Int(info.teaserChars)
                self.gateRewardCoin = Int(info.rewardCoin)
            }
        }
        // 메시지 ID 기반 자원 피드백 이벤트(에너지 차감 / 완료 보상).
        collector.collectResourceFeedback(store: store) { [weak self] feedback in
            Task { @MainActor in
                guard let self else { return }
                if let e = feedback as? ChatResourceFeedbackEnergySpent {
                    self.energyFeedback = EnergyFeedback(
                        eventId: e.eventId, messageId: e.messageId, amount: Int(e.amount)
                    )
                } else if let r = feedback as? ChatResourceFeedbackRewardEarned {
                    self.rewardFeedback = RewardFeedback(
                        eventId: r.eventId, messageId: r.messageId,
                        pointDelta: r.pointDelta, expDelta: r.expDelta
                    )
                }
            }
        }
    }

    /// 회복 카운트다운 종료 등 — 에너지만 재조회.
    func refreshEnergy() {
        Task { @MainActor in try? await self.hudStore.refreshEnergyOnly() }
    }

    /// 게이트 CTA: baseline 적립횟수 → nonce → 광고 표시 → 적립 폴링 → 성공 시 재전송.
    /// showAd는 nonce를 받아 광고를 띄우고, 광고를 끝까지 봤는지(닫힘=true, 미준비=false)를 반환한다.
    func startAdReward(showAd: @escaping (_ nonce: String) async -> Bool) {
        Task { @MainActor in
            rewardPhase = .showingAd
            do {
                let baseline = try await adRewardStore.refreshQuota().usedToday
                let nonce = try await adRewardStore.requestNonce()
                guard await showAd(nonce) else {
                    // 광고를 끝까지 보지 않았거나 준비 실패 → FAILED가 아니라 초기 상태로 복귀.
                    rewardPhase = .idle
                    return
                }
                rewardPhase = .polling
                let applied = try await adRewardStore.awaitRewardApplied(baselineUsedToday: baseline).boolValue
                try? await hudStore.refreshEnergyOnly()
                _ = try? await adRewardStore.refreshQuota()
                if applied {
                    rewardPhase = .idle
                    store.retryBlocked()
                } else {
                    rewardPhase = .failed
                }
            } catch {
                // 멈춤 방지: 폴링 단계에서 실패하면 FAILED, 광고 표시 전(준비) 실패면 초기 상태로.
                rewardPhase = (rewardPhase == .polling) ? .failed : .idle
                try? await hudStore.refreshEnergyOnly()
            }
        }
    }

    func dismissGate() {
        rewardPhase = .idle
        store.dismissEnergyGate()
    }

    /// Ad Gate 해제: nonce 발급 → 광고 → 성공 시 해당 메시지 blur 해제.
    func startGateUnlock(messageId: String, showAd: @escaping (_ nonce: String) async -> Bool) {
        Task { @MainActor in
            var watched = false
            do {
                let nonce = try await adRewardStore.requestNonce()
                watched = await showAd(nonce)
            } catch { watched = false }
            if watched { store.unlockGatedMessage(messageId: messageId) }
        }
    }

    func dismissEnergyGate() {
        store.dismissEnergyGate()
    }

    @discardableResult
    func send(_ text: String) -> Bool {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return false }
        gemmaSendBlockedMessage = nil
        switch selectedModel {
        case .cashAi:
            store.sendMessage(text: trimmed)
            return true
        case .gemma:
            // 모델 파일이 준비됐을 때만 전송(엔진은 첫 전송 시 lazy 로드, ~10초).
            guard gemmaModelReady else {
                gemmaSendBlockedMessage = "Gemma 모델 다운로드와 검증이 끝난 뒤 전송할 수 있어요."
                return false
            }
            localChatStore.sendMessage(text: trimmed)
            return true
        }
    }

    func selectModel(_ selection: ChatModelSelection) {
        switch selection {
        case .cashAi:
            chatModeStore.select(mode: ChatModelMode.cashAi)
        case .gemma:
            chatModeStore.select(mode: ChatModelMode.gemmaLocal)
        }
    }

    func startGemmaDownload() {
        modelDownloadStore.start()
    }

    func cancelGemmaDownload() {
        modelDownloadStore.cancel()
    }

    func startNew() {
        store.startNewConversation()
    }

    func loadConversations() async {
        do {
            conversations = try await chatApi.listConversations()
        } catch {
            // 목록 조회 실패는 조용히 무시(빈 목록 유지) — 채팅 자체는 영향 없음.
            conversations = []
        }
    }

    func open(_ id: Int64) {
        Task { try? await store.openConversation(id: id) }
    }

    /// 스트림 단절/에러 후 마지막 user 메시지 재전송.
    func retry() {
        store.retryLastMessage()
    }
}
