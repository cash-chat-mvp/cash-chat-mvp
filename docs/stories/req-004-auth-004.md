# Story: REQ-004 세션·추가 인증

Status: Draft

## Story

관리자로서 계정 탈취와 방치된 로그인 정보의 악용 방지를 위해 세션과 추가 인증을 안전하게 관리할 수 있다.

- 우선순위: P1
- 주 도메인: 관리자 인증·권한
- 에픽: 관리자 인증·권한 (v.0.2) — CC-567
- Jira 스토리: CC-585
- 원문 근거: INTAKE-001 US-ADM-AUTH-004
- 기존 관계: 신규 (v.0.2)
- UI: [로그인](../ux/wireframes/pages/00-login/index.html)

## Acceptance Criteria (원문 전사)

1. AC-01 유휴 만료: 정책 시간 무활동 후 다음 API 호출 시 세션을 만료시키고 재로그인을 요구한다.
2. AC-02 추가 인증: 비밀번호 인증 통과 후 등록된 추가 인증 수단을 검증한 뒤 세션을 발급한다.
3. AC-03 로그아웃: 로그아웃 시 서버 세션을 무효화하고 재사용을 차단한다.

## Tasks

- [ ] AC-01 유휴 만료 — 구현·검증 (CC-714)
- [ ] AC-02 추가 인증 — 구현·검증 (CC-715)
- [ ] AC-03 로그아웃 — 구현·검증 (CC-716)
- [ ] 관리 화면 구현 (와이어프레임 기준) (CC-717)

## Dev Notes

- Architecture: [architecture.md](../architecture.md) — React+TS admin-frontend / Kotlin Spring Boot admin-backend / 운영 MySQL 공유 / MFA=TOTP.
- 불변 규칙: 인증·RBAC 통과 없이는 반환·변경 금지, 자산 변경은 원장 필수, 변경은 감사와 원자적 커밋.
- DB 테이블·컬럼·API 스키마는 구현(4단계)에서 확정한다.
