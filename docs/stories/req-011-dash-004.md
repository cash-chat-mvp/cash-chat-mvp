# Story: REQ-011 AI 비용 현황

Status: Draft

## Story

비용 관리자로서 AI 운영 비용을 통제하기 위해 모델별 사용량과 예상 비용을 조회할 수 있다.

- 우선순위: P1
- 주 도메인: 대시보드
- 에픽: 대시보드 (v.0.2) — CC-569
- Jira 스토리: CC-592
- 원문 근거: INTAKE-001 US-ADM-DASH-004
- 기존 관계: 신규 (v.0.2)
- UI: [AI 비용 지표](../ux/wireframes/pages/04-dashboard-ai-cost/index.html)

## Acceptance Criteria (원문 전사)

1. AC-01 모델별 사용량: 기간 선택 시 모델별 요청, 입력·출력 토큰, 성공·실패 수를 반환한다.
2. AC-02 비용 계산: 유효 기간 단가로 예상 비용을 계산하고 통화를 표시한다.
3. AC-03 예산 경고: 월간 예산 임계치 도달 시 대시보드에 경고를 표시한다.

## Tasks

- [ ] AC-01 모델별 사용량 — 구현·검증 (CC-741)
- [ ] AC-02 비용 계산 — 구현·검증 (CC-742)
- [ ] AC-03 예산 경고 — 구현·검증 (CC-743)
- [ ] 관리 화면 구현 (와이어프레임 기준) (CC-744)

## Dev Notes

- Architecture: [architecture.md](../architecture.md) — React+TS admin-frontend / Java Spring Boot admin-backend / 운영 MySQL 공유 / MFA=TOTP.
- 불변 규칙: 인증·RBAC 통과 없이는 반환·변경 금지, 자산 변경은 원장 필수, 변경은 감사와 원자적 커밋.
- DB 테이블·컬럼·API 스키마는 구현(4단계)에서 확정한다.
