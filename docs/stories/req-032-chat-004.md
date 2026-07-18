# Story: REQ-032 AI 모델 라우팅 정책

Status: Draft

## Story

운영 관리자로서 품질과 비용의 균형을 조정하기 위해 AI 모델 라우팅 정책을 관리할 수 있다.

- 우선순위: P2
- 주 도메인: 채팅·AI 운영
- Epic: 채팅·AI 운영 (v.0.2) — Jira CC-499
- Jira Task: CC-538
- 원문 근거: INTAKE-001 US-ADM-CHAT-004
- 기존 관계: 신규 (v.0.2)
- UI: ADMIN_UI — 와이어프레임: ../ux/wireframes/req-032/index.html

## Acceptance Criteria (원문 전사)

1. AC-01 정책 변경: 티어별 활성 상태, 확률, 일일 cap 변경 시 유효 시각부터 적용한다.
2. AC-02 합계 검증: 확률 합계와 허용 범위를 검증한다.
3. AC-03 롤백: 새 변경 버전으로 이전 값을 재적용하고 이력을 보존한다.

## Dev Notes

- Architecture: [architecture.md](../architecture.md) — React+TS admin-frontend / Kotlin Spring Boot admin-backend / 운영 MySQL 공유.
- 불변 규칙: 인증·RBAC 통과 없이는 반환·변경 금지, 자산 변경은 원장 필수, 변경은 감사와 원자적 커밋.
- DB 테이블·컬럼·API 스키마는 구현(4단계)에서 확정한다.

## 관계

- 없음

## Tasks

- [ ] 구현 착수 시 세분화 (스프린트 4단계)
