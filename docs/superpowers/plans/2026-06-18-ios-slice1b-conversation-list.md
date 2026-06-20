# iOS Slice 1b — 대화목록(사이드바) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** iOS 채팅에서 대화 목록을 조회하고(과거 대화), 선택해 열거나 새 대화를 시작할 수 있게 한다.

**Architecture:** Android(`ConversationListScreen`)와 동일하게 `ChatApi.listConversations()` 를 직접 호출해 목록을 가져오고, 열기는 `ChatStore.openConversation(id)` 를 사용한다. iOS 는 `KoinHelper` 에 `chatApi()` getter 를 추가하고, `ChatViewModel` 에 목록/열기 로직을, `ChatScreen` 에 사이드 시트를 더한다.

**Tech Stack:** KMM, Koin, SwiftUI.

**선행:** Slice 1a(채팅 코어) 커밋 `70ba073`.

## 검증된 사실 (헤더 실측)
- `ChatApi.listConversations()` → Swift `async throws -> [ConversationSummaryDto]`. `ChatApi` 는 Koin single(`SharedModule.kt`).
- `ConversationSummaryDto`: `conversationId: Int64`, `title: String`, `lastMessage: String?`, `createdAt: String`, `updatedAt: String`.
- `ChatStore.openConversation(id:)` → Swift `async`(throws), `startNewConversation()`.
- 빌드 시 `export JAVA_HOME="$(/usr/libexec/java_home -v 21)"`. SwiftUI ViewModel 은 `import Combine` 필수.

## File Structure
- Modify: `apps/frontend/shared/src/iosMain/.../di/IosBridges.kt` — `KoinHelper.chatApi()` getter.
- Modify: `apps/frontend/CashChatIOS/CashChatIOS/ChatViewModel.swift` — 목록/열기.
- Modify: `apps/frontend/CashChatIOS/CashChatIOS/ChatScreen.swift` — 햄버거 버튼 + 대화목록 시트.

범위 제외: 대화 삭제/이름변경(Android 도 `FeatureFlags.CONVERSATION_EDIT` 게이트로 비활성 — P2-1). 본 슬라이스는 조회/열기/새대화만.

---

### Task 1: `KoinHelper.chatApi()` getter 추가

**Files:** Modify `IosBridges.kt`

- [ ] **Step 1: import + getter 추가**

import 블록에 추가:
```kotlin
import com.nomadclub.cashchat.shared.chat.ChatApi
```
`KoinHelper` 내부에 추가:
```kotlin
    private val chatApiInstance: ChatApi by inject()
    fun chatApi(): ChatApi = chatApiInstance
```

- [ ] **Step 2: 컴파일 + 헤더 노출 검증**
```bash
cd /Users/gudals-mac/Documents/nomade/cash-chat-mvp/apps/frontend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
H="shared/build/bin/iosSimulatorArm64/debugFramework/CashChatShared.framework/Headers/CashChatShared.h"; grep -E "chatApi" "$H"
```
Expected: `chatApi()` 노출.

---

### Task 2: `ChatViewModel` 에 목록/열기 추가

**Files:** Modify `ChatViewModel.swift`

- [ ] **Step 1: 프로퍼티 + chatApi 추가**

`@Published var isStreaming = false` 아래에:
```swift
    @Published var conversations: [ConversationSummaryDto] = []
```
`private let store = KoinHelper().chatStore()` 아래에:
```swift
    private let chatApi = KoinHelper().chatApi()
```

- [ ] **Step 2: 메서드 추가**

`func startNew()` 아래에:
```swift
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
```

> `startNew()` 호출 시 목록 시트를 닫는 처리는 View 에서 한다.

---

### Task 3: `ChatScreen` 에 대화목록 시트 추가

**Files:** Modify `ChatScreen.swift`

- [ ] **Step 1: 상태 추가**

`@State private var input = ""` 아래에:
```swift
    @State private var showConversations = false
```

- [ ] **Step 2: 헤더에 햄버거 버튼 추가**

`header` 의 `HStack { Text("CashAI 비서")...` 에서 `Text` 앞(맨 왼쪽)에 버튼을 추가한다. 기존 `header` 를 아래로 교체:
```swift
    private var header: some View {
        HStack {
            Button {
                showConversations = true
            } label: {
                Image(systemName: "line.3.horizontal").foregroundStyle(.primary)
            }
            Spacer()
            Text("CashAI 비서").font(.headline)
            Spacer()
            Button {
                vm.startNew()
            } label: {
                Image(systemName: "square.and.pencil").foregroundStyle(.primary)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(Color(.systemBackground))
    }
```

- [ ] **Step 3: body 에 시트 연결**

`body` 의 `.onAppear { vm.load() }` 아래에 추가:
```swift
        .sheet(isPresented: $showConversations) {
            conversationListSheet
        }
```

- [ ] **Step 4: 시트 뷰 추가**

`markdownText(_:)` 아래(클래스 맨 끝 `}` 직전)에 추가:
```swift
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
```

---

### Task 4: 빌드 + 런타임 검증

- [ ] **Step 1: 프레임워크 임베드** — Xcode ⌘B 가 자동 호출(별도 불필요). 필요 시:
```bash
cd /Users/gudals-mac/Documents/nomade/cash-chat-mvp/apps/frontend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && ./gradlew :shared:embedAndSignAppleFrameworkForXcode
```
- [ ] **Step 2: Xcode ⌘B (사용자)** — `ChatViewModel`/`ChatScreen` 컴파일. `listConversations()` 반환형이 옵셔널이라고 나오면 `try await` 결과를 `?? []` 로 보정.
- [ ] **Step 3: 런타임 (사용자, 실서버)** — 좌상단 햄버거 → 대화목록 시트: 과거 대화 표시, 탭 시 해당 대화 열림(메시지 로드), "새 대화" 시 빈 화면.
- [ ] **Step 4: 회귀** — 채팅 전송/스트리밍 정상.

---

## Self-Review (작성자 점검 완료)
- **Spec 커버리지:** Slice 1(채팅)의 대화목록 부분(조회/열기/새대화). 삭제·이름변경은 Android 와 동일하게 범위 제외(FeatureFlag 비활성).
- **플레이스홀더:** 없음. 모든 코드 완전 포함.
- **타입 일관성:** `KoinHelper().chatApi()`, `ChatApi.listConversations() -> [ConversationSummaryDto]`, `ConversationSummaryDto.{conversationId:Int64,title,lastMessage}`, `ChatStore.openConversation(id:)` 헤더 실측 일치.
- **리스크:** `listConversations()` 비동기 반환 옵셔널 여부는 Xcode 에서 확정 — 옵셔널이면 `?? []` 보정(Step 2 명시).
