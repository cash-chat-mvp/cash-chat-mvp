import SwiftUI
import shared

/// 혜택존 리워드 광고 카드 상태 홀더. 채팅 경로와 무관하게 독립 동작.
/// runRewardFlow(showAd:) 의 showAd 파라미터는 KotlinSuspendFunction1 프로토콜이라
/// Swift async 클로저를 직접 전달할 수 없으므로, ChatViewModel.startAdReward 와 동일하게
/// refreshQuota / requestNonce / awaitRewardApplied 를 수동으로 순서대로 호출한다.
@MainActor
final class RewardAdCardViewModel: ObservableObject {
    @Published var remaining: Int? = nil
    @Published var busy = false
    @Published var toast: String? = nil

    private let adRewardStore = KoinHelper().adRewardStore()
    private let hudStore = KoinHelper().hudStore()
    private let adManager = RewardedAdManager()

    func onAppear() {
        adManager.preload()
        Task { await loadQuota() }
    }

    func loadQuota() async {
        if let q = try? await adRewardStore.refreshQuota() {
            remaining = Int(q.remaining)
        }
    }

    func watchAd() {
        guard !busy else { return }
        busy = true
        Task { @MainActor in
            defer { busy = false }
            do {
                let baseline = try await adRewardStore.refreshQuota().usedToday
                let nonce = try await adRewardStore.requestNonce()

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

                // 광고 미준비일 때만 안내 토스트(Android parity). 끝까지 안 보고 닫은 경우는 무토스트.
                guard watched else {
                    if notReady { toast = "광고를 준비 중이에요. 잠시 후 다시 시도해주세요" }
                    return
                }

                let applied = try await adRewardStore.awaitRewardApplied(baselineUsedToday: baseline).boolValue
                try? await hudStore.refreshEnergyOnly()
                await loadQuota()

                if applied {
                    toast = "에너지를 충전했어요!"
                } else {
                    toast = "보상 확인 중이에요. 잠시 후 다시 확인해주세요"
                }
            } catch {
                try? await hudStore.refreshEnergyOnly()
                await loadQuota()
            }
        }
    }
}

struct RewardAdCardView: View {
    @StateObject private var vm = RewardAdCardViewModel()
    var onToast: (String) -> Void = { _ in }

    private var limitReached: Bool { vm.remaining == 0 }

    private var gradient: LinearGradient {
        let colors = limitReached
            ? [Color(red: 0.75, green: 0.66, blue: 0.63), Color(red: 0.66, green: 0.60, blue: 0.63)]
            : [Color(red: 1.0, green: 0.54, blue: 0.30), Color(red: 1.0, green: 0.37, blue: 0.54)]
        return LinearGradient(colors: colors, startPoint: .topLeading, endPoint: .bottomTrailing)
    }

    private var badgeText: String {
        if vm.remaining == nil { return "불러오는 중…" }
        return limitReached ? "오늘 한도 도달 · 자정 리셋" : "오늘 \(vm.remaining!)회 남음"
    }

    private let accent = Color(red: 1.0, green: 0.37, blue: 0.54)

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                ZStack {
                    RoundedRectangle(cornerRadius: 12).fill(.white.opacity(0.25)).frame(width: 40, height: 40)
                    Text("⚡").font(.system(size: 20))
                }
                Spacer()
                Text(badgeText)
                    .font(.system(size: 10, weight: .bold)).foregroundStyle(.white)
                    .padding(.horizontal, 9).padding(.vertical, 4)
                    .background(.white.opacity(0.22)).clipShape(Capsule())
            }
            Text("리워드 광고").font(.system(size: 16, weight: .heavy)).foregroundStyle(.white).padding(.top, 10)
            Text("광고 보고 에너지 충전하기").font(.system(size: 12, weight: .medium)).foregroundStyle(.white.opacity(0.92)).padding(.top, 3)

            ZStack {
                RoundedRectangle(cornerRadius: 11).fill(.white.opacity(limitReached ? 0.5 : 1.0))
                if vm.busy {
                    HStack(spacing: 8) {
                        ProgressView().tint(accent)
                        Text("보상 확인 중...").font(.system(size: 13, weight: .bold))
                    }.foregroundStyle(accent)
                } else {
                    Text(limitReached ? "내일 다시 만나요" : "▶  광고 보기")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(accent)
                }
            }
            .frame(height: 40).padding(.top, 13)
        }
        .padding(16)
        .background(gradient)
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .contentShape(Rectangle())
        .onTapGesture { if !limitReached && !vm.busy { vm.watchAd() } }
        .onAppear { vm.onAppear() }
        .onChange(of: vm.toast) { _, newValue in
            guard let newValue else { return }
            onToast(newValue)
            vm.toast = nil
        }
    }
}
