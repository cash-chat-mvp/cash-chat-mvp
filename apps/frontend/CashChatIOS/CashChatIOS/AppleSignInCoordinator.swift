import AuthenticationServices
import UIKit

/// Apple Sign In 결과 — BE /callback/apple로 전달할 자격 정보.
struct AppleCredential {
    let authorizationCode: String
    let identityToken: String?
    /// Apple은 최초 인증 시에만 이름을 제공한다. 이후 로그인부터는 nil.
    let fullName: String?
}

enum AppleSignInError: Error {
    /// 사용자가 시트를 취소함 — 상위에서 토스트 없이 무시.
    case canceled
    /// authorizationCode를 추출하지 못함.
    case missingAuthorizationCode
    /// 이미 진행 중인 요청이 있음 (중복 호출) — 상위에서 무시.
    case alreadyInProgress
}

/// ASAuthorizationController를 async/await로 래핑한다.
/// delegate/presentation context 유지를 위해 AppState가 strong reference로 보유해야 한다.
@MainActor
final class AppleSignInCoordinator: NSObject {
    private var continuation: CheckedContinuation<AppleCredential, Error>?
    /// ASAuthorizationController는 시스템이 strong reference로 잡아주지 않으므로,
    /// 인증 흐름이 끝날 때까지(resume 호출 시점) 직접 보유해야 콜백이 정상 호출된다.
    private var currentController: ASAuthorizationController?

    func signIn() async throws -> AppleCredential {
        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.fullName, .email]

        return try await withCheckedThrowingContinuation { continuation in
            // 재진입 가드: 진행 중인 요청이 있으면 기존 continuation을 덮어쓰지 않고 즉시 거부.
            guard self.continuation == nil else {
                continuation.resume(throwing: AppleSignInError.alreadyInProgress)
                return
            }
            self.continuation = continuation
            let controller = ASAuthorizationController(authorizationRequests: [request])
            controller.delegate = self
            controller.presentationContextProvider = self
            self.currentController = controller
            controller.performRequests()
        }
    }

    private func resume(returning credential: AppleCredential) {
        continuation?.resume(returning: credential)
        continuation = nil
        currentController = nil
    }

    private func resume(throwing error: Error) {
        continuation?.resume(throwing: error)
        continuation = nil
        currentController = nil
    }
}

extension AppleSignInCoordinator: ASAuthorizationControllerDelegate {
    func authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithAuthorization authorization: ASAuthorization
    ) {
        guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
              let codeData = credential.authorizationCode,
              let authorizationCode = String(data: codeData, encoding: .utf8) else {
            resume(throwing: AppleSignInError.missingAuthorizationCode)
            return
        }

        let identityToken = credential.identityToken
            .flatMap { String(data: $0, encoding: .utf8) }

        var fullName: String? = nil
        if let nameComponents = credential.fullName {
            let formatter = PersonNameComponentsFormatter()
            let formatted = formatter.string(from: nameComponents)
                .trimmingCharacters(in: .whitespacesAndNewlines)
            fullName = formatted.isEmpty ? nil : formatted
        }

        resume(returning: AppleCredential(
            authorizationCode: authorizationCode,
            identityToken: identityToken,
            fullName: fullName
        ))
    }

    func authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithError error: Error
    ) {
        if let authError = error as? ASAuthorizationError, authError.code == .canceled {
            resume(throwing: AppleSignInError.canceled)
        } else {
            resume(throwing: error)
        }
    }
}

extension AppleSignInCoordinator: ASAuthorizationControllerPresentationContextProviding {
    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first
        return scene?.windows.first { $0.isKeyWindow }
            ?? scene?.windows.first
            ?? ASPresentationAnchor()
    }
}
