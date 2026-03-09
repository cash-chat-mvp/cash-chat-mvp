import SwiftUI
import Combine
import UIKit

private func sfSymbol(_ primary: String, fallback: String) -> String {
    UIImage(systemName: primary) == nil ? fallback : primary
}

struct ContentView: View {
    @StateObject private var appState = AppState()
    @State private var isOnboarding = true

    var body: some View {
        Group {
            if isOnboarding {
                OnboardingView {
                    isOnboarding = false
                }
            } else {
                OnboardingView.MainTabContainer()
                    .environmentObject(appState)
            }
        }
    }
}

final class AppState: ObservableObject {
    @Published var points: Int = 0
    @Published var messageCount: Int = 0

    func addPoints(_ value: Int) {
        guard value > 0 else { return }
        points += value
    }

    func spendPoints(_ value: Int) -> Bool {
        guard value > 0 else { return false }
        guard points >= value else { return false }
        points -= value
        return true
    }

    func incrementMessageCount() {
        messageCount += 1
        addPoints(10)
    }
}

final class KeyboardObserver: ObservableObject {
    @Published var isVisible = false
    private var observers: [NSObjectProtocol] = []

    init() {
        let center = NotificationCenter.default
        observers.append(
            center.addObserver(forName: UIResponder.keyboardWillShowNotification, object: nil, queue: .main) { [weak self] _ in
                self?.isVisible = true
            }
        )
        observers.append(
            center.addObserver(forName: UIResponder.keyboardWillHideNotification, object: nil, queue: .main) { [weak self] _ in
                self?.isVisible = false
            }
        )
    }

    deinit {
        observers.forEach { NotificationCenter.default.removeObserver($0) }
    }
}

struct OnboardingView: View {
    let onStart: () -> Void
    
    var body: some View {
        GeometryReader { _ in
            ZStack {
                LinearGradient(
                    colors: [Color(red: 0.36, green: 0.42, blue: 0.98), Color(red: 0.29, green: 0.35, blue: 0.91)],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .ignoresSafeArea()
                
                VStack(spacing: 24) {
                    Spacer()
                    VStack(spacing: 12) {
                        Image(systemName: sfSymbol("sparkles", fallback: "star.fill"))
                            .font(.system(size: 74))
                            .foregroundStyle(.white)
                        Text("AI Chat+")
                            .font(.system(size: 42, weight: .bold))
                            .foregroundStyle(.white)
                        Text("대화하고 포인트 받자!")
                            .font(.title3)
                            .foregroundStyle(.white.opacity(0.9))
                    }
                    
                    Spacer()
                    
                    VStack(spacing: 12) {
                        Button("카카오로 3초 만에 시작하기", action: onStart)
                            .buttonStyle(FilledButtonStyle(background: .white, foreground: .blue))
                        Button("Apple로 로그인", action: onStart)
                            .buttonStyle(FilledButtonStyle(background: .black, foreground: .white))
                        Text("가입하면 즉시 500P 지급!")
                            .font(.footnote)
                            .foregroundStyle(.white.opacity(0.7))
                    }
                    .padding(.bottom, 24)
                }
                .padding(.horizontal, 24)
            }
        }
    }
    
    private struct FilledButtonStyle: ButtonStyle {
        let background: Color
        let foreground: Color
        
        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .font(.system(size: 17, weight: .semibold))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)
                .background(background.opacity(configuration.isPressed ? 0.85 : 1))
                .foregroundStyle(foreground)
                .clipShape(RoundedRectangle(cornerRadius: 14))
        }
    }
    
    private enum MainTab: String, CaseIterable {
        case chat = "채팅"
        case rewards = "리워드"
        case shop = "상점"
        case mypage = "마이페이지"
        
        var icon: String {
            switch self {
            case .chat: return sfSymbol("message.fill", fallback: "text.bubble.fill")
            case .rewards: return sfSymbol("gift.fill", fallback: "star.fill")
            case .shop: return sfSymbol("bag.fill", fallback: "cart.fill")
            case .mypage: return sfSymbol("person.fill", fallback: "person.crop.circle.fill")
            }
        }
    }
    
    struct MainTabContainer: View {
        @EnvironmentObject private var appState: AppState
        @State private var selected: MainTab = .chat
        @StateObject private var keyboard = KeyboardObserver()
        
        var body: some View {
            TabView(selection: $selected) {
                ChatView()
                    .tabItem { Label(MainTab.chat.rawValue, systemImage: MainTab.chat.icon) }
                    .tag(MainTab.chat)
                
                RewardsView()
                    .tabItem { Label(MainTab.rewards.rawValue, systemImage: MainTab.rewards.icon) }
                    .tag(MainTab.rewards)
                
                ShopView()
                    .tabItem { Label(MainTab.shop.rawValue, systemImage: MainTab.shop.icon) }
                    .tag(MainTab.shop)
                
                MyPageView()
                    .tabItem { Label(MainTab.mypage.rawValue, systemImage: MainTab.mypage.icon) }
                    .tag(MainTab.mypage)
            }
            .tint(.orange)
            .toolbar(keyboard.isVisible ? .hidden : .visible, for: .tabBar)
        }
    }
    
    private struct AdInfo {
        let brand: String
        let tagline: String
        let cta: String
        let emoji: String
        let bg: Color
        let accent: Color
        let category: String
    }
    
    private enum ChatRow: Identifiable {
        case text(id: String, text: String, isUser: Bool)
        case inlineAd(id: String, ad: AdInfo)
        case rewardPrompt(id: String)
        
        var id: String {
            switch self {
            case .text(let id, _, _): return id
            case .inlineAd(let id, _): return id
            case .rewardPrompt(let id): return id
            }
        }
    }
    
    private struct ChatSession: Identifiable {
        let id: String
        var title: String
        var preview: String
        var updatedAt: Date
        var messages: [ChatRow]
    }
    
    private struct ChatView: View {
        @EnvironmentObject private var appState: AppState
        @State private var animateIn = false
        @State private var sessions: [ChatSession] = [
            ChatSession(
                id: "new-chat",
                title: "새 채팅",
                preview: "새로운 대화를 시작해보세요",
                updatedAt: Date(),
                messages: [.text(id: "1", text: "안녕하세요! 저는 CashAI 비서예요 🤖\n무엇이든 물어보세요. 대화할수록 포인트도 쌓여요!", isUser: false)]
            ),
            ChatSession(
                id: "history-1",
                title: "점심 메뉴 추천",
                preview: "오늘 점심으로는 파스타 어때요?",
                updatedAt: Date().addingTimeInterval(-86400),
                messages: [
                    .text(id: "h1-1", text: "안녕하세요! 저는 CashAI 비서예요 🤖\n무엇이든 물어보세요. 대화할수록 포인트도 쌓여요!", isUser: false),
                    .text(id: "h1-2", text: "오늘 점심 추천해줘", isUser: true),
                    .text(id: "h1-3", text: "오늘 점심으로는 파스타 어때요? 가볍고 맛있어요.", isUser: false)
                ]
            ),
            ChatSession(
                id: "history-2",
                title: "여행 계획",
                preview: "2박 3일 제주 여행 코스를 정리해드릴게요",
                updatedAt: Date().addingTimeInterval(-172800),
                messages: [
                    .text(id: "h2-1", text: "안녕하세요! 저는 CashAI 비서예요 🤖\n무엇이든 물어보세요. 대화할수록 포인트도 쌓여요!", isUser: false),
                    .text(id: "h2-2", text: "제주도 여행 계획 짜줘", isUser: true),
                    .text(id: "h2-3", text: "2박 3일 제주 여행 코스를 정리해드릴게요.", isUser: false)
                ]
            )
        ]
        @State private var selectedSessionId: String = "new-chat"
        @State private var messages: [ChatRow] = [.text(id: "1", text: "안녕하세요! 저는 CashAI 비서예요 🤖\n무엇이든 물어보세요. 대화할수록 포인트도 쌓여요!", isUser: false)]
        @State private var showSidebar = false
        @State private var input = ""
        @State private var isLoading = false
        @State private var sentCount = 0
        @State private var chatIdle = true
        @State private var pendingText = ""
        @State private var showRewardModal = false
        @State private var rewardIndex = 0
        @State private var replyTask: Task<Void, Never>?
        @StateObject private var keyboard = KeyboardObserver()
        @FocusState private var isInputFocused: Bool
        
        private let suggestions = ["오늘 점심 추천해줘", "여행 계획 짜줘", "영어 공부 방법", "다이어트 팁"]
        private let foodAd = AdInfo(brand: "배달의민족", tagline: "지금 주문하면 3,000원 즉시 할인!", cta: "지금 주문하기", emoji: "🍔", bg: Color(red: 1.0, green: 0.96, blue: 0.91), accent: .orange, category: "음식·배달")
        private let defaultAd = AdInfo(brand: "CashAI Premium", tagline: "광고 없이 AI와 무제한 대화!", cta: "업그레이드", emoji: "⭐", bg: Color(red: 1.0, green: 0.97, blue: 0.90), accent: Color(red: 0.80, green: 0.53, blue: 0.0), category: "프리미엄")
        private let rewardAds = [
            AdInfo(brand: "삼성 갤럭시 S25", tagline: "새로운 AI 폰의 시작. 사전예약 시 Galaxy Buds 증정!", cta: "사전예약 하기", emoji: "📱", bg: Color(red: 0.91, green: 0.94, blue: 1.0), accent: Color(red: 0.08, green: 0.16, blue: 0.63), category: "전자제품"),
            AdInfo(brand: "스타벅스 코리아", tagline: "봄 시즌 신메뉴 출시! 앱 주문 시 별 2배 적립", cta: "앱으로 주문", emoji: "☕", bg: Color(red: 0.91, green: 0.96, blue: 0.92), accent: Color(red: 0.0, green: 0.44, blue: 0.29), category: "음료·카페"),
            AdInfo(brand: "현대자동차 아이오닉6", tagline: "전기차 시대의 혁신. 지금 시승 신청하세요!", cta: "시승 신청", emoji: "🚗", bg: Color(red: 1.0, green: 0.95, blue: 0.90), accent: Color(red: 0.0, green: 0.17, blue: 0.37), category: "자동차")
        ]
        
        var body: some View {
            GeometryReader { geo in
                let topSafe = geo.safeAreaInsets.top
                let bottomSafe = geo.safeAreaInsets.bottom
                let sidebarWidth = min(max(geo.size.width * 0.76, 270), 320)
                
                ZStack(alignment: .leading) {
                    VStack(spacing: 0) {
                        HStack {
                            Button {
                                withAnimation(.spring(response: 0.32, dampingFraction: 0.86)) {
                                    showSidebar = true
                                }
                            } label: {
                                Image(systemName: sfSymbol("line.3.horizontal", fallback: "line.horizontal.3"))
                                    .foregroundStyle(.primary)
                            }
                            Spacer()
                            Text("CashAI 비서")
                                .font(.headline)
                            Spacer()
                            Text("🪙 \(appState.points) P")
                                .font(.subheadline.weight(.bold))
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background(.orange)
                                .foregroundStyle(.white)
                                .clipShape(Capsule())
                        }
                        .padding(.top, 8)
                        .padding(.horizontal, 16)
                        .padding(.bottom, 10)
                        .background(.white)
                        
                        ScrollView {
                            LazyVStack(spacing: 10) {
                                ForEach(messages) { row in
                                    switch row {
                                    case .text(_, let text, let isUser):
                                        HStack {
                                            if isUser { Spacer() }
                                            Text(text)
                                                .padding(.horizontal, 14)
                                                .padding(.vertical, 10)
                                                .background(isUser ? Color(red: 0.36, green: 0.42, blue: 0.98) : Color.white)
                                                .foregroundStyle(isUser ? .white : .primary)
                                                .clipShape(RoundedRectangle(cornerRadius: 14))
                                            if !isUser { Spacer() }
                                        }
                                    case .inlineAd(_, let ad):
                                        VStack(alignment: .leading, spacing: 8) {
                                            Text("💡 맞춤 광고 · \(ad.category)")
                                                .font(.caption2)
                                                .foregroundStyle(.secondary)
                                            HStack(spacing: 10) {
                                                Text(ad.emoji).font(.title)
                                                VStack(alignment: .leading, spacing: 2) {
                                                    Text(ad.brand).font(.subheadline.weight(.bold))
                                                    Text(ad.tagline).font(.caption).foregroundStyle(.secondary)
                                                }
                                            }
                                            Button(ad.cta) { }
                                                .font(.caption.weight(.bold))
                                                .frame(maxWidth: .infinity)
                                                .padding(.vertical, 8)
                                                .background(ad.accent)
                                                .foregroundStyle(.white)
                                                .clipShape(RoundedRectangle(cornerRadius: 10))
                                        }
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                        .padding(12)
                                        .background(ad.bg)
                                        .clipShape(RoundedRectangle(cornerRadius: 16))
                                    case .rewardPrompt:
                                        VStack(alignment: .leading, spacing: 8) {
                                            Text("🤔 AI가 생각을 오래하네요...")
                                                .font(.subheadline.weight(.bold))
                                            Text("복잡한 질문이라 분석에 시간이 걸리고 있어요.\n짧은 광고를 보고 오시면 바로 답변 드릴게요! 🎁")
                                                .font(.caption)
                                                .foregroundStyle(.secondary)
                                            Button {
                                                showRewardModal = true
                                            } label: {
                                                HStack {
                                                    Image(systemName: sfSymbol("play.fill", fallback: "play.circle.fill"))
                                                    Text("광고 보고 바로 답변 받기")
                                                    Spacer()
                                                    Text("+30P")
                                                        .font(.caption2.weight(.bold))
                                                        .padding(.horizontal, 6)
                                                        .padding(.vertical, 3)
                                                        .background(.white.opacity(0.2))
                                                        .clipShape(Capsule())
                                                }
                                            }
                                            .buttonStyle(.borderedProminent)
                                            .tint(.orange)
                                        }
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                        .padding(12)
                                        .background(.white)
                                        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.blue.opacity(0.3), lineWidth: 1))
                                        .clipShape(RoundedRectangle(cornerRadius: 16))
                                    }
                                }
                                
                                if isLoading {
                                    HStack {
                                        ProgressView()
                                        Spacer()
                                    }
                                    .padding(12)
                                }
                            }
                            .padding()
                            .padding(.bottom, 8)
                        }
                        .background(Color(white: 0.96))
                        
                        HStack(spacing: 8) {
                            TextField("메시지를 입력하세요...", text: $input)
                                .textFieldStyle(.roundedBorder)
                                .foregroundStyle(Color(red: 0.12, green: 0.14, blue: 0.18))
                                .tint(Color(red: 0.36, green: 0.42, blue: 0.98))
                                .focused($isInputFocused)
                            Button(action: { submit() }) {
                                Image(systemName: sfSymbol("paperplane.fill", fallback: "arrow.up.circle.fill"))
                            }
                            .disabled(input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isLoading)
                        }
                        .padding(.horizontal, 14)
                        .padding(.top, 10)
                        .padding(.bottom, keyboard.isVisible ? 14 : 10)
                        .background(.white)
                    }
                    
                    if chatIdle {
                        ZStack {
                            LinearGradient(
                                colors: [Color(red: 0.93, green: 0.94, blue: 1.0), Color(red: 0.97, green: 0.96, blue: 1.0), Color(red: 1.0, green: 0.96, blue: 0.93)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                            .ignoresSafeArea()
                            
                            VStack(spacing: 18) {
                                Spacer()
                                RoundedRectangle(cornerRadius: 26)
                                    .fill(Color(red: 0.36, green: 0.42, blue: 0.98))
                                    .frame(width: 88, height: 88)
                                    .overlay(Image(systemName: sfSymbol("sparkles", fallback: "star.fill")).font(.system(size: 40)).foregroundStyle(.white))
                                Text("CashAI 비서").font(.system(size: 33, weight: .black))
                                Text("궁금한 것은 무엇이든 물어보세요.\n대화할수록 포인트가 쌓여요!")
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                                    .multilineTextAlignment(.center)
                                
                                HStack(spacing: 8) {
                                    TextField("질문을 입력해보세요...", text: $input)
                                        .textFieldStyle(.roundedBorder)
                                        .foregroundStyle(Color(red: 0.12, green: 0.14, blue: 0.18))
                                        .tint(Color(red: 0.36, green: 0.42, blue: 0.98))
                                        .focused($isInputFocused)
                                    Button(action: { submit() }) {
                                        Image(systemName: sfSymbol("paperplane.fill", fallback: "arrow.up.circle.fill"))
                                    }
                                    .frame(width: 48, height: 48)
                                    .buttonStyle(.borderedProminent)
                                    .tint(Color(red: 0.36, green: 0.42, blue: 0.98))
                                    .disabled(input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                                }
                                .padding(6)
                                .background(.white.opacity(0.85))
                                .clipShape(RoundedRectangle(cornerRadius: 24))
                                
                                HStack(spacing: 8) {
                                    ForEach(suggestions.prefix(3), id: \.self) { s in
                                        Button(s) { submit(override: s) }
                                            .font(.caption.weight(.semibold))
                                            .padding(.horizontal, 10)
                                            .padding(.vertical, 8)
                                            .background(.white.opacity(0.8))
                                            .clipShape(Capsule())
                                    }
                                }
                                Spacer()
                            }
                            .padding(.top, topSafe + 6)
                            .padding(.horizontal, 24)
                            .padding(.bottom, bottomSafe + 12)
                        }
                        .transition(.opacity.combined(with: .scale(scale: 0.985)))
                    }
                    
                    Color.black.opacity(showSidebar ? 0.16 : 0)
                        .ignoresSafeArea()
                        .allowsHitTesting(showSidebar)
                        .onTapGesture {
                            withAnimation(.spring(response: 0.32, dampingFraction: 0.88)) {
                                showSidebar = false
                            }
                        }
                        .zIndex(3)
                    
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            Text("채팅 내역")
                                .font(.headline.weight(.bold))
                            Spacer()
                            Button {
                                withAnimation(.spring(response: 0.32, dampingFraction: 0.88)) {
                                    showSidebar = false
                                }
                            } label: {
                                Image(systemName: sfSymbol("xmark", fallback: "xmark.circle"))
                                    .font(.system(size: 13, weight: .bold))
                                    .padding(8)
                                    .background(Color(white: 0.94))
                                    .clipShape(Circle())
                            }
                            .buttonStyle(.plain)
                        }
                        
                        Button("+ 새 채팅") {
                            createNewSession()
                        }
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color(red: 0.36, green: 0.42, blue: 0.98))
                        
                        ScrollView {
                            VStack(spacing: 8) {
                                ForEach(sessions.sorted(by: { $0.updatedAt > $1.updatedAt })) { session in
                                    Button {
                                        selectSession(session)
                                    } label: {
                                        VStack(alignment: .leading, spacing: 4) {
                                            Text(session.title)
                                                .font(.subheadline.weight(.semibold))
                                                .foregroundStyle(.primary)
                                            Text(session.preview)
                                                .font(.caption)
                                                .foregroundStyle(.secondary)
                                                .lineLimit(1)
                                        }
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                        .padding(10)
                                        .background(session.id == selectedSessionId ? Color(red: 0.93, green: 0.95, blue: 1.0) : Color.clear)
                                        .clipShape(RoundedRectangle(cornerRadius: 10))
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                            .padding(.bottom, bottomSafe + 8)
                        }
                    }
                    .padding(.horizontal, 14)
                    .padding(.top, 8)
                    .padding(.bottom, bottomSafe + 8)
                    .frame(width: sidebarWidth)
                    .frame(maxHeight: .infinity, alignment: .top)
                    .background(Color(white: 0.985))
                    .overlay(alignment: .trailing) {
                        Rectangle().fill(Color.black.opacity(0.06)).frame(width: 0.5)
                    }
                    .shadow(color: Color.black.opacity(0.12), radius: 10, x: 4, y: 0)
                    .offset(x: showSidebar ? 0 : -sidebarWidth - 24)
                    .allowsHitTesting(showSidebar)
                    .zIndex(4)
                    
                    if showRewardModal {
                        RewardAdModalView(ad: rewardAds[rewardIndex % rewardAds.count], onClose: {
                            showRewardModal = false
                            if !pendingText.isEmpty {
                                appendAiOnly(from: pendingText)
                                pendingText = ""
                            }
                        }, onComplete: {
                            appState.addPoints(30)
                            rewardIndex += 1
                            showRewardModal = false
                            if !pendingText.isEmpty {
                                appendAiOnly(from: pendingText)
                                pendingText = ""
                            }
                        })
                        .zIndex(5)
                    }
                }
                .background(Color(white: 0.96).ignoresSafeArea())
                .animation(.easeInOut(duration: 0.18), value: chatIdle)
                .animation(.spring(response: 0.32, dampingFraction: 0.86), value: showSidebar)
                .opacity(animateIn ? 1 : 0)
                .offset(y: animateIn ? 0 : 14)
                .animation(.easeOut(duration: 0.34), value: animateIn)
                .simultaneousGesture(
                    TapGesture().onEnded {
                        isInputFocused = false
                    }
                )
                .onAppear {
                    animateIn = false
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.03) {
                        withAnimation(.easeOut(duration: 0.34)) {
                            animateIn = true
                        }
                    }
                }
                .onDisappear {
                    replyTask?.cancel()
                    animateIn = false
                }
            }
        }
        
        private func submit(override: String? = nil) {
            let text = (override ?? input).trimmingCharacters(in: .whitespacesAndNewlines)
            guard !text.isEmpty, !isLoading else { return }
            isInputFocused = false
            let sessionId = selectedSessionId
            chatIdle = false
            input = ""
            sentCount += 1
            let turn = sentCount
            appState.incrementMessageCount()
            setMessages(messages + [.text(id: UUID().uuidString, text: text, isUser: true)], for: sessionId)
            isLoading = true
            
            replyTask?.cancel()
            replyTask = Task {
                do {
                    try await Task.sleep(for: .milliseconds(1400))
                } catch {
                    return
                }
                guard !Task.isCancelled, sessionId == selectedSessionId else { return }
                isLoading = false
                
                if turn % 3 == 0 {
                    pendingText = text
                    appendMessage(.rewardPrompt(id: UUID().uuidString), to: sessionId)
                } else {
                    appendAiAndAd(from: text, to: sessionId)
                }
            }
        }
        
        private func appendAiAndAd(from text: String, to sessionId: String) {
            let answer: String
            if text.contains("안녕") {
                answer = "안녕하세요! 😊 저는 CashAI 비서예요."
            } else if text.contains("음식") {
                answer = "오늘 뭐 드실지 고민이시군요! 😋"
            } else {
                answer = "'\(String(text.prefix(15)))...'에 대해 답변 드릴게요! 🤖"
            }
            appendMessage(.text(id: UUID().uuidString, text: answer, isUser: false), to: sessionId)
            let ad = text.contains("음식") ? foodAd : defaultAd
            appendMessage(.inlineAd(id: UUID().uuidString, ad: ad), to: sessionId)
        }
        
        private func appendAiOnly(from text: String, to sessionId: String? = nil) {
            let targetSessionId = sessionId ?? selectedSessionId
            let answer: String
            if text.contains("안녕") {
                answer = "안녕하세요! 😊 저는 CashAI 비서예요."
            } else if text.contains("음식") {
                answer = "오늘 뭐 드실지 고민이시군요! 😋"
            } else {
                answer = "'\(String(text.prefix(15)))...'에 대해 답변 드릴게요! 🤖"
            }
            appendMessage(.text(id: UUID().uuidString, text: answer, isUser: false), to: targetSessionId)
        }
        
        private func createNewSession() {
            replyTask?.cancel()
            let id = "new-\(UUID().uuidString)"
            let welcome: [ChatRow] = [.text(id: UUID().uuidString, text: "안녕하세요! 저는 CashAI 비서예요 🤖\n무엇이든 물어보세요. 대화할수록 포인트도 쌓여요!", isUser: false)]
            let session = ChatSession(
                id: id,
                title: "새 채팅",
                preview: "새로운 대화를 시작해보세요",
                updatedAt: Date(),
                messages: welcome
            )
            sessions.insert(session, at: 0)
            selectedSessionId = id
            messages = welcome
            input = ""
            isLoading = false
            pendingText = ""
            chatIdle = true
            withAnimation(.spring(response: 0.32, dampingFraction: 0.88)) {
                showSidebar = false
            }
        }
        
        private func selectSession(_ session: ChatSession) {
            replyTask?.cancel()
            selectedSessionId = session.id
            messages = session.messages
            input = ""
            isLoading = false
            pendingText = ""
            chatIdle = session.messages.count <= 1
            withAnimation(.spring(response: 0.32, dampingFraction: 0.88)) {
                showSidebar = false
            }
        }
        
        private func sessionPreview(_ rows: [ChatRow]) -> String {
            for row in rows.reversed() {
                if case let .text(_, text, _) = row {
                    let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
                    if !trimmed.isEmpty { return String(trimmed.prefix(36)) }
                }
            }
            return "새로운 대화를 시작해보세요"
        }
        
        private func sessionTitle(from rows: [ChatRow], currentTitle: String) -> String {
            guard currentTitle == "새 채팅" else { return currentTitle }
            for row in rows {
                if case let .text(_, text, isUser) = row, isUser {
                    let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
                    if !trimmed.isEmpty { return String(trimmed.prefix(18)) }
                }
            }
            return currentTitle
        }
        
        private func setMessages(_ newMessages: [ChatRow]) {
            setMessages(newMessages, for: selectedSessionId)
        }
        
        private func appendMessage(_ row: ChatRow, to sessionId: String) {
            let current = sessions.first(where: { $0.id == sessionId })?.messages ?? []
            setMessages(current + [row], for: sessionId)
        }
        
        private func setMessages(_ newMessages: [ChatRow], for sessionId: String) {
            if selectedSessionId == sessionId {
                withAnimation(.easeOut(duration: 0.22)) {
                    messages = newMessages
                }
            }
            if let idx = sessions.firstIndex(where: { $0.id == sessionId }) {
                sessions[idx].messages = newMessages
                sessions[idx].preview = sessionPreview(newMessages)
                sessions[idx].title = sessionTitle(from: newMessages, currentTitle: sessions[idx].title)
                sessions[idx].updatedAt = Date()
            }
        }
    }
    
    private struct RewardAdModalView: View {
        let ad: AdInfo
        let onClose: () -> Void
        let onComplete: () -> Void
        
        @State private var progress: Double = 0
        @State private var canSkip = false
        @State private var phaseComplete = false
        private let duration: Double = 5.0
        
        var body: some View {
            ZStack {
                Color.black.opacity(0.94).ignoresSafeArea()
                VStack(spacing: 18) {
                    HStack {
                        Text("리워드 광고").font(.caption).foregroundStyle(.white.opacity(0.7))
                        Spacer()
                        if canSkip && !phaseComplete {
                            Button("건너뛰기", action: onClose)
                                .font(.caption2)
                                .foregroundStyle(.white.opacity(0.8))
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .overlay(Capsule().stroke(Color.white.opacity(0.4), lineWidth: 1))
                        }
                    }
                    
                    if !phaseComplete {
                        VStack(spacing: 14) {
                            VStack(spacing: 14) {
                                Text(ad.emoji).font(.system(size: 56))
                                Text(ad.brand).font(.title2.weight(.black)).foregroundStyle(ad.accent)
                                Text(ad.tagline).font(.subheadline).multilineTextAlignment(.center)
                                Button(ad.cta) { }
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 12)
                                    .background(ad.accent)
                                    .foregroundStyle(.white)
                                    .clipShape(Capsule())
                            }
                            .padding(24)
                            .frame(maxWidth: .infinity)
                            .background(ad.bg)
                            .clipShape(RoundedRectangle(cornerRadius: 24))
                            
                            ProgressView(value: progress)
                                .tint(.orange)
                            Text("\(Int((duration - progress * duration).rounded(.up)))초 남음")
                                .font(.caption)
                                .foregroundStyle(.white.opacity(0.7))
                        }
                    } else {
                        VStack(spacing: 14) {
                            Image(systemName: sfSymbol("gift.fill", fallback: "star.fill")).font(.system(size: 46)).foregroundStyle(.orange)
                            Text("시청 완료! 🎉").font(.title2.weight(.bold)).foregroundStyle(.white)
                            Text("리워드 포인트가 지급되었어요").font(.subheadline).foregroundStyle(.white.opacity(0.8))
                            Text("+30 P")
                                .font(.title.weight(.black))
                                .foregroundStyle(.orange)
                                .padding(.horizontal, 26)
                                .padding(.vertical, 8)
                                .background(.orange.opacity(0.2))
                                .clipShape(Capsule())
                            Button("AI 답변 확인하기 →", action: onComplete)
                                .buttonStyle(.borderedProminent)
                                .tint(Color(red: 0.36, green: 0.42, blue: 0.98))
                        }
                    }
                }
                .padding(24)
            }
            .task {
                let start = Date().timeIntervalSince1970
                do {
                    while !Task.isCancelled && !phaseComplete {
                        let elapsed = Date().timeIntervalSince1970 - start
                        progress = min(elapsed / duration, 1)
                        canSkip = elapsed >= 3
                        if elapsed >= duration {
                            phaseComplete = true
                            break
                        }
                        try await Task.sleep(for: .milliseconds(16))
                    }
                } catch {
                    return
                }
            }
        }
    }
    
    private struct MissionItem: Identifiable {
        let id: String
        let title: String
        let subtitle: String
        let reward: Int
        let icon: String
        let maxProgress: Int?
    }
    
    private struct RewardsView: View {
        @EnvironmentObject private var appState: AppState
        @State private var animateIn = false
        @State private var claimedIDs: Set<String> = []
        private let targetPoints = 4500
        private let missions = [
            MissionItem(id: "attendance", title: "출석 체크", subtitle: "오늘 하루 출석하고 포인트 받기", reward: 10, icon: "calendar", maxProgress: nil),
            MissionItem(id: "chat-10", title: "채팅 미션", subtitle: "오늘 AI와 10번 대화하기", reward: 50, icon: "message", maxProgress: 10),
            MissionItem(id: "watch-video", title: "동영상 시청", subtitle: "광고 보고 룰렛 돌리기", reward: 100, icon: "play.fill", maxProgress: nil)
        ]
        
        var body: some View {
            ScrollView {
                VStack(spacing: 14) {
                    VStack(alignment: .leading, spacing: 16) {
                        Text("리워드 미션")
                            .font(.system(size: 30, weight: .black))
                        VStack(alignment: .leading, spacing: 10) {
                            HStack {
                                Text("커피 교환까지")
                                    .font(.system(size: 15, weight: .bold))
                                Spacer()
                                Text("\(appState.points.formatted()) / \(targetPoints.formatted()) P")
                                    .font(.caption.weight(.semibold))
                            }
                            .foregroundStyle(.white)
                            
                            ProgressView(value: min(Double(appState.points) / Double(targetPoints), 1))
                                .tint(.white)
                            
                            let remain = max(targetPoints - appState.points, 0)
                            Text("\(remain.formatted())P 더 모으면 스타벅스 아메리카노!")
                                .font(.caption)
                                .foregroundStyle(.white.opacity(0.9))
                        }
                        .padding(18)
                        .background(
                            LinearGradient(colors: [Color(red: 0.36, green: 0.42, blue: 0.98), Color(red: 0.29, green: 0.35, blue: 0.91)], startPoint: .leading, endPoint: .trailing)
                        )
                        .clipShape(RoundedRectangle(cornerRadius: 20))
                    }
                    .padding(20)
                    .background(.white)
                    
                    ForEach(missions) { mission in
                        let isClaimed = claimedIDs.contains(mission.id)
                        let progress = mission.maxProgress.map { min(appState.messageCount, $0) } ?? 0
                        let canClaim = !isClaimed && (mission.maxProgress == nil || progress >= (mission.maxProgress ?? 0))
                        
                        HStack(alignment: .top, spacing: 14) {
                            Image(systemName: sfSymbol(mission.icon, fallback: "star.fill"))
                                .foregroundStyle(Color(red: 0.36, green: 0.42, blue: 0.98))
                                .frame(width: 44, height: 44)
                                .background(Color(red: 0.96, green: 0.97, blue: 0.98))
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                            
                            VStack(alignment: .leading, spacing: 8) {
                                Text(mission.title).font(.headline.weight(.bold))
                                Text(mission.subtitle).font(.caption).foregroundStyle(.secondary)
                                if let max = mission.maxProgress {
                                    ProgressView(value: Double(progress) / Double(max))
                                        .tint(Color(red: 0.36, green: 0.42, blue: 0.98))
                                    Text("진행도 \(progress)/\(max)")
                                        .font(.caption2).foregroundStyle(.secondary)
                                }
                                
                                HStack {
                                    Text("+\(mission.reward)P")
                                        .font(.system(size: 18, weight: .black))
                                        .foregroundStyle(.orange)
                                    Spacer()
                                    Button(isClaimed ? "완료됨" : "받기") {
                                        if canClaim {
                                            appState.addPoints(mission.reward)
                                            claimedIDs.insert(mission.id)
                                        }
                                    }
                                    .buttonStyle(.borderedProminent)
                                    .tint(isClaimed ? .gray : .orange)
                                    .disabled(!canClaim)
                                }
                            }
                        }
                        .padding(18)
                        .background(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 20))
                        .padding(.horizontal, 20)
                    }
                }
                .padding(.bottom, 16)
            }
            .background(Color(red: 0.96, green: 0.97, blue: 0.98))
            .opacity(animateIn ? 1 : 0)
            .offset(y: animateIn ? 0 : 14)
            .animation(.easeOut(duration: 0.34), value: animateIn)
            .onAppear {
                animateIn = false
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.03) {
                    withAnimation(.easeOut(duration: 0.34)) {
                        animateIn = true
                    }
                }
            }
            .onDisappear {
                animateIn = false
            }
        }
    }
    
    private enum ShopCategory: String, CaseIterable {
        case all = "전체"
        case cafe = "카페"
        case cvs = "편의점"
        case food = "외식"
        case voucher = "상품권"
    }
    
    private struct ShopItem: Identifiable {
        let id = UUID()
        let brand: String
        let title: String
        let price: Int
        let emoji: String
        let category: ShopCategory
    }
    
    private struct ShopView: View {
        @EnvironmentObject private var appState: AppState
        @State private var animateIn = false
        @State private var selectedCategory: ShopCategory = .all
        @State private var query = ""
        @State private var notice = ""
        
        private let items = [
            ShopItem(brand: "스타벅스", title: "아메리카노 Tall", price: 4500, emoji: "☕", category: .cafe),
            ShopItem(brand: "스타벅스", title: "카페라떼 Grande", price: 5500, emoji: "☕", category: .cafe),
            ShopItem(brand: "CU", title: "1+1 교환권", price: 3000, emoji: "🏪", category: .cvs),
            ShopItem(brand: "배스킨라빈스", title: "해피콘 3,000원권", price: 3000, emoji: "🍦", category: .cafe),
            ShopItem(brand: "BBQ", title: "치킨 할인권 5,000원", price: 5000, emoji: "🍗", category: .food),
            ShopItem(brand: "문화상품권", title: "문화상품권 5,000원", price: 5000, emoji: "🎁", category: .voucher)
        ]
        
        private var filtered: [ShopItem] {
            items.filter {
                (selectedCategory == .all || $0.category == selectedCategory) &&
                (query.isEmpty || $0.title.localizedCaseInsensitiveContains(query) || $0.brand.localizedCaseInsensitiveContains(query))
            }
        }
        
        private let columns = [GridItem(.flexible(), spacing: 14), GridItem(.flexible(), spacing: 14)]
        
        var body: some View {
            VStack(spacing: 0) {
                VStack(alignment: .leading, spacing: 14) {
                    HStack {
                        Text("포인트 상점")
                            .font(.system(size: 30, weight: .black))
                        Spacer()
                        Text("🪙 \(appState.points.formatted()) P")
                            .font(.subheadline.weight(.black))
                            .padding(.horizontal, 14)
                            .padding(.vertical, 8)
                            .background(Color(red: 0.36, green: 0.42, blue: 0.98))
                            .foregroundStyle(.white)
                            .clipShape(Capsule())
                    }
                    TextField("상품을 검색하세요", text: $query)
                        .padding(10)
                        .background(Color(red: 0.95, green: 0.96, blue: 0.98))
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .padding(20)
                .background(.white)
                
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(ShopCategory.allCases, id: \.self) { category in
                            Button(category.rawValue) { selectedCategory = category }
                                .font(.caption.weight(.bold))
                                .padding(.horizontal, 14)
                                .padding(.vertical, 8)
                                .background(selectedCategory == category ? Color(red: 0.36, green: 0.42, blue: 0.98) : Color(red: 0.94, green: 0.95, blue: 0.97))
                                .foregroundStyle(selectedCategory == category ? .white : .primary)
                                .clipShape(Capsule())
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 12)
                }
                .background(.white)
                
                ScrollView {
                    LazyVGrid(columns: columns, spacing: 14) {
                        ForEach(filtered) { item in
                            VStack(alignment: .leading, spacing: 10) {
                                ZStack {
                                    LinearGradient(colors: [Color(red: 0.95, green: 0.96, blue: 0.98), Color(red: 0.90, green: 0.92, blue: 0.96)], startPoint: .topLeading, endPoint: .bottomTrailing)
                                    Text(item.emoji).font(.system(size: 44))
                                }
                                .frame(height: 110)
                                .clipShape(RoundedRectangle(cornerRadius: 16))
                                
                                Text(item.brand).font(.caption).foregroundStyle(.secondary)
                                Text(item.title).font(.subheadline.weight(.bold)).lineLimit(2)
                                HStack {
                                    Text("\(item.price.formatted())P")
                                        .font(.subheadline.weight(.black))
                                        .foregroundStyle(.orange)
                                    Spacer()
                                    Button("구매") {
                                        notice = appState.spendPoints(item.price) ? "구매 완료: \(item.title)" : "포인트가 부족합니다"
                                    }
                                    .font(.caption.weight(.bold))
                                    .buttonStyle(.borderedProminent)
                                    .tint(Color(red: 0.36, green: 0.42, blue: 0.98))
                                }
                            }
                            .padding(12)
                            .background(.white)
                            .clipShape(RoundedRectangle(cornerRadius: 20))
                        }
                    }
                    .padding(20)
                }
                .background(Color(red: 0.96, green: 0.97, blue: 0.98))
            }
            .safeAreaInset(edge: .bottom) {
                if !notice.isEmpty {
                    Text(notice)
                        .font(.footnote)
                        .padding(10)
                        .frame(maxWidth: .infinity)
                        .background(.thinMaterial)
                }
            }
            .opacity(animateIn ? 1 : 0)
            .offset(y: animateIn ? 0 : 14)
            .animation(.easeOut(duration: 0.34), value: animateIn)
            .onAppear {
                animateIn = false
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.03) {
                    withAnimation(.easeOut(duration: 0.34)) {
                        animateIn = true
                    }
                }
            }
            .onDisappear {
                animateIn = false
            }
        }
    }
    
    private struct MyPageMenuItem: Identifiable {
        let id = UUID()
        let icon: String
        let label: String
        let badge: String?
    }
    
    private struct MyPageView: View {
        @EnvironmentObject private var appState: AppState
        @State private var animateIn = false
        private let menuItems = [
            MyPageMenuItem(icon: "gift", label: "내 기프티콘 보관함", badge: "2"),
            MyPageMenuItem(icon: "clock.arrow.circlepath", label: "포인트 적립/사용 내역", badge: nil),
            MyPageMenuItem(icon: "bell", label: "공지사항", badge: "N"),
            MyPageMenuItem(icon: "questionmark.circle", label: "고객센터", badge: nil),
            MyPageMenuItem(icon: "gearshape", label: "설정", badge: nil)
        ]
        
        var body: some View {
            ScrollView {
                VStack(spacing: 14) {
                    VStack(alignment: .leading, spacing: 18) {
                        HStack(spacing: 16) {
                            Text("👤")
                                .font(.system(size: 34))
                                .frame(width: 76, height: 76)
                                .background(.white.opacity(0.2))
                                .clipShape(Circle())
                            VStack(alignment: .leading, spacing: 2) {
                                Text("홍길동님").font(.title3.weight(.black)).foregroundStyle(.white)
                                Text("gildong@kakao.com").font(.subheadline).foregroundStyle(.white.opacity(0.85))
                            }
                        }
                        
                        VStack(alignment: .leading, spacing: 6) {
                            Text("보유 포인트").font(.caption).foregroundStyle(.white.opacity(0.9))
                            HStack(alignment: .lastTextBaseline, spacing: 4) {
                                Text(appState.points.formatted()).font(.system(size: 34, weight: .black)).foregroundStyle(.white)
                                Text("P").font(.title3.weight(.bold)).foregroundStyle(.white)
                            }
                            Text("누적 획득 포인트: 15,750 P")
                                .font(.caption2)
                                .foregroundStyle(.white.opacity(0.7))
                        }
                        .padding(16)
                        .background(.white.opacity(0.16))
                        .clipShape(RoundedRectangle(cornerRadius: 18))
                    }
                    .padding(24)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(
                        LinearGradient(colors: [Color(red: 0.36, green: 0.42, blue: 0.98), Color(red: 0.29, green: 0.35, blue: 0.91)], startPoint: .topLeading, endPoint: .bottomTrailing)
                    )
                    .opacity(animateIn ? 1 : 0)
                    .offset(y: animateIn ? 0 : 18)
                    .animation(.easeOut(duration: 0.26), value: animateIn)
                    
                    VStack(spacing: 10) {
                        ForEach(Array(menuItems.enumerated()), id: \.element.id) { index, item in
                            HStack {
                                Image(systemName: sfSymbol(item.icon, fallback: "circle.fill"))
                                    .frame(width: 36, height: 36)
                                    .background(Color(red: 0.96, green: 0.97, blue: 0.98))
                                    .clipShape(RoundedRectangle(cornerRadius: 10))
                                    .foregroundStyle(Color(red: 0.36, green: 0.42, blue: 0.98))
                                Text(item.label)
                                    .font(.subheadline.weight(.semibold))
                                Spacer()
                                if let badge = item.badge {
                                    Text(badge)
                                        .font(.caption2.weight(.bold))
                                        .foregroundStyle(.white)
                                        .padding(.horizontal, 8)
                                        .padding(.vertical, 3)
                                        .background(.orange)
                                        .clipShape(Capsule())
                                }
                                Image(systemName: sfSymbol("chevron.right", fallback: "arrow.right"))
                                    .foregroundStyle(.secondary)
                            }
                            .padding(14)
                            .background(.white)
                            .clipShape(RoundedRectangle(cornerRadius: 16))
                            .opacity(animateIn ? 1 : 0)
                            .offset(y: animateIn ? 0 : 16)
                            .animation(.easeOut(duration: 0.24).delay(0.04 * Double(index + 1)), value: animateIn)
                        }
                    }
                    .padding(.horizontal, 20)
                    
                    HStack(spacing: 10) {
                        statCard("7", "연속 출석", color: Color(red: 0.36, green: 0.42, blue: 0.98))
                        statCard("\(appState.messageCount)", "총 대화수", color: .orange)
                        statCard("3", "교환 상품", color: .green)
                    }
                    .padding(.horizontal, 20)
                    .padding(.bottom, 18)
                    .opacity(animateIn ? 1 : 0)
                    .offset(y: animateIn ? 0 : 16)
                    .animation(.easeOut(duration: 0.24).delay(0.28), value: animateIn)
                }
            }
            .background(Color(red: 0.96, green: 0.97, blue: 0.98))
            .onAppear {
                animateIn = false
                DispatchQueue.main.async {
                    animateIn = true
                }
            }
            .onDisappear {
                animateIn = false
            }
        }
        
        private func statCard(_ number: String, _ label: String, color: Color) -> some View {
            VStack(spacing: 4) {
                Text(number).font(.title2.weight(.black)).foregroundStyle(color)
                Text(label).font(.caption).foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(.white)
            .clipShape(RoundedRectangle(cornerRadius: 14))
        }
    }
    
    #Preview {
        ContentView()
    }
}
