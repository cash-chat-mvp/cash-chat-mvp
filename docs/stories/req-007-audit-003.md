# Story: REQ-007 위험 작업 확인

Status: Draft

## Story

운영 관리자로서 실수로 인한 피해 방지를 위해 위험한 변경의 대상과 결과를 확인할 수 있다.

- 우선순위: P0
- 주 도메인: 감사·보안
- 에픽: 감사·보안 (v.0.2) — CC-568
- Jira 스토리: CC-588
- 원문 근거: INTAKE-001 US-ADM-AUDIT-003
- 기존 관계: 신규 (v.0.2)
- UI: 공통 모달(이중 확인) — 제재·조정·취소 등 위험 작업 페이지에 포함 (별도 페이지 없음)

## Acceptance Criteria (원문 전사)

1. AC-01 사유 필수: 제재·자산 차감·재처리에서 사유 미입력 시 실행하지 않는다.
2. AC-02 재확인: 최종 실행 전 대상, 현재 값, 변경 값과 예상 결과를 표시한다.
3. AC-03 충돌 방지: 확인 이후 대상 데이터가 변경되면 충돌을 반환하고 최신 값 재확인을 요구한다.

## Tasks

- [ ] AC-01 사유 필수 — 구현·검증 (CC-725)
- [ ] AC-02 재확인 — 구현·검증 (CC-726)
- [ ] AC-03 충돌 방지 — 구현·검증 (CC-727)
- [ ] 관리 화면 구현 (와이어프레임 기준) (CC-728)

## Dev Notes

- Architecture: [architecture.md](../architecture.md) — React+TS admin-frontend / Java Spring Boot admin-backend / 운영 MySQL 공유 / MFA=TOTP.
- 불변 규칙: 인증·RBAC 통과 없이는 반환·변경 금지, 자산 변경은 원장 필수, 변경은 감사와 원자적 커밋.
- DB 테이블·컬럼·API 스키마는 구현(4단계)에서 확정한다.
