# Story: REQ-014 회원 이용 제재

Status: Draft

## Story

운영 관리자로서 정책 위반 사용자의 서비스 이용을 통제하기 위해 회원을 정지하거나 이용을 제한할 수 있다.

- 우선순위: P0
- 주 도메인: 회원·제재
- 에픽: 회원·제재 (v.0.2) — CC-570
- Jira 스토리: CC-595
- 원문 근거: INTAKE-001 US-ADM-MEMBER-003
- 기존 관계: 신규 (v.0.2)
- UI: [이용 제재](../ux/wireframes/pages/07-sanctions/index.html)

## Acceptance Criteria (원문 전사)

1. AC-01 정지·기간 제한: 사유와 선택적 종료 시각 입력 시 상태를 변경하고 활성 인증 토큰을 무효화한다.
2. AC-02 서비스 적용: 정지·제한 회원의 로그인·보호 API 호출을 서비스 서버가 공통 DB 상태 검증으로 거부한다.
3. AC-03 해제: 권한 있는 관리자가 해제 사유 입력 시 정상 상태로 변경하고 다음 인증부터 허용한다.
4. AC-04 감사: 제재 상태 변경 시 전후 상태, 기간, 사유, 처리자를 기록한다.

## Tasks

- [ ] AC-01 정지·기간 제한 — 구현·검증 (CC-754)
- [ ] AC-02 서비스 적용 — 구현·검증 (CC-755)
- [ ] AC-03 해제 — 구현·검증 (CC-756)
- [ ] AC-04 감사 — 구현·검증 (CC-757)

## Dev Notes

- Architecture: [architecture.md](../architecture.md) — React+TS admin-frontend / Kotlin Spring Boot admin-backend / 운영 MySQL 공유 / MFA=TOTP.
- 불변 규칙: 인증·RBAC 통과 없이는 반환·변경 금지, 자산 변경은 원장 필수, 변경은 감사와 원자적 커밋.
- DB 테이블·컬럼·API 스키마는 구현(4단계)에서 확정한다.
