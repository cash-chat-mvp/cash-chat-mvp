# Story: REQ-034 상품 관리

Status: Draft

## Story

운영 관리자로서 판매 카탈로그를 운영하기 위해 상품을 등록·수정·비활성화할 수 있다.

- 우선순위: P2
- 주 도메인: 상점·주문
- Epic: 상점·주문 (v.0.2) — Jira CC-500
- Jira Task: CC-540
- 원문 근거: INTAKE-001 US-ADM-SHOP-001
- 기존 관계: 신규 (v.0.2)
- UI: ADMIN_UI — 와이어프레임: ../ux/wireframes/req-034/index.html

## Acceptance Criteria (원문 전사)

1. AC-01 상품 등록: 상품 코드, 이름, 설명, 이미지, 카테고리, 가격, 노출 순서를 입력하면 중복되지 않은 상품을 비활성 초안으로 생성한다.
2. AC-02 판매 활성화: 필수 정보·재고 정책이 유효하면 서비스 카탈로그에 노출한다.
3. AC-03 삭제 제한: 주문·인벤토리 이력이 있는 상품은 물리 삭제를 거부하고 비활성화만 허용한다.

## Dev Notes

- Architecture: [architecture.md](../architecture.md) — React+TS admin-frontend / Kotlin Spring Boot admin-backend / 운영 MySQL 공유.
- 불변 규칙: 인증·RBAC 통과 없이는 반환·변경 금지, 자산 변경은 원장 필수, 변경은 감사와 원자적 커밋.
- DB 테이블·컬럼·API 스키마는 구현(4단계)에서 확정한다.

## 관계

- 없음

## Tasks

- [ ] 구현 착수 시 세분화 (스프린트 4단계)
