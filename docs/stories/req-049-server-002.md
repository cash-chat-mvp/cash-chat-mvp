# Story: REQ-049 오류·응답시간 조회

Status: Draft

## Story

운영 관리자로서 장애 원인을 조사하기 위해 서버 오류율, 응답시간과 오류 로그를 조회할 수 있다.

- 우선순위: P1
- 주 도메인: 서버·배포 운영
- Epic: 서버·배포 운영 (v.0.2) — Jira CC-503
- Jira Task: CC-555
- 원문 근거: INTAKE-001 US-ADM-SERVER-002
- 기존 관계: 신규 (v.0.2)
- UI: N/A (백엔드·연동 기반 Story)

## Acceptance Criteria (원문 전사)

1. AC-01 메트릭: API별 요청 수, 오류율, 응답시간 백분위를 반환한다.
2. AC-02 오류 검색: 추적 ID, API, 오류 종류, 기간으로 검색하고 민감정보를 마스킹한다.
3. AC-03 운영 데이터 연결: 권한 범위 안에서 관련 운영 데이터로 이동한다.

## Dev Notes

- Architecture: [architecture.md](../architecture.md) — React+TS admin-frontend / Kotlin Spring Boot admin-backend / 운영 MySQL 공유.
- 불변 규칙: 인증·RBAC 통과 없이는 반환·변경 금지, 자산 변경은 원장 필수, 변경은 감사와 원자적 커밋.
- DB 테이블·컬럼·API 스키마는 구현(4단계)에서 확정한다.

## 관계

- 없음

## Tasks

- [ ] 구현 착수 시 세분화 (스프린트 4단계)
