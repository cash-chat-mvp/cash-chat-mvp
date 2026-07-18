# Story: REQ-035 재고·판매 정책 관리

Status: Draft

## Story

운영 관리자로서 품절과 과판매를 방지하기 위해 상품 재고와 판매 조건을 관리할 수 있다.

- 우선순위: P2
- 주 도메인: 상점·주문
- 에픽: 상점·주문 (v.0.2) — CC-574
- Jira 스토리: CC-616
- 원문 근거: INTAKE-001 US-ADM-SHOP-002
- 기존 관계: 신규 (v.0.2)
- UI: 2단계에서 와이어프레임 작성 (docs/ux/wireframes/)

## Acceptance Criteria (원문 전사)

1. AC-01 재고: 재고 원장과 현재 수량을 단일 트랜잭션으로 변경한다.
2. AC-02 판매 조건: 판매 기간·회원별 구매 제한을 설정하고 구매 시점에 재검증한다.
3. AC-03 동시 구매: 재고보다 많은 주문이 완료되지 않는다.

## Tasks

- [ ] AC-01 재고 — 구현·검증 (CC-838)
- [ ] AC-02 판매 조건 — 구현·검증 (CC-839)
- [ ] AC-03 동시 구매 — 구현·검증 (CC-840)
- [ ] 관리 화면 구현 (와이어프레임 기준) (CC-841)

## Dev Notes

- Architecture: [architecture.md](../architecture.md) — React+TS admin-frontend / Kotlin Spring Boot admin-backend / 운영 MySQL 공유 / MFA=TOTP.
- 불변 규칙: 인증·RBAC 통과 없이는 반환·변경 금지, 자산 변경은 원장 필수, 변경은 감사와 원자적 커밋.
- DB 테이블·컬럼·API 스키마는 구현(4단계)에서 확정한다.
