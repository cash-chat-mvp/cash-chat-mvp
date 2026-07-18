# Story: REQ-036 주문 조회

Status: Draft

## Story

운영·CS 관리자로서 구매 문의와 장애를 처리하기 위해 주문 내역을 조회할 수 있다.

- 우선순위: P1
- 주 도메인: 상점·주문
- 에픽: 상점·주문 (v.0.2) — CC-574
- Jira 스토리: CC-617
- 원문 근거: INTAKE-001 US-ADM-SHOP-003
- 기존 관계: 신규 (v.0.2)
- UI: [주문 관리](../ux/wireframes/pages/19-orders/index.html)

## Acceptance Criteria (원문 전사)

1. AC-01 검색: 주문 ID, 회원, 상품, 상태, 기간으로 검색해 최신순 반환한다.
2. AC-02 주문 상세: 가격 스냅샷, 수량, 포인트 거래, 지급 아이템, 상태 이력을 반환한다.
3. AC-03 실패 식별: 실패 단계와 재처리 가능 여부를 표시한다.

## Tasks

- [ ] AC-01 검색 — 구현·검증 (CC-842)
- [ ] AC-02 주문 상세 — 구현·검증 (CC-843)
- [ ] AC-03 실패 식별 — 구현·검증 (CC-844)
- [ ] 관리 화면 구현 (와이어프레임 기준) (CC-845)

## Dev Notes

- Architecture: [architecture.md](../architecture.md) — React+TS admin-frontend / Java Spring Boot admin-backend / 운영 MySQL 공유 / MFA=TOTP.
- 불변 규칙: 인증·RBAC 통과 없이는 반환·변경 금지, 자산 변경은 원장 필수, 변경은 감사와 원자적 커밋.
- DB 테이블·컬럼·API 스키마는 구현(4단계)에서 확정한다.
