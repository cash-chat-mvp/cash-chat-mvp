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

    // 출석 — 채팅 진입 시 자동 체크인 (Android ChatViewModel과 동일).
    @Published var attendanceMonth: Int = 0
    @Published var attendanceStreak: Int = 0
    @Published var attendanceCheckedDays: Set<Int> = []
    @Published var attendanceTodayChecked = false
    @Published var checkInToast: String? = nil

    // 에너지 게이트 리워드 광고 보상 단계 (Android RewardPhase 미러).
    enum RewardPhase { case idle, showingAd, polling, failed }
    @Published var rewardPhase: RewardPhase = .idle

    // Ad Gate(블라인드 답변) 정보.
    @Published var gateTeaserChars: Int = 80
    @Published var gateRewardCoin: Int = 30

    private let store = KoinHelper().chatStore()
    private let chatApi = KoinHelper().chatApi()
    private let hudStore = KoinHelper().hudStore()
    private let attendanceStore = KoinHelper().attendanceStore()
    private let adRewardStore = KoinHelper().adRewardStore()
    private let collector = FlowCollector()
    private var didLoad = false
    // 자동 출석 체크인은 세션당 1회만 시도 — 실패(네트워크/409 등) 시 무한 재시도 방지.
    private var hasAttemptedAutoCheckIn = false

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
        // 출석: 월간 로드 후 미출석이면 1회 자동 체크인.
        attendanceStore.loadMonthly(year: nil, month: nil)
        collector.collectAttendance(store: attendanceStore) { [weak self] s in
            Task { @MainActor in
                guard let self else { return }
                self.attendanceMonth = Int(s.month)
                self.attendanceStreak = Int(s.currentStreak)
                self.attendanceCheckedDays = Set(s.checkedDays.map { $0.intValue })
                self.attendanceTodayChecked = s.todayChecked
                if !s.todayChecked && !s.isCheckingIn && !self.hasAttemptedAutoCheckIn {
                    self.hasAttemptedAutoCheckIn = true
                    self.attendanceStore.checkIn()
                }
            }
        }
        collector.collectRewards(store: attendanceStore) { [weak self] ev in
            Task { @MainActor in self?.checkInToast = "출석 완료! +\(ev.awardedCoin) 코인" }
        }
        collector.collectGateInfo(store: store) { [weak self] info in
            Task { @MainActor in
                guard let self, let info else { return }
                self.gateTeaserChars = Int(info.teaserChars)
                self.gateRewardCoin = Int(info.rewardCoin)
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
