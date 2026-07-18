# Story: REQ-056 이상 적립 후보 조회

Status: Draft

## Story

운영 관리자로서 보상 손실을 줄이기 위해 비정상 적립 의심 회원을 조회할 수 있다.

- 우선순위: P2
- 주 도메인: 어뷰징·위험 관리
- Epic: 어뷰징·위험 관리 (v.0.2) — Jira CC-505
- Jira Task: CC-562
- 원문 근거: INTAKE-001 US-ADM-RISK-001
- 기존 관계: 신규 (v.0.2)
- UI: ADMIN_UI — 와이어프레임: ../ux/wireframes/req-056/index.html

## Acceptance Criteria (원문 전사)

1. AC-01 규칙 탐지: 단시간 대량 적립, 동일 기기 다계정, 반복 실패 패턴에서 규칙, 근거 이벤트, 위험 점수를 가진 후보를 생성한다.
2. AC-02 조사 상태: 정상, 조사 중, 확정 분류와 근거를 보존한다.
3. AC-03 자동 제재 금지: 관리자 검토 전 자동 포인트 몰수·영구 정지를 수행하지 않는다.

## Dev Notes

- Architecture: [architecture.md](../architecture.md) — React+TS admin-frontend / Kotlin Spring Boot admin-backend / 운영 MySQL 공유.
- 불변 규칙: 인증·RBAC 통과 없이는 반환·변경 금지, 자산 변경은 원장 필수, 변경은 감사와 원자적 커밋.
- DB 테이블·컬럼·API 스키마는 구현(4단계)에서 확정한다.

## 관계

- 없음

## Tasks

- [ ] 구현 착수 시 세분화 (스프린트 4단계)
