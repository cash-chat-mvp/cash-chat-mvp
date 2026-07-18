# Story: REQ-018 회원 탈퇴 처리

Status: Draft

## Story

운영·CS 관리자로서 회원의 탈퇴 요청을 정책에 맞게 처리하기 위해 탈퇴 상태와 개인정보 처리 단계를 관리할 수 있다.

- 우선순위: P1
- 주 도메인: 회원·제재
- 에픽: 회원·제재 (v.0.2) — CC-570
- Jira 스토리: CC-599
- 원문 근거: INTAKE-001 US-ADM-MEMBER-007
- 기존 관계: 신규 (v.0.2)
- UI: [탈퇴 처리](../ux/wireframes/pages/08-withdrawals/index.html)

## Acceptance Criteria (원문 전사)

1. AC-01 탈퇴 예약: 요청 근거와 처리 예정일 확인 시 탈퇴 예정 상태로 변경하고 활성 인증 토큰을 무효화한다.
2. AC-02 보존 정책 적용: 처리일 도래 시 법적·회계 보존 대상과 즉시 삭제·익명화 대상을 구분 처리한다.
3. AC-03 재가입 식별 정책: 익명화 후 동일 OAuth·기기 재가입은 확정된 정책에 따라 허용·차단하고 과거 개인정보를 임의 복원하지 않는다.
4. AC-04 처리 감사: 상태·처리 단계 변경 시 근거, 처리자, 전후 상태, 결과를 감사 로그에 기록한다.

## Tasks

- [ ] AC-01 탈퇴 예약 — 구현·검증 (CC-770)
- [ ] AC-02 보존 정책 적용 — 구현·검증 (CC-771)
- [ ] AC-03 재가입 식별 정책 — 구현·검증 (CC-772)
- [ ] AC-04 처리 감사 — 구현·검증 (CC-773)
- [ ] 관리 화면 구현 (와이어프레임 기준) (CC-774)

## Dev Notes

- Architecture: [architecture.md](../architecture.md) — React+TS admin-frontend / Kotlin Spring Boot admin-backend / 운영 MySQL 공유 / MFA=TOTP.
- 불변 규칙: 인증·RBAC 통과 없이는 반환·변경 금지, 자산 변경은 원장 필수, 변경은 감사와 원자적 커밋.
- DB 테이블·컬럼·API 스키마는 구현(4단계)에서 확정한다.
