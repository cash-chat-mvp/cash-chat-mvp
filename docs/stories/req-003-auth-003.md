# Story: REQ-003 역할별 권한 관리

Status: Draft

## Story

슈퍼 관리자로서 업무 범위에 맞는 접근 통제를 위해 관리자 역할과 세부 권한을 관리할 수 있다.

- 우선순위: P0
- 주 도메인: 관리자 인증·권한
- Epic: 관리자 인증·권한 (v.0.2) — Jira CC-493
- Jira Task: CC-509
- 원문 근거: INTAKE-001 US-ADM-AUTH-003
- 기존 관계: 신규 (v.0.2)
- UI: ADMIN_UI — 와이어프레임: ../ux/wireframes/req-003/index.html

## Acceptance Criteria (원문 전사)

1. AC-01 권한 구성: 역할에 조회·변경·다운로드 권한을 부여하면 다음 API 요청부터 적용한다.
2. AC-02 서버 검증: 권한 없는 관리자가 API를 직접 호출하면 `403 Forbidden`으로 거부한다.
3. AC-03 메뉴 제한: 로그인 시 허용된 메뉴와 동작만 노출한다.

## Dev Notes

- Architecture: [architecture.md](../architecture.md) — React+TS admin-frontend / Kotlin Spring Boot admin-backend / 운영 MySQL 공유.
- 불변 규칙: 인증·RBAC 통과 없이는 반환·변경 금지, 자산 변경은 원장 필수, 변경은 감사와 원자적 커밋.
- DB 테이블·컬럼·API 스키마는 구현(4단계)에서 확정한다.

## 관계

- REQ-003 is blocked by REQ-001

## Tasks

- [ ] 구현 착수 시 세분화 (스프린트 4단계)
