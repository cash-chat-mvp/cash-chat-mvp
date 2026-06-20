# iOS Apple 로그인 실제 연동 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** iOS 앱의 placeholder Apple 로그인을 네이티브 `ASAuthorizationController` 기반 실제 로그인으로 교체하고, 완성된 백엔드 `POST /api/auth/callback/apple`와 KMM shared 레이어를 통해 연동한다.

**Architecture:** Google 로그인과 동일한 3-레이어 패턴. ① KMM shared(`AuthApiService`)에 Apple API 계약 추가 → ② iOS에 `ASAuthorizationController`를 async/await로 래핑한 `AppleSignInCoordinator` 신규 작성 → ③ `AppState.loginWithApple()` placeholder를 실제 흐름으로 교체. 백엔드가 `authorizationCode`를 서버에서 재교환·검증하므로 클라이언트 nonce는 불필요.

**Tech Stack:** Swift / SwiftUI / AuthenticationServices(ASAuthorization), Kotlin Multiplatform / Ktor / kotlinx.serialization, Xcode 16(동기화 폴더 그룹).

**검증 전략:** 이 레이어(`AuthApiService`, ASAuthorization UI)는 코드베이스에 단위 테스트 인프라가 없고 네이티브 SDK는 실기기 의존이므로, 각 태스크 검증은 **컴파일/빌드 성공 + 수동 통합 테스트**를 기준으로 한다(기존 `loginWithGoogle` 패턴과 동일).

**중요 사전조건/리스크(코드 범위 밖, 명시만):**
- 인프라가 `APPLE_*` 환경변수 5종을 배포에 반영해야 BE `/callback/apple`가 동작한다(Confluence CC-239 §7).
- 네이티브 iOS 플로우의 id_token `aud`는 앱 **번들 ID**(`com.nomadlab.cashchat`)다. BE `APPLE_CLIENT_ID`(Services ID, 예: `com.nomadclub.cashchat`)와 다르면 Apple token 교환이 `invalid_client`(502)로 실패할 수 있다 — BE/인프라 설정에서 정렬 필요. iOS 코드는 client_id를 하드코딩하지 않으므로 코드 변경 없음.

---

## File Structure

| 파일 | 변경 | 책임 |
| --- | --- | --- |
| `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/auth/model/AppleOAuthCallbackRequest.kt` | 생성 | Apple 콜백 요청 DTO (`@Serializable`) |
| `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/auth/AuthApiService.kt` | 수정 | `loginWithApple()` 메서드 추가 |
| `apps/frontend/CashChatIOS/CashChatIOS/AppleSignInCoordinator.swift` | 생성 | ASAuthorization async 래핑 + credential 추출 |
| `apps/frontend/CashChatIOS/CashChatIOS/ContentView.swift` | 수정 | `AppState.loginWithApple()` 교체, Apple 버튼 연동 |
| `apps/frontend/CashChatIOS/CashChatIOS/CashChatIOS.entitlements` | 생성 | Sign in with Apple capability |
| `apps/frontend/CashChatIOS/CashChatIOS.xcodeproj/project.pbxproj` | 수정 | `CODE_SIGN_ENTITLEMENTS` 연결 (Debug/Release) |

> 동기화 폴더 그룹이므로 신규 `.swift`/`.entitlements` 파일은 폴더에 두면 타겟에 자동 포함된다. pbxproj 수정은 entitlements 연결 한 곳뿐이다.

---

## Task 1: KMM — Apple 콜백 요청 DTO 추가

**Files:**
- Create: `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/auth/model/AppleOAuthCallbackRequest.kt`

- [ ] **Step 1: DTO 파일 생성**

`GoogleOAuthCallbackRequest`와 동일 패턴. BE `AppleOAuthCallbackRequest`(authorizationCode 필수, 나머지 nullable)와 1:1 대응.

```kotlin
package com.nomadclub.cashchat.shared.auth.model

import kotlinx.serialization.Serializable

/**
 * POST /api/auth/callback/apple 요청 바디.
 *
 * @param authorizationCode Apple ASAuthorization에서 받은 authorization code (필수)
 * @param identityToken     Apple id_token (선택 — 현재 BE 검증 미사용, 계약 필드)
 * @param fullName          사용자 이름 (Apple 최초 인증 시에만 전달, 이후 null)
 * @param deviceToken       게스트 → 회원 승격 시 기존 deviceToken 연결용 (선택)
 */
@Serializable
data class AppleOAuthCallbackRequest(
    val authorizationCode: String,
    val identityToken: String? = null,
    val fullName: String? = null,
    val deviceToken: String? = null
)
```

- [ ] **Step 2: 컴파일 검증**

Run: `cd apps/frontend && ./gradlew :shared:compileKotlinMetadata`
Expected: BUILD SUCCESSFUL (신규 직렬화 모델 컴파일 통과)

- [ ] **Step 3: Commit**

```bash
git add apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/auth/model/AppleOAuthCallbackRequest.kt
git commit -m "feat(shared): Apple 콜백 요청 DTO 추가"
```

---

## Task 2: KMM — AuthApiService.loginWithApple() 추가

**Files:**
- Modify: `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/auth/AuthApiService.kt`

- [ ] **Step 1: import 추가**

파일 상단 import 블록의 `import com.nomadclub.cashchat.shared.auth.model.AuthResponse` 아래에 추가:

```kotlin
import com.nomadclub.cashchat.shared.auth.model.AppleOAuthCallbackRequest
```

- [ ] **Step 2: loginWithApple 메서드 추가**

`loginWithGoogle` 메서드 바로 아래에 추가(동일 Ktor 패턴):

```kotlin
    /**
     * Apple OAuth 로그인 (Member 전환).
     * iOS ASAuthorization에서 받은 authorizationCode를 BE로 전달하면
     * BE가 Apple token endpoint와 직접 교환하여 id_token을 검증합니다.
     *
     * POST /api/auth/callback/apple
     */
    suspend fun loginWithApple(
        authorizationCode: String,
        identityToken: String?,
        fullName: String?,
        deviceToken: String
    ): AuthResponse {
        return httpClient.post("$baseUrl/api/auth/callback/apple") {
            contentType(ContentType.Application.Json)
            setBody(
                AppleOAuthCallbackRequest(
                    authorizationCode = authorizationCode,
                    identityToken = identityToken,
                    fullName = fullName,
                    deviceToken = deviceToken
                )
            )
        }.body()
    }
```

- [ ] **Step 3: shared 프레임워크 빌드 검증**

Run: `cd apps/frontend && ./gradlew :shared:assembleDebug`
Expected: BUILD SUCCESSFUL — iOS에서 사용할 `loginWithApple`이 공개 API에 포함됨

- [ ] **Step 4: Commit**

```bash
git add apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/auth/AuthApiService.kt
git commit -m "feat(shared): AuthApiService에 loginWithApple 추가"
```

---

## Task 3: iOS — AppleSignInCoordinator 작성

**Files:**
- Create: `apps/frontend/CashChatIOS/CashChatIOS/AppleSignInCoordinator.swift`

- [ ] **Step 1: Coordinator 파일 생성**

ASAuthorization delegate 콜백을 `withCheckedThrowingContinuation`으로 async/await에 브리지한다. delegate 유지를 위해 controller를 인스턴스 프로퍼티로 보관하고, continuation 중복 resume을 가드한다.

```swift
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
}

/// ASAuthorizationController를 async/await로 래핑한다.
/// delegate/presentation context 유지를 위해 AppState가 strong reference로 보유해야 한다.
@MainActor
final class AppleSignInCoordinator: NSObject {
    private var continuation: CheckedContinuation<AppleCredential, Error>?

    func signIn() async throws -> AppleCredential {
        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.fullName, .email]

        return try await withCheckedThrowingContinuation { continuation in
            self.continuation = continuation
            let controller = ASAuthorizationController(authorizationRequests: [request])
            controller.delegate = self
            controller.presentationContextProvider = self
            controller.performRequests()
        }
    }

    private func resume(returning credential: AppleCredential) {
        continuation?.resume(returning: credential)
        continuation = nil
    }

    private func resume(throwing error: Error) {
        continuation?.resume(throwing: error)
        continuation = nil
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
```

- [ ] **Step 2: 컴파일 검증는 Task 5에서 일괄 수행**

이 파일 단독 컴파일은 불가(앱 타겟 전체 빌드 필요). 작성만 하고 빌드는 Task 5에서 ContentView 교체 후 함께 검증한다.

- [ ] **Step 3: Commit**

```bash
git add apps/frontend/CashChatIOS/CashChatIOS/AppleSignInCoordinator.swift
git commit -m "feat(ios): ASAuthorization async 래핑 AppleSignInCoordinator 추가"
```

---

## Task 4: iOS — Sign in with Apple capability(entitlements) 추가

**Files:**
- Create: `apps/frontend/CashChatIOS/CashChatIOS/CashChatIOS.entitlements`
- Modify: `apps/frontend/CashChatIOS/CashChatIOS.xcodeproj/project.pbxproj`

- [ ] **Step 1: entitlements 파일 생성**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>com.apple.developer.applesignin</key>
	<array>
		<string>Default</string>
	</array>
</dict>
</plist>
```

- [ ] **Step 2: pbxproj 앱 타겟 Debug 빌드 설정에 entitlements 연결**

`apps/frontend/CashChatIOS/CashChatIOS.xcodeproj/project.pbxproj`의 **앱 타겟 Debug** buildSettings 블록(`CODE_SIGN_IDENTITY = "Apple Development";`와 `INFOPLIST_FILE = Info.plist;`를 포함하고 `PRODUCT_BUNDLE_IDENTIFIER = com.nomadlab.cashchat;`인 블록, 약 427번째 줄)에서 `CODE_SIGN_STYLE = Automatic;` 줄 바로 아래에 추가:

```
				CODE_SIGN_ENTITLEMENTS = CashChatIOS/CashChatIOS.entitlements;
```

- [ ] **Step 3: pbxproj 앱 타겟 Release 빌드 설정에도 동일 추가**

같은 파일의 **앱 타겟 Release** buildSettings 블록(약 459번째 줄, 역시 `PRODUCT_BUNDLE_IDENTIFIER = com.nomadlab.cashchat;` 포함)에서 `CODE_SIGN_STYLE = Automatic;` 줄 바로 아래에 동일하게 추가:

```
				CODE_SIGN_ENTITLEMENTS = CashChatIOS/CashChatIOS.entitlements;
```

> 주의: `CashChatIOSTests`/`CashChatIOSUITests` 타겟 블록(BUNDLE_IDENTIFIER에 `Tests`/`UITests` 포함)에는 추가하지 않는다. 앱 타겟 2개(Debug/Release)에만 추가한다.

- [ ] **Step 4: pbxproj 유효성 검증**

Run: `cd apps/frontend/CashChatIOS && plutil -lint CashChatIOS.xcodeproj/project.pbxproj && plutil -lint CashChatIOS/CashChatIOS.entitlements`
Expected: 두 파일 모두 `OK` — pbxproj/plist 문법 깨지지 않음

- [ ] **Step 5: Commit**

```bash
git add apps/frontend/CashChatIOS/CashChatIOS/CashChatIOS.entitlements apps/frontend/CashChatIOS/CashChatIOS.xcodeproj/project.pbxproj
git commit -m "feat(ios): Sign in with Apple capability(entitlements) 추가"
```

---

## Task 5: iOS — AppState.loginWithApple() 교체 및 버튼 연동

**Files:**
- Modify: `apps/frontend/CashChatIOS/CashChatIOS/ContentView.swift`

- [ ] **Step 1: AppState에 coordinator 프로퍼티 추가**

`ContentView.swift`의 `AppState` 클래스에서 `private let defaults = UserDefaults.standard` 줄 아래에 추가:

```swift
    private let appleSignInCoordinator = AppleSignInCoordinator()
```

- [ ] **Step 2: placeholder loginWithApple() 교체**

기존 메서드(약 83~86줄)를 삭제:

```swift
    // Apple 로그인 — API 준비 전까지 placeholder
    func loginWithApple() {
        errorMessage = "Apple 로그인은 준비 중입니다."
    }
```

다음으로 교체:

```swift
    // Apple 로그인 — 네이티브 ASAuthorization + BE /callback/apple 연동
    func loginWithApple() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            let credential = try await appleSignInCoordinator.signIn()
            let deviceToken = getOrCreateDeviceToken()
            let response = try await apiService.loginWithApple(
                authorizationCode: credential.authorizationCode,
                identityToken: credential.identityToken,
                fullName: credential.fullName,
                deviceToken: deviceToken
            )
            KeychainHelper.set(response.accessToken, forKey: Keys.accessToken)
            KeychainHelper.set(response.role, forKey: Keys.role)
            if let refreshToken = response.refreshToken {
                KeychainHelper.set(refreshToken, forKey: Keys.refreshToken)
            }
            isAuthenticated = true
        } catch AppleSignInError.canceled {
            // 사용자 취소 — 토스트 없이 무시
        } catch {
            errorMessage = "Apple 로그인에 실패했습니다. 다시 시도해주세요."
        }
    }
```

- [ ] **Step 3: OnboardingView의 Apple 버튼을 async 호출로 변경**

`OnboardingView`의 Apple 로그인 Button(약 234줄) 액션을 변경. 기존:

```swift
                    Button {
                        appState.loginWithApple()
                    } label: {
```

다음으로 변경:

```swift
                    Button {
                        Task { await appState.loginWithApple() }
                    } label: {
```

- [ ] **Step 4: shared 프레임워크 임베드 후 앱 빌드 검증**

Run:
```bash
cd apps/frontend
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
./gradlew :shared:embedAndSignAppleFrameworkForXcode
cd CashChatIOS
xcodebuild -project CashChatIOS.xcodeproj -scheme CashChatIOS -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' build CODE_SIGNING_ALLOWED=NO
```
Expected: `** BUILD SUCCEEDED **` — `AppleSignInCoordinator`, `loginWithApple(async)`, `apiService.loginWithApple` 모두 컴파일 통과

> `xcodebuild`/시뮬레이터가 없는 환경이면 이 스텝은 수동 검증으로 표시하고, 최소한 `./gradlew :shared:assembleDebug` 성공까지만 자동 확인한다.

- [ ] **Step 5: Commit**

```bash
git add apps/frontend/CashChatIOS/CashChatIOS/ContentView.swift
git commit -m "feat(ios): Apple 로그인 placeholder를 실제 ASAuthorization 연동으로 교체"
```

---

## Task 6: 통합 검증 (수동)

**Files:** 없음 (실행/관찰만)

- [ ] **Step 1: 빌드 전체 그린 확인**

Run:
```bash
cd apps/frontend && ./gradlew :shared:assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 실기기 수동 통합 테스트 체크리스트 (Apple Developer 계정 + 실기기 필요)**

다음을 수동으로 확인하고 결과를 기록한다(자동화 불가 — 실제 Apple ID 필요):
- [ ] "Apple로 로그인" 탭 → Apple 시트 표시 → 동의 → `isAuthenticated = true`로 메인 탭 진입
- [ ] **최초 가입**: `fullName` 포함되어 BE에 사용자 이름 저장 확인
- [ ] **재로그인**: `fullName` nil이어도 정상 로그인(BE가 providerId로 기존 사용자 조회)
- [ ] **취소**: 시트에서 취소 시 토스트 없이 로그인 화면 유지
- [ ] **게스트 승격**: 게스트로 시작 후 Apple 로그인 시 동일 계정 승격(role=MEMBER)
- [ ] **회귀**: Google 로그인 / 게스트 로그인 정상 동작

- [ ] **Step 3: 사전조건 재확인 (인프라)**

- [ ] BE 배포 환경에 `APPLE_*` 환경변수 5종 반영 여부 확인 (Confluence CC-239 §7) — 미반영 시 `/callback/apple` 502
- [ ] `APPLE_CLIENT_ID`(Services ID)와 앱 번들 ID 정렬로 `invalid_client` 미발생 확인

---

## Self-Review

**Spec coverage:**
- KMM `AppleOAuthCallbackRequest` → Task 1 ✅
- KMM `AuthApiService.loginWithApple` → Task 2 ✅
- iOS `AppleSignInCoordinator`(ASAuthorization async, Data→UTF8, fullName 조합, canceled 매핑) → Task 3 ✅
- iOS `AppState.loginWithApple` 교체 + Keychain 저장 + 버튼 연동 → Task 5 ✅
- Sign in with Apple capability → Task 4 ✅
- 에러 처리(취소 무시/토스트) → Task 3 + Task 5 ✅
- 검증(KMM assembleDebug, xcodebuild, 수동 통합) → Task 2/5/6 ✅
- 범위 밖(인프라/Android/BE/UI) → 명시됨 ✅

**Type consistency:** `AppleCredential`/`AppleSignInError`(Task 3) → Task 5에서 동일 이름 사용. `loginWithApple(authorizationCode, identityToken, fullName, deviceToken)` 시그니처가 Task 2(KMM)와 Task 5(Swift 호출부)에서 일치. `deviceToken`은 양쪽 모두 non-null String.

**Placeholder scan:** 모든 코드 스텝에 실제 코드 포함, TBD/TODO 없음.
