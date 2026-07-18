# Story: REQ-009 회원 활성 지표

Status: Draft

## Story

마케팅·분석 관리자로서 서비스 이용 추세를 파악하기 위해 가입자와 활성 회원 지표를 조회할 수 있다.

- 우선순위: P1
- 주 도메인: 대시보드
- 에픽: 대시보드 (v.0.2) — CC-569
- Jira 스토리: CC-590
- 원문 근거: INTAKE-001 US-ADM-DASH-002
- 기존 관계: 신규 (v.0.2)
- UI: 2단계에서 와이어프레임 작성 (docs/ux/wireframes/)

## Acceptance Criteria (원문 전사)

1. AC-01 가입자: 날짜 범위 선택 시 신규 회원과 가입 유형별 수를 일 단위로 반환한다.
2. AC-02 활성 회원: 활동 이벤트 정의가 확정된 상태에서 DAU·WAU·MAU 조회 시 중복 제거한 활성 회원 수와 정의를 함께 표시한다.
3. AC-03 비교: 직전 동일 기간 대비 증감 수와 비율을 반환한다.

## Tasks

- [ ] AC-01 가입자 — 구현·검증 (CC-733)
- [ ] AC-02 활성 회원 — 구현·검증 (CC-734)
- [ ] AC-03 비교 — 구현·검증 (CC-735)
- [ ] 관리 화면 구현 (와이어프레임 기준) (CC-736)

## Dev Notes

- Architecture: [architecture.md](../architecture.md) — React+TS admin-frontend / Kotlin Spring Boot admin-backend / 운영 MySQL 공유 / MFA=TOTP.
- 불변 규칙: 인증·RBAC 통과 없이는 반환·변경 금지, 자산 변경은 원장 필수, 변경은 감사와 원자적 커밋.
- DB 테이블·컬럼·API 스키마는 구현(4단계)에서 확정한다.
