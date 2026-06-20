# iOS Slice 1a — 채팅 코어(전송 + SSE 스트리밍 렌더) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** iOS 채팅 탭에서 실제 AI 채팅이 동작하게 한다 — 메시지 전송 → 대화 자동 생성 → SSE 토큰 스트리밍 렌더 → 완료/에러/재시도. 기존 목업 상태머신을 브랜치 `ChatStore` 기반으로 재구성한다.

**Architecture:** `shared/iosMain` `FlowCollector` 에 채팅용 Flow 브리지를 추가하고, Swift `ChatViewModel`(ObservableObject)이 `KoinHelper().chatStore()` 를 감싸 `@Published items/isStreaming` 으로 노출한다. 새 `ChatScreen` SwiftUI 뷰가 이를 렌더하고, 탭 컨테이너의 채팅 탭을 이 뷰로 교체한다. 탭/네비 골격은 유지한다.

**Tech Stack:** Kotlin Multiplatform, Koin, Ktor SSE(이미 구현됨), SwiftUI, Combine.

**선행조건:** Slice 0(기반) 완료 — `KoinIosKt.doInitKoin`, `KoinHelper`, `FlowCollector` 존재 및 앱 시작 시 Koin 초기화. (`docs/superpowers/plans/2026-06-18-ios-slice0-foundation.md`)

---

## 검증된 사실 (헤더/소스 실측)

- `ChatStore` Swift 표면: `sendMessage(text:)`, `startNewConversation()`, `retryLastMessage()`, `openConversation(id:completionHandler:)`(async), 프로퍼티 `items`/`isStreaming`/`gateInfo`/`energyGateVisible`/`streamCompletedCount`(모두 StateFlow), `conversationId: KotlinLong?`.
- `ChatStore` 는 Koin single 로 등록됨(`SharedModule.kt`: `single { ChatStore(get(), get()) }`). `KoinHelper().chatStore()` 로 획득.
- `ChatItem`(sealed, Swift 프로토콜 `ChatItem`, `id: String`) 하위타입:
  - `ChatItemUserMessage`: `id: String`, `text: String`, `status: ChatItemSendStatus`
  - `ChatItemAssistantMessage`: `id: String`, `text: String`, `isStreaming: Bool`, `isError: Bool`, `gated: Bool`
  - `ChatItemProductCards`: (1d 에서 처리 — 본 플랜은 무시)
- `ChatStore.items` = `StateFlow<List<ChatItem>>`, `isStreaming` = `StateFlow<Boolean>`.
- SSE/스트리밍 로직(`ChatApi.streamMessage`, `ChatStore.stream`)은 이미 commonMain 에 구현됨 — iOS 는 소비만 한다. (단, iOS Darwin 엔진의 SSE 실동작은 본 플랜 런타임 검증에서 처음 확인된다.)
- 현재 채팅 탭: `MainTabContainer.body`(ContentView.swift 라인 ~385) 가 `ChatView()`(OnboardingView 내 private struct, ~500줄 목업)를 사용.
- 빌드 시 `export JAVA_HOME="$(/usr/libexec/java_home -v 21)"` 필요.

## File Structure

- Modify: `apps/frontend/shared/src/iosMain/kotlin/com/nomadclub/cashchat/shared/di/IosBridges.kt` — `FlowCollector` 에 채팅 collector 2개 추가.
- Create: `apps/frontend/CashChatIOS/CashChatIOS/ChatViewModel.swift` — `ChatStore` 래퍼 ObservableObject.
- Create: `apps/frontend/CashChatIOS/CashChatIOS/ChatScreen.swift` — store 기반 채팅 화면.
- Modify: `apps/frontend/CashChatIOS/CashChatIOS/ContentView.swift` — `MainTabContainer` 채팅 탭을 `ChatScreen()` 으로 교체.

본 플랜 범위(1a) **제외**: 사이드바/대화목록(1b), 에너지 게이트(1c), 상품카드·광고게이트(1d), 진화·아바타(1e). 기존 목업 `ChatView` struct 는 제거하지 않고 남겨둔다(미사용) — 후속 슬라이스에서 정리.

---

### Task 1: `FlowCollector` 에 채팅 collector 추가

**Files:**
- Modify: `apps/frontend/shared/src/iosMain/kotlin/com/nomadclub/cashchat/shared/di/IosBridges.kt`

- [ ] **Step 1: import 추가**

`IosBridges.kt` 상단 import 블록에 다음을 추가한다(이미 `ChatStore` import 가 있으면 `ChatItem` 만 추가):

```kotlin
import com.nomadclub.cashchat.shared.chat.ChatStore
import com.nomadclub.cashchat.shared.chat.model.ChatItem
```

- [ ] **Step 2: `FlowCollector` 클래스에 메서드 2개 추가**

`FlowCollector` 의 `cancel()` 위에 추가한다:

```kotlin
    fun collectChatItems(store: ChatStore, onEach: (List<ChatItem>) -> Unit) {
        scope.launch { store.items.collect { onEach(it) } }
    }

    fun collectIsStreaming(store: ChatStore, onEach: (Boolean) -> Unit) {
        scope.launch { store.isStreaming.collect { onEach(it) } }
    }
```

- [ ] **Step 3: 컴파일 검증**

```bash
cd /Users/gudals-mac/Documents/nomade/cash-chat-mvp/apps/frontend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && ./gradlew :shared:compileKotlinIosSimulatorArm64
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: 프레임워크 링크로 Swift 노출 확인**

```bash
cd /Users/gudals-mac/Documents/nomade/cash-chat-mvp/apps/frontend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
H="shared/build/bin/iosSimulatorArm64/debugFramework/CashChatShared.framework/Headers/CashChatShared.h"
grep -E "collectChatItems|collectIsStreaming" "$H"
```
Expected: 두 메서드가 `FlowCollector` 인터페이스에 노출됨(`collectChatItems(store:onEach:)`, `collectIsStreaming(store:onEach:)`).

---

### Task 2: `ChatViewModel.swift` 생성

**Files:**
- Create: `apps/frontend/CashChatIOS/CashChatIOS/ChatViewModel.swift`

- [ ] **Step 1: 파일 생성**

```swift
import Foundation
import SwiftUI
import CashChatShared

/// 브랜치 shared `ChatStore` 를 감싸는 iOS 채팅 ViewModel.
/// Flow 구독은 FlowCollector(메인 디스패처)로 브리지하고, deinit 에서 취소해 누수를 막는다.
@MainActor
final class ChatViewModel: ObservableObject {
    @Published var items: [ChatItem] = []
    @Published var isStreaming = false

    private let store = KoinHelper().chatStore()
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
    }

    func send(_ text: String) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        store.sendMessage(text: trimmed)
    }

    func startNew() {
        store.startNewConversation()
    }

    /// 스트림 단절/에러 후 마지막 user 메시지 재전송.
    func retry() {
        store.retryLastMessage()
    }
}
```

> 주의: `collectIsStreaming` 콜백의 Kotlin `Boolean` 은 Swift 에서 `KotlinBoolean` 으로 들어오므로 `.boolValue` 로 변환한다. `collectChatItems` 콜백의 `List<ChatItem>` 은 Swift `[ChatItem]` 로 브리지된다. (Xcode 빌드에서 정확한 타입이 다르면 클로저 파라미터 타입을 자동완성에 맞춰 조정.)

- [ ] **Step 2: 컴파일 검증은 Task 4(Xcode 빌드)에서**

---

### Task 3: `ChatScreen.swift` 생성 (store 기반 채팅 화면)

**Files:**
- Create: `apps/frontend/CashChatIOS/CashChatIOS/ChatScreen.swift`

- [ ] **Step 1: 파일 생성**

기존 목업의 시각 스타일(상단 바 + 포인트 칩, 말풍선, idle 안내, 하단 입력바)을 유지하되 상태는 `ChatViewModel` 에서 가져온다. 사이드바/광고/리워드모달은 본 슬라이스 범위 밖이라 포함하지 않는다.

```swift
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
                    Text(a.text.isEmpty && a.isStreaming ? "…" : a.text)
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
}
```

- [ ] **Step 2: 컴파일 검증은 Task 4(Xcode 빌드)에서**

---

### Task 4: 채팅 탭 교체 + 빌드 + 런타임 검증

**Files:**
- Modify: `apps/frontend/CashChatIOS/CashChatIOS/ContentView.swift` (라인 ~385, `MainTabContainer.body`)

- [ ] **Step 1: 채팅 탭을 `ChatScreen` 으로 교체**

`MainTabContainer.body` 의 TabView 첫 항목을 교체한다:

변경 전:
```swift
                ChatView()
                    .tabItem { Label(MainTab.chat.rawValue, systemImage: MainTab.chat.icon) }
                    .tag(MainTab.chat)
```
변경 후:
```swift
                ChatScreen()
                    .tabItem { Label(MainTab.chat.rawValue, systemImage: MainTab.chat.icon) }
                    .tag(MainTab.chat)
```

> 기존 `ChatView` private struct 와 그 의존 타입(`ChatRow`/`AdInfo`/`ChatSession` 등)은 그대로 둔다(미사용 경고는 무시; 후속 슬라이스에서 정리). 컴파일 에러가 나면 그때 제거 범위를 판단한다.

- [ ] **Step 2: 프레임워크 임베드**

```bash
cd /Users/gudals-mac/Documents/nomade/cash-chat-mvp/apps/frontend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && ./gradlew :shared:embedAndSignAppleFrameworkForXcode
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Xcode 빌드 (사용자 수행 — GUI)**

`apps/frontend/CashChatIOS/CashChatIOS.xcodeproj` 를 열고 시뮬레이터로 ⌘B.
Expected: 빌드 성공. `ChatViewModel`/`ChatScreen` 컴파일 통과, `vm.items`/`as? ChatItemUserMessage` 캐스팅 정상.
- 만약 `collectChatItems` 클로저 파라미터 타입 또는 `as?` 캐스팅 타입명이 다르면 Xcode 자동완성으로 정확한 이름을 확인해 조정.

- [ ] **Step 4: 런타임 검증 (사용자 수행 — 실서버 필요)**

로그인 후 채팅 탭에서:
1. 메시지 입력 → 전송 → **AI 응답이 토큰 단위로 스트리밍** 표시되는지 확인(빈 말풍선 "…" → 점진 채워짐).
2. 스트리밍 중 입력/전송 버튼 비활성화 확인.
3. 응답 완료 후 정상 종료.
4. (가능하면) 네트워크 끊고 전송 → 에러 말풍선 + "다시 시도" 버튼 → 복구 후 재시도 동작.
5. 우상단 새 대화 버튼 → 목록 비워짐.

> **iOS SSE 리스크:** Darwin(NSURLSession) 엔진의 SSE 실동작은 이 단계에서 처음 검증된다. 토큰이 한 번에 몰려 오거나(버퍼링) 스트리밍이 안 되면, `HttpClientEngine.ios.kt`/`ChatApi.streamMessage` 의 Darwin 설정(예: `URLSession` 구성)을 점검해야 한다. 증상 발견 시 별도 디버깅 이슈로 보고.

- [ ] **Step 5: 회귀 확인**

리워드/상점/마이페이지 탭이 기존 목업 그대로 표시되는지(깨짐 없음) 확인.

---

## Self-Review (작성자 점검 완료)

- **Spec 커버리지:** 본 플랜은 spec `2026-06-18-ios-feature-parity-design.md` 의 Slice 1 중 **코어(전송 + SSE 스트리밍 렌더 + 대화 생성 + 에러/재시도)** 를 구현한다. 사이드바/에너지게이트/상품카드/진화는 후속 슬라이스(1b~1e)로 명시 분리.
- **플레이스홀더:** 없음. 모든 코드 단계에 완전한 코드 포함. 범위 제외 항목은 의도적 분리이며 "나중에 구현" 식 빈칸이 아님.
- **타입 일관성:** `KoinHelper().chatStore()`, `FlowCollector.collectChatItems/collectIsStreaming`, `ChatStore.sendMessage(text:)/startNewConversation()/retryLastMessage()`, `ChatItemUserMessage.text`, `ChatItemAssistantMessage.{text,isStreaming,isError}` 가 헤더 실측과 일치.
- **알려진 리스크:** (1) KMM→Swift 콜백 타입(`KotlinBoolean`, `[ChatItem]`)은 Xcode 빌드에서 확정 — 불일치 시 조정 단계 명시. (2) iOS Darwin SSE 실동작은 런타임에서 최초 검증 — 실패 시 디버깅 이슈로 분리.
