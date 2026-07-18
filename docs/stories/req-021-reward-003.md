# Story: REQ-021 Energy 수동 조정

Status: Draft

## Story

권한 있는 운영 관리자로서 광고·채팅 보상 문제를 정정하기 위해 회원의 Energy를 수동으로 지급하거나 차감할 수 있다.

- 우선순위: P0
- 주 도메인: 보상·경제
- 에픽: 보상·경제 (v.0.2) — CC-571
- Jira 스토리: CC-602
- 원문 근거: INTAKE-001 US-ADM-REWARD-003
- 기존 관계: 신규 (v.0.2)
- UI: [포인트·Energy 수동 조정](../ux/wireframes/pages/10-manual-adjust/index.html)

## Acceptance Criteria (원문 전사)

1. AC-01 상한 적용: 최대 Energy 초과 지급 시 요청량, 실제 지급량, 상한 적용 결과를 원장에 기록한다.
2. AC-02 예약 보호: 채팅 예약 Energy를 제외한 사용 가능 Energy만 차감한다.
3. AC-03 원자성: 지갑, Energy 원장, 감사 로그를 함께 저장한다.

## Tasks

- [ ] AC-01 상한 적용 — 구현·검증 (CC-782)
- [ ] AC-02 예약 보호 — 구현·검증 (CC-783)
- [ ] AC-03 원자성 — 구현·검증 (CC-784)
- [ ] 관리 화면 구현 (와이어프레임 기준) (CC-785)

## Dev Notes

- Architecture: [architecture.md](../architecture.md) — React+TS admin-frontend / Java Spring Boot admin-backend / 운영 MySQL 공유 / MFA=TOTP.
- 불변 규칙: 인증·RBAC 통과 없이는 반환·변경 금지, 자산 변경은 원장 필수, 변경은 감사와 원자적 커밋.
- DB 테이블·컬럼·API 스키마는 구현(4단계)에서 확정한다.
