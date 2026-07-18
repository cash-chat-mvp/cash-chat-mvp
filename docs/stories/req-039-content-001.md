# Story: REQ-039 진화 정책 관리

Status: Draft

## Story

운영 관리자로서 성장 난이도와 보상을 조정하기 위해 진화 등급 정책을 관리할 수 있다.

- 우선순위: P2
- 주 도메인: 진화·리텐션 콘텐츠
- Epic: 진화·리텐션 콘텐츠 (v.0.2) — Jira CC-501
- Jira Task: CC-545
- 원문 근거: INTAKE-001 US-ADM-CONTENT-001
- 기존 관계: 신규 (v.0.2)
- UI: ADMIN_UI — 와이어프레임: ../ux/wireframes/req-039/index.html

## Acceptance Criteria (원문 전사)

1. AC-01 등급 규칙: 필요 경험치, 기본 성공률, 실패 결과 변경 시 허용 범위와 레벨 연결을 검증한다.
2. AC-02 적용 시점: 유효 시각 도래 시 새 진화 시도부터 적용한다.
3. AC-03 이력: 특정 시도 상세에 당시 적용 정책 버전을 표시한다.

## Dev Notes

- Architecture: [architecture.md](../architecture.md) — React+TS admin-frontend / Kotlin Spring Boot admin-backend / 운영 MySQL 공유.
- 불변 규칙: 인증·RBAC 통과 없이는 반환·변경 금지, 자산 변경은 원장 필수, 변경은 감사와 원자적 커밋.
- DB 테이블·컬럼·API 스키마는 구현(4단계)에서 확정한다.

## 관계

- REQ-039 blocks REQ-040

## Tasks

- [ ] 구현 착수 시 세분화 (스프린트 4단계)
