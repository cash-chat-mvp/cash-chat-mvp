# Story: REQ-040 진화 이력 조회

Status: Draft

## Story

운영·CS 관리자로서 진화 문의를 조사하기 위해 회원의 진화 상태와 시도 이력을 조회할 수 있다.

- 우선순위: P1
- 주 도메인: 진화·리텐션 콘텐츠
- 에픽: 진화·리텐션 콘텐츠 (v.0.2) — CC-575
- Jira 스토리: CC-621
- 원문 근거: INTAKE-001 US-ADM-CONTENT-002
- 기존 관계: 신규 (v.0.2)
- UI: 2단계에서 와이어프레임 작성 (docs/ux/wireframes/)

## Acceptance Criteria (원문 전사)

1. AC-01 상태 조회: 현재 레벨, 경험치, 다음 시도 비용을 표시한다.
2. AC-02 시도 이력: 전후 레벨, 비용, 기본·최종 성공률, 타이밍 등급, 결과를 반환한다.
3. AC-03 정책 연결: 계산 근거를 재현할 수 있도록 정책 값을 표시한다.

## Tasks

- [ ] AC-01 상태 조회 — 구현·검증 (CC-858)
- [ ] AC-02 시도 이력 — 구현·검증 (CC-859)
- [ ] AC-03 정책 연결 — 구현·검증 (CC-860)
- [ ] 관리 화면 구현 (와이어프레임 기준) (CC-861)

## Dev Notes

- Architecture: [architecture.md](../architecture.md) — React+TS admin-frontend / Kotlin Spring Boot admin-backend / 운영 MySQL 공유 / MFA=TOTP.
- 불변 규칙: 인증·RBAC 통과 없이는 반환·변경 금지, 자산 변경은 원장 필수, 변경은 감사와 원자적 커밋.
- DB 테이블·컬럼·API 스키마는 구현(4단계)에서 확정한다.
