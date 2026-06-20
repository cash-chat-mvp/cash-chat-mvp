# Apple 소셜 로그인 백엔드 작업 체크리스트

> Source plan: `docs/superpowers/plans/2026-05-16-apple-social-login.md`

## 작업 1: API 계약과 테스트 뼈대

- [ ] `authorizationCode`를 필수로 받고 `identityToken`, `fullName`, `deviceToken`을 선택값으로 받는 `AppleOAuthCallbackRequest`를 추가한다.
- [ ] `POST /api/auth/callback/apple`에 대한 controller 테스트를 추가한다.
- [ ] `authorizationCode`가 없거나 빈 값이면 요청 검증 실패가 발생하는지 확인한다.
- [ ] controller가 Apple 로그인 요청을 `AuthService`의 Apple provider 흐름으로 위임하는지 확인한다.

## 작업 2: Provider 모델과 설정

- [ ] `AuthProviderType`에 `APPLE`을 추가한다.
- [ ] Apple client id, team id, key id, private key source, token URI, JWKS URI, 필요한 경우 redirect URI를 표현할 수 있도록 OAuth 설정을 확장한다.
- [ ] `.env.example` 또는 application 예시 설정에 실제 secret이 아닌 placeholder Apple 값을 추가한다.
- [ ] Apple private key 원문이 저장소에 커밋되지 않도록 확인한다.

## 작업 3: Apple client secret 생성

- [ ] Apple Team ID를 issuer로, 설정된 client id를 audience/subject 대상으로, Key ID를 header에 포함하는 client secret JWT 생성기를 구현한다.
- [ ] JWT header와 claim 구성이 기대한 형태인지 테스트한다.
- [ ] Apple private key 설정이 없거나 잘못된 경우 실패하는지 테스트한다.
- [ ] 구현 중 요청마다 생성할지, 만료 직전까지 캐시할지 결정하고 코드에 반영한다.

## 작업 4: Apple token exchange client

- [ ] `https://appleid.apple.com/auth/token`에 authorization code 교환 요청을 보내는 client를 구현한다.
- [ ] 요청에는 `client_id`, 생성된 `client_secret`, `code`, `grant_type=authorization_code`, 필요한 경우 설정된 `redirect_uri`를 포함한다.
- [ ] Apple token response를 매핑하고 `id_token`이 없으면 실패로 처리한다.
- [ ] Apple HTTP 실패, 네트워크 실패, 잘못된 응답 형식을 `OAuthException`으로 변환한다.
- [ ] 성공, Apple 거부, 네트워크 실패, `id_token` 누락 케이스를 테스트한다.

## 작업 5: Apple ID token 검증

- [ ] Apple JWKS를 조회하고 token header의 `kid`에 맞는 key를 선택하는 로직을 구현한다.
- [ ] `id_token` 서명을 검증한다.
- [ ] issuer가 `https://appleid.apple.com`인지 검증한다.
- [ ] audience가 설정된 Apple client id와 일치하는지 검증한다.
- [ ] expiration이 유효한지 검증한다.
- [ ] `sub` claim이 존재하는지 검증한다.
- [ ] 테스트용 key로 정상 token, 잘못된 audience, 만료 token, subject 누락, 알 수 없는 key id 케이스를 테스트한다.

## 작업 6: 사용자 매핑과 AuthService 통합

- [ ] 검증된 Apple claim을 `OAuthUserInfo`로 매핑하고, `sub`를 provider id로 사용한다.
- [ ] Apple이 선택 정보를 반환하지 않는 재로그인 상황에서는 기존 사용자의 email/name을 null로 덮어쓰지 않는다.
- [ ] `deviceToken`이 일치하고 provider가 `NONE`인 게스트 사용자는 기존 게스트 승격 흐름을 재사용한다.
- [ ] 승격된 게스트 사용자의 role이 `MEMBER`가 되고 `deviceToken`이 제거되는지 확인한다.
- [ ] 기존 Apple 사용자는 중복 생성 없이 새 CashChat access token과 refresh token을 받는지 확인한다.
- [ ] 포인트 초기화가 기존 인증 응답 생성 경로를 통해 계속 실행되는지 확인한다.

## 작업 7: endpoint 구현

- [ ] `AuthController`에 `POST /api/auth/callback/apple`을 추가한다.
- [ ] 응답은 기존 `AuthResponse` 형태와 동일하게 유지한다.
- [ ] authorization code, Apple token, 생성된 client secret, private key가 로그에 남지 않도록 확인한다.
- [ ] 기존 Google 로그인 endpoint와 동작이 회귀되지 않았는지 확인한다.

## 작업 8: 검증

- [ ] Auth controller와 Auth service 관련 백엔드 단위 테스트 및 web 테스트를 실행한다.
- [ ] 기존 Google OAuth 테스트를 실행한다.
- [ ] 로컬 환경에서 가능하면 전체 backend test suite를 실행한다.
- [ ] 변경 diff에 관련 없는 수정이 섞이지 않았는지 확인한다.
- [ ] 구현 진행 상황에 맞춰 이 checklist를 갱신한다.
