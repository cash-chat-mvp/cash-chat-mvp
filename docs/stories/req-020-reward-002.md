# Story: REQ-020 포인트 수동 조정

Status: Draft

## Story

권한 있는 운영 관리자로서 보상 누락과 오지급을 정정하기 위해 회원의 포인트를 수동으로 지급하거나 차감할 수 있다.

- 우선순위: P0
- 주 도메인: 보상·경제
- 에픽: 보상·경제 (v.0.2) — CC-571
- Jira 스토리: CC-601
- 원문 근거: INTAKE-001 US-ADM-REWARD-002
- 기존 관계: 신규 (v.0.2)
- UI: [포인트·Energy 수동 조정](../ux/wireframes/pages/10-manual-adjust/index.html)

## Acceptance Criteria (원문 전사)

1. AC-01 지급·차감: 회원, 금액, 사유, 관련 번호가 유효하면 지갑과 원장을 단일 트랜잭션으로 변경한다.
2. AC-02 음수 방지: 차감 후 잔액이 음수가 되면 거부하고 데이터를 변경하지 않는다.
3. AC-03 멱등성: 동일 요청 식별자 재요청은 기존 결과를 반환하고 중복 조정하지 않는다.

## Tasks

- [ ] AC-01 지급·차감 — 구현·검증 (CC-778)
- [ ] AC-02 음수 방지 — 구현·검증 (CC-779)
- [ ] AC-03 멱등성 — 구현·검증 (CC-780)
- [ ] 관리 화면 구현 (와이어프레임 기준) (CC-781)

## Dev Notes

- Architecture: [architecture.md](../architecture.md) — React+TS admin-frontend / Kotlin Spring Boot admin-backend / 운영 MySQL 공유 / MFA=TOTP.
- 불변 규칙: 인증·RBAC 통과 없이는 반환·변경 금지, 자산 변경은 원장 필수, 변경은 감사와 원자적 커밋.
- DB 테이블·컬럼·API 스키마는 구현(4단계)에서 확정한다.
