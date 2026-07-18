# Story: REQ-038 교환 상품·발송 관리

Status: Draft

## Story

운영 관리자로서 기프티콘과 교환 상품을 안정적으로 제공하기 위해 공급업체와 발송 상태를 관리할 수 있다.

- 우선순위: P3
- 주 도메인: 상점·주문
- 에픽: 상점·주문 (v.0.2) — CC-574
- Jira 스토리: CC-619
- 원문 근거: INTAKE-001 US-ADM-SHOP-005
- 기존 관계: 신규 (v.0.2)
- UI: [주문 관리](../ux/wireframes/pages/19-orders/index.html)

## Acceptance Criteria (원문 전사)

1. AC-01 공급 정보: 공급업체, 공급 상품 코드, 유효기간 정책을 등록하고 민감 인증 정보는 암호화 저장한다.
2. AC-02 발송 상태: 요청, 성공, 실패, 재시도 상태와 외부 거래 ID를 기록한다.
3. AC-03 실패 재처리: 같은 외부 멱등 키로 중복 발송 없이 처리한다.

## Tasks

- [ ] AC-01 공급 정보 — 구현·검증 (CC-850)
- [ ] AC-02 발송 상태 — 구현·검증 (CC-851)
- [ ] AC-03 실패 재처리 — 구현·검증 (CC-852)
- [ ] 관리 화면 구현 (와이어프레임 기준) (CC-853)

## Dev Notes

- Architecture: [architecture.md](../architecture.md) — React+TS admin-frontend / Java Spring Boot admin-backend / 운영 MySQL 공유 / MFA=TOTP.
- 불변 규칙: 인증·RBAC 통과 없이는 반환·변경 금지, 자산 변경은 원장 필수, 변경은 감사와 원자적 커밋.
- DB 테이블·컬럼·API 스키마는 구현(4단계)에서 확정한다.
