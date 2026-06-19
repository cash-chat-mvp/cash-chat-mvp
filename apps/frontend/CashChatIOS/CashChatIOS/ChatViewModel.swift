import Foundation
import SwiftUI
import Combine
import CashChatShared

/// 브랜치 shared `ChatStore` 를 감싸는 iOS 채팅 ViewModel.
/// Flow 구독은 FlowCollector(메인 디스패처)로 브리지하고, deinit 에서 취소해 누수를 막는다.
@MainActor
final class ChatViewModel: ObservableObject {
    @Published var items: [ChatItem] = []
    @Published var isStreaming = false
    @Published var energyGateVisible = false
    @Published var conversations: [ConversationSummaryDto] = []

    // HUD (에너지/레벨/포인트) — Android ChatScreen 톱바와 동일 정보.
    @Published var level: Int = 1
    @Published var isMaxLevel = false
    @Published var energy: Int = 0
    @Published var maxEnergy: Int = 0
    @Published var points: Int64? = nil
    @Published var nextRecoverAt: String? = nil
    @Published var hudLoaded = false

    private let store = KoinHelper().chatStore()
    private let chatApi = KoinHelper().chatApi()
    private let hudStore = KoinHelper().hudStore()
    private let collector = FlowCollector()
    private var didLoad = false

    deinit {
        collector.cancel()
    }

    /// 화면 진입 시 1회 호출 — Flow 구독 시작.
    func load() {
        guard !didLoad else { return }
        didLoad = true
        collector.collectChatItems(store: store) { [weak self] list in
            Task { @MainActor in self?.items = list }
        }
        collector.collectIsStreaming(store: store) { [weak self] streaming in
            Task { @MainActor in self?.isStreaming = streaming.boolValue }
        }
        collector.collectEnergyGate(store: store) { [weak self] visible in
            Task { @MainActor in self?.energyGateVisible = visible.boolValue }
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
                self.nextRecoverAt = s.nextRecoverAt
                self.hudLoaded = s.isLoaded
            }
        }
        // 스트림 정상 종료 시 에너지(밥) 소모 반영 위해 재조회.
        collector.collectStreamCompleted(store: store) { [weak self] count in
            Task { @MainActor in
                guard let self, count.intValue > 0 else { return }
                try? await self.hudStore.refreshEnergyOnly()
            }
        }
    }

    /// 회복 카운트다운 종료 등 — 에너지만 재조회.
    func refreshEnergy() {
        Task { @MainActor in try? await self.hudStore.refreshEnergyOnly() }
    }

    func dismissEnergyGate() {
        store.dismissEnergyGate()
    }

    func send(_ text: String) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        store.sendMessage(text: trimmed)
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
