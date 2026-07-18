# Story: REQ-050 배포·재기동 요청

Status: Draft

## Story

권한 있는 운영 관리자로서 장애 복구를 위해 승인된 배포 파이프라인의 재기동 작업을 요청할 수 있다.

- 우선순위: P3
- 주 도메인: 서버·배포 운영
- Epic: 서버·배포 운영 (v.0.2) — Jira CC-503
- Jira Task: CC-556
- 원문 근거: INTAKE-001 US-ADM-SERVER-003
- 기존 관계: 신규 (v.0.2)
- UI: ADMIN_UI — 와이어프레임: ../ux/wireframes/req-050/index.html

## Acceptance Criteria (원문 전사)

1. AC-01 파이프라인 호출: 허용된 파이프라인만 최소 권한 자격증명으로 호출한다.
2. AC-02 이중 확인: 프로덕션 재기동 시 대상 환경과 예상 영향을 재확인한다.
3. AC-03 상태 추적: 요청자, 외부 실행 ID, 진행 상태, 결과를 표시한다.

## Dev Notes

- Architecture: [architecture.md](../architecture.md) — React+TS admin-frontend / Kotlin Spring Boot admin-backend / 운영 MySQL 공유.
- 불변 규칙: 인증·RBAC 통과 없이는 반환·변경 금지, 자산 변경은 원장 필수, 변경은 감사와 원자적 커밋.
- DB 테이블·컬럼·API 스키마는 구현(4단계)에서 확정한다.

## 관계

- 없음

## Tasks

- [ ] 구현 착수 시 세분화 (스프린트 4단계)
