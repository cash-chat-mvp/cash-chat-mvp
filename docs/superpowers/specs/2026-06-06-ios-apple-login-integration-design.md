# iOS Apple 로그인 실제 연동 설계

## 목표

CashChat iOS 앱의 placeholder 상태인 Apple 로그인(`ContentView.swift`의 `loginWithApple()` — 현재 "Apple 로그인은 준비 중입니다." 토스트만 표시)을, 네이티브 Sign in with Apple(`ASAuthorizationController`) 기반 실제 로그인으로 교체한다. 이미 완성된 백엔드 `POST /api/auth/callback/apple` 엔드포인트와 연동하며, KMM shared 모듈에 Apple API 계약을 추가한다.

본 스펙은 **iOS 프론트엔드 + KMM shared** 범위만 다룬다. 백엔드(완료), 인프라 환경변수(`APPLE_*`, 인프라 담당), Android(미지원)는 범위 밖이다.

참고 문서:
- 백엔드 설계: `docs/superpowers/specs/2026-05-16-apple-social-login-design.md`
- 통합 문서: Confluence `[DOCS] CC-239 · 사용자 인증 시스템` (pageId 15007771)

## 현재 상태

| 항목 | 상태 |
| --- | --- |
| 게스트 로그인 (iOS) | ✅ 동작 (`/api/auth/guest`) |
| Google 로그인 (iOS) | ✅ 동작 (GoogleSignIn → serverAuthCode → `/api/auth/callback/google`) |
| Apple 로그인 (iOS) | ⚠️ placeholder (`loginWithApple()` 토스트만) |
| BE `/api/auth/callback/apple` | ✅ 완료 (authorizationCode 서버 재교환 + id_token 검증) |
| KMM `AuthApiService.loginWithApple` | ❌ 없음 |
| KMM `AppleOAuthCallbackRequest` 모델 | ❌ 없음 |

## API 계약 (BE 완료분, 변경 없음)

```
POST /api/auth/callback/apple
Content-Type: application/json
{
  "authorizationCode": "c1a2b3...",   // 필수 (@NotBlank)
  "identityToken": "eyJraWQ...",       // 선택 (현재 BE 검증 미사용, 계약 필드)
  "fullName": "홍길동",                 // 선택 (Apple 최초 인증 시에만 제공)
  "deviceToken": "9c1d2e3f-..."        // 선택 (게스트 → 회원 승격 연결)
}

200 OK → AuthResponse { userId, role, accessToken, refreshToken }
```

핵심: BE는 클라이언트가 보낸 `authorizationCode`를 **서버에서 직접 재교환**(ES256 client_secret 생성 → Apple token endpoint)하여 id_token을 얻고 JWKS로 검증한다. 따라서 클라이언트는 nonce를 생성/검증할 필요가 없다. `email`은 BE가 id_token claims에서 추출하므로 요청 바디에 email 필드가 없다.

## 아키텍처

Google 로그인 흐름과 동일한 3-레이어 패턴을 따른다.

```
OnboardingView "Apple로 로그인" 버튼
  → AppState.loginWithApple()                 [Swift, placeholder 교체 → async]
      → AppleSignInCoordinator.signIn()        [Swift 신규: ASAuthorizationController async 래핑]
          → (authorizationCode, identityToken?, fullName?) 반환
      → AuthApiService.loginWithApple(...)      [KMM shared 신규 메서드]
          → POST /api/auth/callback/apple       [BE 완료]
      → KeychainHelper에 accessToken/role/refreshToken 저장
      → isAuthenticated = true
```

## 변경 단위

### 1. KMM shared (commonMain)

**1-1. `auth/model/AppleOAuthCallbackRequest.kt` (신규)**

`GoogleOAuthCallbackRequest`와 동일 패턴의 `@Serializable` data class:

```kotlin
@Serializable
data class AppleOAuthCallbackRequest(
    val authorizationCode: String,
    val identityToken: String? = null,
    val fullName: String? = null,
    val deviceToken: String? = null
)
```

**1-2. `auth/AuthApiService.kt` — `loginWithApple(...)` 추가**

`loginWithGoogle`과 동일 구조. Ktor `httpClient.post`로 `$baseUrl/api/auth/callback/apple` 호출, JSON 바디 전송, `AuthResponse` 반환.

```kotlin
suspend fun loginWithApple(
    authorizationCode: String,
    identityToken: String?,
    fullName: String?,
    deviceToken: String
): AuthResponse
```

### 2. iOS Swift

**2-1. `AppleSignInCoordinator.swift` (신규)**

- `import AuthenticationServices`
- `NSObject` 기반, `ASAuthorizationControllerDelegate` + `ASAuthorizationControllerPresentationContextProviding` 채택
- `func signIn() async throws -> AppleCredential` — `withCheckedThrowingContinuation`으로 delegate 콜백을 async/await로 브리지
- `ASAuthorizationAppleIDProvider().createRequest()` 생성, `requestedScopes = [.fullName, .email]`
- 성공 콜백(`didCompleteWithAuthorization`)에서 `ASAuthorizationAppleIDCredential` 추출:
  - `authorizationCode: Data` → UTF-8 String (없으면 에러 throw)
  - `identityToken: Data?` → UTF-8 String (선택)
  - `fullName: PersonNameComponents?` → `PersonNameComponentsFormatter`로 String 조합 (빈 문자열이면 nil)
- 실패 콜백(`didCompleteWithError`)에서 `ASAuthorizationError.canceled`는 별도 `AppleSignInError.canceled`로 매핑(상위에서 조용히 무시), 그 외는 에러 전파
- continuation 중복 resume 방지 가드

반환 구조체:
```swift
struct AppleCredential {
    let authorizationCode: String
    let identityToken: String?
    let fullName: String?
}
```

**2-2. `ContentView.swift` — `AppState.loginWithApple()` 교체**

기존 placeholder(83~86줄) 제거 후 `async` 메서드로 전환. `loginWithGoogle()`의 토큰 저장/에러 처리 패턴을 그대로 재사용:

```swift
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
        // 사용자 취소 → 토스트 없이 무시
    } catch {
        errorMessage = "Apple 로그인에 실패했습니다. 다시 시도해주세요."
    }
}
```

`AppState`는 `AppleSignInCoordinator` 인스턴스를 보유한다(coordinator는 delegate 유지를 위해 strong reference 필요).

**2-3. `OnboardingView` Apple 버튼 연동**

`appState.loginWithApple()`(동기 placeholder) 호출부를 `Button { Task { await appState.loginWithApple() } }`로 변경. Google 버튼과 동일하게 `isLoading` 동안 disabled.

### 3. Xcode 설정

- **Sign in with Apple capability 추가**: `CashChatIOS.entitlements` 파일 신규 생성(`com.apple.developer.applesignin = [Default]`)하고 `project.pbxproj`의 `CODE_SIGN_ENTITLEMENTS` 빌드 설정으로 연결. ASAuthorization 동작에 필수.
- Apple Developer Portal의 App ID capability enable 및 Services ID 발급은 인프라/계정 담당 영역(코드 변경 아님, 체크리스트로만 명시).

## 에러 처리

| 상황 | 처리 |
| --- | --- |
| 사용자 취소 (`ASAuthorizationError.canceled`) | 토스트 없이 무시 |
| authorizationCode 추출 실패 | 에러 throw → 토스트 |
| 네트워크/BE 오류 (502 등) | 기존 `errorMessage` 토스트 |
| 기타 ASAuthorization 오류 | 토스트 |

기존 Google 흐름과 달리 실패 시 게스트 세션으로 자동 복귀하지 않는다(로그인 화면에 머무름 — 현재 iOS Google 흐름과 동일).

## 검증

- **KMM**: `cd apps/frontend && ./gradlew :shared:assembleDebug` — 신규 모델/메서드 컴파일 성공 확인
- **iOS 빌드**: `./gradlew :shared:embedAndSignAppleFrameworkForXcode`(JAVA_HOME=JDK21) 후 `xcodebuild`로 Swift 컴파일 확인. 실제 Apple ID 로그인은 Apple Developer 계정 + 실기기 필요 → 빌드/컴파일 단계까지 자동 검증, 실기기 통합 테스트는 수동
- Apple 최초 가입 시에만 `fullName`/`email` 전달되는 케이스를 코드 주석으로 명시

## 범위 밖 (Non-Goals)

- 인프라 `APPLE_*` 환경변수 설정 (Confluence 섹션 7, 인프라 담당)
- Android Apple 로그인 (미지원 결정)
- 백엔드 코드 변경 (완료됨)
- OnboardingView UI/레이아웃 리디자인 (버튼 동작 연동만)
- 계정 병합 기능 (BE 미지원)
