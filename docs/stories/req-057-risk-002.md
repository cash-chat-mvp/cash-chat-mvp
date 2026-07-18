# Story: REQ-057 기기·앱 식별자 차단

Status: Draft

## Story

권한 있는 운영 관리자로서 반복적인 악성 이용을 차단하기 위해 기기 또는 앱 식별자를 차단할 수 있다.

- 우선순위: P3
- 주 도메인: 어뷰징·위험 관리
- Epic: 어뷰징·위험 관리 (v.0.2) — Jira CC-505
- Jira Task: CC-563
- 원문 근거: INTAKE-001 US-ADM-RISK-002
- 기존 관계: 신규 (v.0.2)
- UI: ADMIN_UI — 와이어프레임: ../ux/wireframes/req-057/index.html

## Acceptance Criteria (원문 전사)

1. AC-01 차단 등록: 식별자, 기간, 사유 입력 시 차단 목록에 등록하고 영향 회원을 표시한다.
2. AC-02 서비스 적용: 차단 식별자의 신규 가입·인증 요청을 서비스 서버가 정책에 따라 거부한다.
3. AC-03 해제·감사: 과거 기록을 보존하고 변경을 감사한다.

## Dev Notes

- Architecture: [architecture.md](../architecture.md) — React+TS admin-frontend / Kotlin Spring Boot admin-backend / 운영 MySQL 공유.
- 불변 규칙: 인증·RBAC 통과 없이는 반환·변경 금지, 자산 변경은 원장 필수, 변경은 감사와 원자적 커밋.
- DB 테이블·컬럼·API 스키마는 구현(4단계)에서 확정한다.

## 관계

- 없음

## Tasks

- [ ] 구현 착수 시 세분화 (스프린트 4단계)
