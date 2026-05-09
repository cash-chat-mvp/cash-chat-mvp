import Foundation

enum AppConfig {
    static let googleIOSClientId: String = required(Secrets.googleIOSClientId, key: "googleIOSClientId")
    static let googleWebClientId: String = Secrets.googleWebClientId
    static let apiBaseUrl: String = Secrets.apiBaseUrl

    private static func required(_ value: String, key: String) -> String {
        guard !value.isEmpty, !value.hasPrefix("YOUR_") else {
            preconditionFailure(
                "[\(key)] 값이 설정되지 않았습니다.\n" +
                "Secrets.swift.example을 Secrets.swift로 복사하고 실제 값을 입력하세요."
            )
        }
        return value
    }
}
