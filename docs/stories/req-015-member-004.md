# Story: REQ-015 회원 활동 통합 조회

Status: Draft

## Story

운영·CS 관리자로서 문의와 이상 행동의 원인을 파악하기 위해 회원 활동 이력을 시간순으로 조회할 수 있다.

- 우선순위: P1
- 주 도메인: 회원·제재
- 에픽: 회원·제재 (v.0.2) — CC-570
- Jira 스토리: CC-596
- 원문 근거: INTAKE-001 US-ADM-MEMBER-004
- 기존 관계: 신규 (v.0.2)
- UI: [회원 상세](../ux/wireframes/pages/06-member-detail/index.html)

## Acceptance Criteria (원문 전사)

1. AC-01 타임라인: 로그인, 채팅, 자산, 광고, 출석, 오퍼월, 초대, 룰렛, 진화, 구매를 시간순 반환한다.
2. AC-02 필터: 유형·기간 조건으로 필터링한다.
3. AC-03 원본 연결: 자산 변동 활동은 원본 이벤트, 원장 거래, 변경 후 잔액을 연결한다.

## Tasks

- [ ] AC-01 타임라인 — 구현·검증 (CC-758)
- [ ] AC-02 필터 — 구현·검증 (CC-759)
- [ ] AC-03 원본 연결 — 구현·검증 (CC-760)
- [ ] 관리 화면 구현 (와이어프레임 기준) (CC-761)

## Dev Notes

- Architecture: [architecture.md](../architecture.md) — React+TS admin-frontend / Java Spring Boot admin-backend / 운영 MySQL 공유 / MFA=TOTP.
- 불변 규칙: 인증·RBAC 통과 없이는 반환·변경 금지, 자산 변경은 원장 필수, 변경은 감사와 원자적 커밋.
- DB 테이블·컬럼·API 스키마는 구현(4단계)에서 확정한다.
