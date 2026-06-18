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

    private let store = KoinHelper().chatStore()
    private let chatApi = KoinHelper().chatApi()
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
