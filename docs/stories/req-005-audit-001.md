# Story: REQ-005 관리자 변경 감사

Status: Draft

## Story

감사 담당자로서 운영 변경의 책임과 경위를 추적하기 위해 관리자 작업 이력을 조회할 수 있다.

- 우선순위: P0
- 주 도메인: 감사·보안
- Epic: 감사·보안 (v.0.2) — Jira CC-494
- Jira Task: CC-511
- 원문 근거: INTAKE-001 US-ADM-AUDIT-001
- 기존 관계: 신규 (v.0.2)
- UI: N/A (백엔드·연동 기반 Story)

## Acceptance Criteria (원문 전사)

1. AC-01 변경 기록: 제재·자산 조정·설정 변경 트랜잭션 처리 시 기능, 대상, 처리자, 사유, 전후 값, 시각과 IP를 기록한다.
2. AC-02 원자성: 감사 로그 저장 실패 시 업무 데이터 변경도 롤백한다.
3. AC-03 불변성: 저장된 감사 로그의 수정·삭제 시도는 거부한다.

## Dev Notes

- Architecture: [architecture.md](../architecture.md) — React+TS admin-frontend / Kotlin Spring Boot admin-backend / 운영 MySQL 공유.
- 불변 규칙: 인증·RBAC 통과 없이는 반환·변경 금지, 자산 변경은 원장 필수, 변경은 감사와 원자적 커밋.
- DB 테이블·컬럼·API 스키마는 구현(4단계)에서 확정한다.

## 관계

- 없음

## Tasks

- [ ] 구현 착수 시 세분화 (스프린트 4단계)
