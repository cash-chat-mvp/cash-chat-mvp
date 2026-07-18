# Story: REQ-048 서비스 상태 조회

Status: Draft

## Story

운영 관리자로서 장애 여부를 빠르게 판단하기 위해 서비스 서버와 주요 의존성의 상태를 조회할 수 있다.

- 우선순위: P0
- 주 도메인: 서버·배포 운영
- 에픽: 서버·배포 운영 (v.0.2) — CC-577
- Jira 스토리: CC-629
- 원문 근거: INTAKE-001 US-ADM-SERVER-001
- 기존 관계: 신규 (v.0.2)
- UI: [서비스 상태·성능](../ux/wireframes/pages/25-system-status/index.html)

## Acceptance Criteria (원문 전사)

1. AC-01 상태 확인: 서비스, DB, 외부 AI, 필수 연동의 정상·비정상 상태와 확인 시각을 표시한다.
2. AC-02 타임아웃: 제한 시간 내 무응답 시 장애 상태로 표시하되 관리자 화면 전체 요청은 실패시키지 않는다.
3. AC-03 이력: 장애 시작·복구 시각과 지속 시간을 반환한다.

## Tasks

- [ ] AC-01 상태 확인 — 구현·검증 (CC-890)
- [ ] AC-02 타임아웃 — 구현·검증 (CC-891)
- [ ] AC-03 이력 — 구현·검증 (CC-892)
- [ ] 관리 화면 구현 (와이어프레임 기준) (CC-939)

## Dev Notes

- Architecture: [architecture.md](../architecture.md) — React+TS admin-frontend / Java Spring Boot admin-backend / 운영 MySQL 공유 / MFA=TOTP.
- 불변 규칙: 인증·RBAC 통과 없이는 반환·변경 금지, 자산 변경은 원장 필수, 변경은 감사와 원자적 커밋.
- DB 테이블·컬럼·API 스키마는 구현(4단계)에서 확정한다.
