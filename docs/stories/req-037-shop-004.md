# Story: REQ-037 주문 취소·복구

Status: Draft

## Story

권한 있는 운영 관리자로서 주문 오류를 정정하기 위해 주문을 취소하거나 지급을 복구할 수 있다.

- 우선순위: P2
- 주 도메인: 상점·주문
- 에픽: 상점·주문 (v.0.2) — CC-574
- Jira 스토리: CC-618
- 원문 근거: INTAKE-001 US-ADM-SHOP-004
- 기존 관계: 신규 (v.0.2)
- UI: [주문 관리](../ux/wireframes/pages/19-orders/index.html)

## Acceptance Criteria (원문 전사)

1. AC-01 취소 가능 여부: 주문 상태, 아이템 사용 여부, 환불 이력을 검증한다.
2. AC-02 원자적 취소: 아이템 회수, 포인트 환불, 주문 상태, 감사 로그를 함께 처리한다.
3. AC-03 중복 취소 방지: 이미 취소된 주문은 기존 결과를 반환하고 추가 환불하지 않는다.

## Tasks

- [ ] AC-01 취소 가능 여부 — 구현·검증 (CC-846)
- [ ] AC-02 원자적 취소 — 구현·검증 (CC-847)
- [ ] AC-03 중복 취소 방지 — 구현·검증 (CC-848)
- [ ] 관리 화면 구현 (와이어프레임 기준) (CC-849)

## Dev Notes

- Architecture: [architecture.md](../architecture.md) — React+TS admin-frontend / Kotlin Spring Boot admin-backend / 운영 MySQL 공유 / MFA=TOTP.
- 불변 규칙: 인증·RBAC 통과 없이는 반환·변경 금지, 자산 변경은 원장 필수, 변경은 감사와 원자적 커밋.
- DB 테이블·컬럼·API 스키마는 구현(4단계)에서 확정한다.
