# Story: REQ-030 채팅 성공·실패 분석

Status: Draft

## Story

운영 관리자로서 채팅 품질 저하를 파악하기 위해 요청의 성공·실패 현황을 조회할 수 있다.

- 우선순위: P1
- 주 도메인: 채팅·AI 운영
- 에픽: 채팅·AI 운영 (v.0.2) — CC-573
- Jira 스토리: CC-611
- 원문 근거: INTAKE-001 US-ADM-CHAT-002
- 기존 관계: 신규 (v.0.2)
- UI: [채팅 성공·실패 분석](../ux/wireframes/pages/16-chat-analytics/index.html)

## Acceptance Criteria (원문 전사)

1. AC-01 상태 집계: 전체 요청, 정상 완료, 스트리밍 실패, 취소를 구분한다.
2. AC-02 모델별 분석: 모델별 요청 수, 성공률, 실패율을 반환한다.
3. AC-03 자산 처리 연결: 메시지 상태와 관련 Energy·포인트·경험치 거래를 연결한다.

## Tasks

- [ ] AC-01 상태 집계 — 구현·검증 (CC-818)
- [ ] AC-02 모델별 분석 — 구현·검증 (CC-819)
- [ ] AC-03 자산 처리 연결 — 구현·검증 (CC-820)
- [ ] 관리 화면 구현 (와이어프레임 기준) (CC-821)

## Dev Notes

- Architecture: [architecture.md](../architecture.md) — React+TS admin-frontend / Kotlin Spring Boot admin-backend / 운영 MySQL 공유 / MFA=TOTP.
- 불변 규칙: 인증·RBAC 통과 없이는 반환·변경 금지, 자산 변경은 원장 필수, 변경은 감사와 원자적 커밋.
- DB 테이블·컬럼·API 스키마는 구현(4단계)에서 확정한다.
