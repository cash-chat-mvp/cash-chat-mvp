import SwiftUI
import UIKit
import Combine
import CashChatShared

/// 일부 SF Symbol 이름은 iOS 버전에 따라 없을 수 있어, 없으면 폴백 이름을 사용한다.
private func chatSFSymbol(_ primary: String, fallback: String) -> String {
    UIImage(systemName: primary) == nil ? fallback : primary
}

struct ChatScreen: View {
    @StateObject private var vm = ChatViewModel()
    @StateObject private var adManager = RewardedAdManagerBox()
    @State private var input = ""
    @State private var showConversations = false
    @State private var showEvolution = false
    @State private var showAttendance = false
    @FocusState private var isInputFocused: Bool

    private let accent = Color(red: 0.36, green: 0.42, blue: 0.98)

    var body: some View {
        VStack(spacing: 0) {
            header
            messageList
            inputBar
        }
        .background(Color(.systemGroupedBackground))
        .onAppear { vm.load() }
        .sheet(isPresented: $showConversations) {
            conversationListSheet
        }
        .sheet(isPresented: $showAttendance) {
            AttendanceSheet()
        }
        .sheet(isPresented: $vm.energyGateVisible, onDismiss: { vm.dismissGate() }) {
            EnergyGateSheet(vm: vm, adManager: adManager.manager)
                .presentationDetents([.height(300)])
        }
        .overlay(alignment: .top) {
            if let toast = vm.checkInToast {
                Text(toast)
                    .font(.subheadline.weight(.semibold))
                    .padding(.horizontal, 16).padding(.vertical, 10)
                    .background(.orange).foregroundStyle(.white)
                    .clipShape(Capsule())
                    .padding(.top, 8)
                    .transition(.move(edge: .top).combined(with: .opacity))
                    .task {
                        try? await Task.sleep(for: .seconds(2))
                        vm.checkInToast = nil
                    }
            }
        }
        .animation(.easeInOut, value: vm.checkInToast)
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
            Button { showAttendance = true } label: {
                Image(systemName: chatSFSymbol("calendar", fallback: "calendar.circle"))
                    .foregroundStyle(.primary)
            }
            if vm.hudLoaded {
                if let p = vm.points {
                    chip("🪙", "\(p)")
                }
                VStack(alignment: .trailing, spacing: 2) {
                    chip("⚡", "\(vm.energy)/\(vm.maxEnergy)", warning: vm.energy == 0)
                    if let iso = vm.nextRecoverAt {
                        RecoveryCountdown(nextRecoverAtIso: iso) { vm.refreshEnergy() }
                    }
                }
            }
            Button {
                vm.startNew()
            } label: {
                Image(systemName: chatSFSymbol("square.and.pencil", fallback: "plus.square"))
                    .foregroundStyle(.primary)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
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
                    }
                    ForEach(vm.items, id: \.id) { item in
                        row(for: item).id(item.id)
                    }
                    if vm.isStreaming {
                        HStack { ProgressView(); Spacer() }.padding(.horizontal, 4)
                    }
                }
                .padding()
            }
            .onChange(of: vm.items.count) { _ in
                if let last = vm.items.last { withAnimation { proxy.scrollTo(last.id, anchor: .bottom) } }
            }
        }
    }

    private var emptyState: some View {
        VStack(spacing: 10) {
            Text("CashAI 비서").font(.system(size: 26, weight: .bold))
            Text("궁금한 것은 무엇이든 물어보세요.\n대화할수록 포인트가 쌓여요!")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 60)
    }

    @ViewBuilder
    private func row(for item: ChatItem) -> some View {
        if let u = item as? ChatItemUserMessage {
            HStack {
                Spacer()
                Text(u.text)
                    .padding(.horizontal, 14).padding(.vertical, 10)
                    .background(accent).foregroundStyle(.white)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
            }
        } else if let a = item as? ChatItemAssistantMessage {
            HStack {
                VStack(alignment: .leading, spacing: 6) {
                    markdownText(a.text.isEmpty && a.isStreaming ? "…" : a.text)
                        .padding(.horizontal, 14).padding(.vertical, 10)
                        .background(Color(.secondarySystemGroupedBackground))
                        .foregroundStyle(.primary)
                        .clipShape(RoundedRectangle(cornerRadius: 14))
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
        // ChatItemProductCards 는 Slice 1d 에서 처리.
    }

    private var inputBar: some View {
        HStack(spacing: 8) {
            TextField("메시지를 입력하세요...", text: $input)
                .textFieldStyle(.roundedBorder)
                .tint(accent)
                .focused($isInputFocused)
                .onSubmit(sendCurrent)
            Button(action: sendCurrent) {
                Image(systemName: chatSFSymbol("paperplane.fill", fallback: "arrow.up.circle.fill"))
            }
            .disabled(input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || vm.isStreaming)
        }
        .padding(.horizontal, 14).padding(.vertical, 10)
        .background(Color(.systemBackground))
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
    }

    private var watchButton: some View {
        Button {
            vm.startAdReward { nonce in
                await withCheckedContinuation { cont in
                    adManager.show(
                        nonce: nonce,
                        onRewarded: { _ in },
                        onDismissed: { cont.resume(returning: true) },
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

/// 출석 캘린더 시트 — BenefitZone의 AttendanceWidgetView 재사용.
private struct AttendanceSheet: View {
    @StateObject private var vm = AttendanceViewModel()
    var body: some View {
        NavigationStack {
            ScrollView {
                AttendanceWidgetView(vm: vm).padding()
            }
            .navigationTitle("출석 체크")
            .navigationBarTitleDisplayMode(.inline)
            .onAppear { vm.load() }
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
                while !Task.isCancelled {
                    let sec = max(Int(target.timeIntervalSinceNow), 0)
                    remain = String(format: "%d:%02d 후 ⚡", sec / 60, sec % 60)
                    if sec == 0 { break }
                    try? await Task.sleep(for: .seconds(1))
                }
                onFinished()
            }
    }
}
