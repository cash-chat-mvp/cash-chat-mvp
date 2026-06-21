import SwiftUI
import Combine
import CashChatShared

@MainActor
final class InviteViewModel: ObservableObject {
    @Published var status: InviteStatus? = nil
    @Published var submitting = false
    @Published var toast: String? = nil

    private let store = KoinHelper().inviteStore()
    private let collector = FlowCollector()

    func onAppear() {
        collector.collectInviteStatus(store: store) { [weak self] s in self?.status = s }
        Task { try? await store.refresh() }
    }
    func onDisappear() { collector.cancel() }

    func redeem(_ code: String) {
        guard !submitting else { return }
        submitting = true
        Task { @MainActor in
            defer { submitting = false }
            guard let result = try? await store.redeem(code: code) else { toast = "잠시 후 다시 시도해주세요"; return }
            if result.success {
                toast = "⚡\(Int(result.awardedEnergy)) 에너지를 받았어요!"
            } else {
                toast = result.message ?? "코드를 확인해주세요"
            }
        }
    }
}

struct InviteView: View {
    @StateObject private var vm = InviteViewModel()
    var onClose: () -> Void = {}
    @State private var input = ""

    var body: some View {
        VStack(spacing: 11) {
            VStack(alignment: .leading, spacing: 5) {
                Text("친구 초대하고 코인 받기 🎁").font(.system(size: 17, weight: .heavy)).foregroundStyle(.white)
                if let s = vm.status {
                    Text("친구가 가입하면 나는 🪙+\(Int(s.rewardCoin)), 친구는 ⚡+\(Int(s.rewardEnergy))!")
                        .font(.system(size: 12)).foregroundStyle(.white.opacity(0.92))
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading).padding(18)
            .background(LinearGradient(colors: [Color(red:0.49,green:0.42,blue:1), Color(red:1,green:0.37,blue:0.54)],
                                       startPoint: .topLeading, endPoint: .bottomTrailing))
            .clipShape(RoundedRectangle(cornerRadius: 16))

            VStack(alignment: .leading, spacing: 0) {
                Text("내 추천 코드").font(.system(size: 12)).foregroundStyle(.secondary)
                Text(vm.status?.myCode ?? "-").font(.system(size: 22, weight: .heavy)).padding(.top, 4)
                if let code = vm.status?.myCode {
                    ShareLink(item: "캐시챗에서 만나요! 추천코드 [\(code)] 입력하면 에너지를 드려요 ⚡") {
                        Text("친구에게 공유하기").frame(maxWidth: .infinity)
                    }.buttonStyle(.borderedProminent).padding(.top, 12)
                }
                if let s = vm.status {
                    Text("지금까지 \(Int(s.invitedCount))명 초대").font(.system(size: 12)).foregroundStyle(.secondary).padding(.top, 9)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading).padding(15).background(.white).clipShape(RoundedRectangle(cornerRadius: 16))

            VStack(alignment: .leading, spacing: 8) {
                let available = vm.status?.redeemAvailable ?? false
                Text(available ? "추천 코드 입력" : "이미 추천 코드를 사용했어요")
                    .font(.system(size: 12)).foregroundStyle(.secondary)
                if available {
                    TextField("코드 입력", text: $input)
                        .textInputAutocapitalization(.characters).autocorrectionDisabled()
                        .textFieldStyle(.roundedBorder)
                    Button(action: { vm.redeem(input) }) { Text("에너지 받기").frame(maxWidth: .infinity) }
                        .buttonStyle(.borderedProminent).disabled(vm.submitting || input.isEmpty)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading).padding(15).background(.white).clipShape(RoundedRectangle(cornerRadius: 16))

            Button("닫기") { onClose() }.foregroundStyle(.secondary).font(.system(size: 13))
        }
        .padding(16)
        .onAppear { vm.onAppear() }
        .onDisappear { vm.onDisappear() }
        .safeAreaInset(edge: .bottom) {
            if let t = vm.toast {
                Text(t).font(.system(size: 14, weight: .semibold)).foregroundStyle(.white)
                    .padding(.horizontal, 18).padding(.vertical, 12)
                    .background(Color(red:0.1,green:0.1,blue:0.16).opacity(0.92)).clipShape(Capsule()).padding(.bottom, 8)
            }
        }
        .animation(.easeOut(duration: 0.25), value: vm.toast)
        .onChange(of: vm.toast) { _, newValue in
            guard newValue != nil else { return }
            // 토스트 자동 사라짐(Android Toast.LENGTH_SHORT 와 동등).
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) { vm.toast = nil }
        }
    }
}
