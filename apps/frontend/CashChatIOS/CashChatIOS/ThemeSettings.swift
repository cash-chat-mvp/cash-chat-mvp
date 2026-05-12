import Combine
import SwiftUI

enum ThemeMode: String {
    case system
    case light
    case dark

    var colorScheme: ColorScheme? {
        switch self {
        case .system: return nil
        case .light:  return .light
        case .dark:   return .dark
        }
    }

    var label: String {
        switch self {
        case .system: return "시스템 동기화"
        case .light:  return "라이트"
        case .dark:   return "다크"
        }
    }

    var description: String {
        switch self {
        case .system: return "기기 설정에 따라 자동으로 변경"
        case .light:  return "항상 밝은 테마 사용"
        case .dark:   return "항상 어두운 테마 사용"
        }
    }

    var systemImageName: String {
        switch self {
        case .system: return "circle.lefthalf.filled"
        case .light:  return "sun.max.fill"
        case .dark:   return "moon.fill"
        }
    }
}

final class ThemeSettings: ObservableObject {
    private static let key = "app_theme_mode"

    @Published var themeMode: ThemeMode {
        didSet {
            UserDefaults.standard.set(themeMode.rawValue, forKey: Self.key)
        }
    }

    init() {
        let stored = UserDefaults.standard.string(forKey: Self.key) ?? ""
        self.themeMode = ThemeMode(rawValue: stored) ?? .system
    }
}
