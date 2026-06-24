import SwiftUI

/// 점검 모드 / 강제 업데이트 전체 차단 화면.
/// Remote Config 긴급 키로 `gateState`가 `.none`이 아닐 때 루트에서 표시된다.
/// Android `AppGateScreen.kt`와 대응.
struct AppGateView: View {
    let state: AppGateState
    /// 강제 업데이트 시 App Store로 이동(점검 모드에서는 버튼이 없다).
    var onUpdate: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: iconName)
                .font(.system(size: 56))
                .foregroundColor(.accentColor)
            Text(title)
                .font(.title2).bold()
            Text(message)
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
            if showButton {
                Button(action: onUpdate) {
                    Text("업데이트 하러 가기").bold()
                }
                .padding(.top, 8)
            }
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(.systemBackground))
    }

    private var iconName: String {
        switch state {
        case .maintenance: return "wrench.and.screwdriver.fill"
        case .forceUpdate: return "arrow.down.circle.fill"
        case .none: return ""
        }
    }

    private var title: String {
        switch state {
        case .maintenance: return "서비스 점검 중이에요"
        case .forceUpdate: return "업데이트가 필요해요"
        case .none: return ""
        }
    }

    private var showButton: Bool {
        if case .forceUpdate = state { return true }
        return false
    }

    private var message: String {
        switch state {
        case let .maintenance(message):
            return message.isEmpty
                ? "더 나은 서비스를 위해 점검 중입니다.\n잠시 후 다시 이용해주세요."
                : message
        case let .forceUpdate(message):
            return message.isEmpty
                ? "최신 버전에서 더 안정적으로 이용할 수 있어요.\n스토어에서 업데이트해주세요."
                : message
        case .none:
            return ""
        }
    }
}
