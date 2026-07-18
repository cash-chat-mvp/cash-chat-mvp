# Story: REQ-016 위험 표시·내부 메모

Status: Draft

## Story

운영 관리자로서 의심 회원의 조사 상태를 공유하기 위해 위험 표시와 내부 메모를 관리할 수 있다.

- 우선순위: P1
- 주 도메인: 회원·제재
- 에픽: 회원·제재 (v.0.2) — CC-570
- Jira 스토리: CC-597
- 원문 근거: INTAKE-001 US-ADM-MEMBER-005
- 기존 관계: 신규 (v.0.2)
- UI: [회원 상세](../ux/wireframes/pages/06-member-detail/index.html)

## Acceptance Criteria (원문 전사)

1. AC-01 표시 등록: 위험 유형, 사유, 근거 입력 시 회원 상세에 내부 위험 표시를 추가한다.
2. AC-02 사용자 비노출: 내부 표시와 메모를 서비스 앱에 노출하지 않는다.
3. AC-03 해제 이력: 해제 사유 입력 시 표시를 비활성화하고 과거 이력을 보존한다.

## Tasks

- [ ] AC-01 표시 등록 — 구현·검증 (CC-762)
- [ ] AC-02 사용자 비노출 — 구현·검증 (CC-763)
- [ ] AC-03 해제 이력 — 구현·검증 (CC-764)
- [ ] 관리 화면 구현 (와이어프레임 기준) (CC-765)

## Dev Notes

- Architecture: [architecture.md](../architecture.md) — React+TS admin-frontend / Java Spring Boot admin-backend / 운영 MySQL 공유 / MFA=TOTP.
- 불변 규칙: 인증·RBAC 통과 없이는 반환·변경 금지, 자산 변경은 원장 필수, 변경은 감사와 원자적 커밋.
- DB 테이블·컬럼·API 스키마는 구현(4단계)에서 확정한다.
