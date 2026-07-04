---
id: US-AUTH-001
domain: auth
slug: apple-social-login
status: implemented
jira: CC-239            # BE Apple Social Login
source: docs/specs/auth/spec.md
related-domains: [auth, user, point]
---

# Apple 소셜 로그인 (iOS)

## 스토리

iOS 사용자로서, 나는 Apple 계정으로 로그인해 게스트 세션을 회원 계정으로 전환하거나 기존 Apple 계정으로 다시 로그인하고 싶다.
게스트 사용자로서, 나는 Apple 로그인 후에도 기존 세션에서 쌓은 데이터·포인트를 이어 쓰고 싶다.
백엔드로서, 나는 클라이언트가 준 식별값을 그대로 믿지 않고 Apple `authorizationCode`/`id_token`을 서버에서 검증하고 싶다.

## 수용 조건 (Acceptance Criteria)

- [ ] **AC-01 정상 Apple 로그인**
  Given 유효한 `authorizationCode`가 전달되고 Apple token endpoint가 설정된 client id에 대한 `id_token`을 반환한다
  When `POST /api/auth/callback/apple`
  Then 백엔드는 `id_token`의 서명·issuer·audience·expiration을 검증하고 `accessToken/refreshToken/userId/role=MEMBER`를 반환한다.

- [ ] **AC-02 게스트 승격**
  Given 전달된 `deviceToken`과 일치하는 게스트가 있고 provider가 `NONE`이며 Apple 계정이 아직 미연결이다
  Then 기존 게스트를 provider `APPLE`로 변경(신규 생성 아님)하고, 재사용 방지를 위해 `deviceToken`을 제거한다.

- [ ] **AC-03 기존 Apple 사용자 로그인**
  Given Apple `sub` + provider `APPLE` 사용자가 이미 존재한다
  Then 기존 사용자에게 새 토큰을 발급하고 중복 사용자를 만들지 않는다.

- [ ] **AC-04 토큰 교환 실패**
  Given Apple이 code/secret/redirect/client id 중 하나를 거부한다
  Then OAuth 인증 실패로 처리하고, 응답에 Apple 원문·private key·client secret이 노출되지 않는다.

- [ ] **AC-05 잘못된 id_token**
  Given `id_token`이 없거나/만료/서명 오류/audience 불일치다
  Then 로그인을 거부하고 사용자를 생성·수정하지 않는다.

- [ ] **AC-06 비-Apple 플랫폼**
  Then Android 전용 Apple 로그인 흐름은 추가하지 않으며 플랫폼별 UX·클라이언트 구현은 범위 외.

## 검증 매핑 (Verification)

- BE: `id_token` 서명·claim 검증, JWKS 처리, 게스트 승격/기존 사용자/신규 생성 분기 테스트
- 보안: Apple private key는 env/secret 주입, 저장소 커밋 금지

## 관련

- 기술 상세(백엔드 흐름·시퀀스·결정 기록): `docs/specs/auth/spec.md`
- 설계 계획: `docs/superpowers/specs/2026-05-16-apple-social-login-design.md`
