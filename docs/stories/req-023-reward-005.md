# Story: REQ-023 실패 보상 재처리

Status: Draft

## Story

권한 있는 운영 관리자로서 보상 누락을 안전하게 복구하기 위해 재처리 가능한 보상을 다시 처리할 수 있다.

- 우선순위: P1
- 주 도메인: 보상·경제
- 에픽: 보상·경제 (v.0.2) — CC-571
- Jira 스토리: CC-604
- 원문 근거: INTAKE-001 US-ADM-REWARD-005
- 기존 관계: 신규 (v.0.2)
- UI: [채널별 보상·실패 재처리](../ux/wireframes/pages/11-reward-channels/index.html)

## Acceptance Criteria (원문 전사)

1. AC-01 가능 여부: 원본 유효성, 기존 지급, 멱등 키 검사 결과를 반환한다.
2. AC-02 안전한 재처리: 원본이 유효하고 지급 거래가 없으면 기존 비즈니스 규칙으로 한 번만 지급한다.
3. AC-03 무효 이벤트 차단: 위조 서명, 미존재 회원, 기지급 거래는 거부하고 근거를 기록한다.

## Tasks

- [ ] AC-01 가능 여부 — 구현·검증 (CC-790)
- [ ] AC-02 안전한 재처리 — 구현·검증 (CC-791)
- [ ] AC-03 무효 이벤트 차단 — 구현·검증 (CC-792)
- [ ] 관리 화면 구현 (와이어프레임 기준) (CC-793)

## Dev Notes

- Architecture: [architecture.md](../architecture.md) — React+TS admin-frontend / Kotlin Spring Boot admin-backend / 운영 MySQL 공유 / MFA=TOTP.
- 불변 규칙: 인증·RBAC 통과 없이는 반환·변경 금지, 자산 변경은 원장 필수, 변경은 감사와 원자적 커밋.
- DB 테이블·컬럼·API 스키마는 구현(4단계)에서 확정한다.
