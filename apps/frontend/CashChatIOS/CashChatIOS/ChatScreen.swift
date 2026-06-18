import SwiftUI
import CashChatShared

struct ChatScreen: View {
    @StateObject private var vm = ChatViewModel()
    @State private var input = ""
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
    }

    private var header: some View {
        HStack {
            Text("CashAI 비서").font(.headline)
            Spacer()
            Button {
                vm.startNew()
            } label: {
                Image(systemName: "square.and.pencil")
                    .foregroundStyle(.primary)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(Color(.systemBackground))
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
                Image(systemName: "paperplane.fill")
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
}
