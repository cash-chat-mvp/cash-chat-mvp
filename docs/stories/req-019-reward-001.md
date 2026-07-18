# Story: REQ-019 자산·변동 원장 조회

Status: Draft

## Story

운영·CS 관리자로서 보상 적립과 사용 문제를 조사하기 위해 회원의 포인트와 Energy 변동 내역을 조회할 수 있다.

- 우선순위: P0
- 주 도메인: 보상·경제
- 에픽: 보상·경제 (v.0.2) — CC-571
- Jira 스토리: CC-600
- 원문 근거: INTAKE-001 US-ADM-REWARD-001
- 기존 관계: 신규 (v.0.2)
- UI: [자산·원장 조회](../ux/wireframes/pages/09-ledger/index.html)

## Acceptance Criteria (원문 전사)

1. AC-01 포인트: 현재 잔액, 증감, 변경 후 잔액, 사유, 원본 식별자, 시각을 반환한다.
2. AC-02 Energy: 사용·예약 Energy와 광고, 채팅 예약·정산·환불, 초대, 룰렛, 관리자 조정 이력을 반환한다.
3. AC-03 정합성: 최신 변경 후 잔액과 현재 지갑 잔액이 일치한다.

## Tasks

- [ ] AC-01 포인트 — 구현·검증 (CC-775)
- [ ] AC-02 Energy — 구현·검증 (CC-776)
- [ ] AC-03 정합성 — 구현·검증 (CC-777)
- [ ] 관리 화면 구현 (와이어프레임 기준) (CC-938)

## Dev Notes

- Architecture: [architecture.md](../architecture.md) — React+TS admin-frontend / Java Spring Boot admin-backend / 운영 MySQL 공유 / MFA=TOTP.
- 불변 규칙: 인증·RBAC 통과 없이는 반환·변경 금지, 자산 변경은 원장 필수, 변경은 감사와 원자적 커밋.
- DB 테이블·컬럼·API 스키마는 구현(4단계)에서 확정한다.
