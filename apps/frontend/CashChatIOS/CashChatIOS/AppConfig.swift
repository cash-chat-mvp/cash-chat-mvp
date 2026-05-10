import Foundation

enum AppConfig {
    static let googleIOSClientId: String = required(Secrets.googleIOSClientId, key: "googleIOSClientId")
    static let googleWebClientId: String = required(Secrets.googleWebClientId, key: "googleWebClientId")
    static let apiBaseUrl: String = required(Secrets.apiBaseUrl, key: "apiBaseUrl")

    private static func required(_ value: String, key: String) -> String {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, !trimmed.hasPrefix("YOUR_") else {
            preconditionFailure(
                "[\(key)] 값이 설정되지 않았습니다.\n" +
                "Secrets.swift를 생성하고 실제 값을 입력하세요."
            )
        }
        return trimmed
    }
}
