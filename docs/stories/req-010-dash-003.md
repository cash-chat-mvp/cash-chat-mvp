# Story: REQ-010 보상 경제 지표

Status: Draft

## Story

운영 관리자로서 보상 경제의 이상을 확인하기 위해 포인트와 Energy의 적립·소비량을 조회할 수 있다.

- 우선순위: P1
- 주 도메인: 대시보드
- 에픽: 대시보드 (v.0.2) — CC-569
- Jira 스토리: CC-591
- 원문 근거: INTAKE-001 US-ADM-DASH-003
- 기존 관계: 신규 (v.0.2)
- UI: 2단계에서 와이어프레임 작성 (docs/ux/wireframes/)

## Acceptance Criteria (원문 전사)

1. AC-01 통화 분리: 포인트, Energy, 진화 경험치를 서로 다른 통화로 집계한다.
2. AC-02 채널별 집계: 출석, 채팅, 광고, 오퍼월, 초대, 룰렛, 상점, 관리자 조정을 구분한다.
3. AC-03 이상 표시: 임계치 초과 변동 시 이상 지표와 영향 채널을 표시한다.

## Tasks

- [ ] AC-01 통화 분리 — 구현·검증 (CC-737)
- [ ] AC-02 채널별 집계 — 구현·검증 (CC-738)
- [ ] AC-03 이상 표시 — 구현·검증 (CC-739)
- [ ] 관리 화면 구현 (와이어프레임 기준) (CC-740)

## Dev Notes

- Architecture: [architecture.md](../architecture.md) — React+TS admin-frontend / Kotlin Spring Boot admin-backend / 운영 MySQL 공유 / MFA=TOTP.
- 불변 규칙: 인증·RBAC 통과 없이는 반환·변경 금지, 자산 변경은 원장 필수, 변경은 감사와 원자적 커밋.
- DB 테이블·컬럼·API 스키마는 구현(4단계)에서 확정한다.
