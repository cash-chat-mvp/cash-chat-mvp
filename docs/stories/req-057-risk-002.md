# Story: REQ-057 기기·앱 식별자 차단

Status: Draft

## Story

권한 있는 운영 관리자로서 반복적인 악성 이용을 차단하기 위해 기기 또는 앱 식별자를 차단할 수 있다.

- 우선순위: P3
- 주 도메인: 어뷰징·위험 관리
- 에픽: 어뷰징·위험 관리 (v.0.2) — CC-579
- Jira 스토리: CC-638
- 원문 근거: INTAKE-001 US-ADM-RISK-002
- 기존 관계: 신규 (v.0.2)
- UI: [이상 적립·식별자 차단](../ux/wireframes/pages/29-abuse/index.html)

## Acceptance Criteria (원문 전사)

1. AC-01 차단 등록: 식별자, 기간, 사유 입력 시 차단 목록에 등록하고 영향 회원을 표시한다.
2. AC-02 서비스 적용: 차단 식별자의 신규 가입·인증 요청을 서비스 서버가 정책에 따라 거부한다.
3. AC-03 해제·감사: 과거 기록을 보존하고 변경을 감사한다.

## Tasks

- [ ] AC-01 차단 등록 — 구현·검증 (CC-924)
- [ ] AC-02 서비스 적용 — 구현·검증 (CC-925)
- [ ] AC-03 해제·감사 — 구현·검증 (CC-926)
- [ ] 관리 화면 구현 (와이어프레임 기준) (CC-927)

## Dev Notes

- Architecture: [architecture.md](../architecture.md) — React+TS admin-frontend / Java Spring Boot admin-backend / 운영 MySQL 공유 / MFA=TOTP.
- 불변 규칙: 인증·RBAC 통과 없이는 반환·변경 금지, 자산 변경은 원장 필수, 변경은 감사와 원자적 커밋.
- DB 테이블·컬럼·API 스키마는 구현(4단계)에서 확정한다.
