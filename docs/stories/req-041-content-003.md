# Story: REQ-041 출석·초대·룰렛 정책

Status: Draft

## Story

운영 관리자로서 리텐션 보상을 조정하기 위해 출석·초대·룰렛 정책을 관리할 수 있다.

- 우선순위: P2
- 주 도메인: 진화·리텐션 콘텐츠
- 에픽: 진화·리텐션 콘텐츠 (v.0.2) — CC-575
- Jira 스토리: CC-622
- 원문 근거: INTAKE-001 US-ADM-CONTENT-003
- 기존 관계: 신규 (v.0.2)
- UI: [출석·초대·룰렛 정책](../ux/wireframes/pages/21-retention-policy/index.html)

## Acceptance Criteria (원문 전사)

1. AC-01 출석 정책: 중복 일차와 음수 보상을 거부한다.
2. AC-02 초대 정책: 신규 redeem부터 새 버전을 적용한다.
3. AC-03 룰렛 정책: 확률 합계와 지급 상한을 검증한다.

## Tasks

- [ ] AC-01 출석 정책 — 구현·검증 (CC-862)
- [ ] AC-02 초대 정책 — 구현·검증 (CC-863)
- [ ] AC-03 룰렛 정책 — 구현·검증 (CC-864)
- [ ] 관리 화면 구현 (와이어프레임 기준) (CC-865)

## Dev Notes

- Architecture: [architecture.md](../architecture.md) — React+TS admin-frontend / Java Spring Boot admin-backend / 운영 MySQL 공유 / MFA=TOTP.
- 불변 규칙: 인증·RBAC 통과 없이는 반환·변경 금지, 자산 변경은 원장 필수, 변경은 감사와 원자적 커밋.
- DB 테이블·컬럼·API 스키마는 구현(4단계)에서 확정한다.
