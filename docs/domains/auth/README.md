# auth 도메인 — 유저 스토리 인덱스

인증(소셜 로그인·토큰 발급)의 유저 스토리 카탈로그. 각 파일은 직군 중립 계약(스토리 + 인수 조건)이다.

> 파일명 = 불변 ID(`US-AUTH-NNN`) + slug. 번호는 생성 순서일 뿐 의미 없음.

| ID | 스토리 | 상태 | Jira | 원본 spec |
| -- | ------ | ---- | ---- | --------- |
| [US-AUTH-001](./US-AUTH-001-apple-social-login.md) | Apple 소셜 로그인(iOS) | implemented | CC-239 | specs/auth |

> Google OAuth 로그인·게스트 로그인은 기존 구현이며 별도 spec 문서로 응결되지 않았다. 필요 시 `US-AUTH-002`, `US-AUTH-003`으로 추가.
