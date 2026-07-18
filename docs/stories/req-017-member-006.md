# Story: REQ-017 로그인·기기 이력

Status: Draft

## Story

운영 관리자로서 계정 탈취와 다계정 사용을 조사하기 위해 회원의 로그인·기기 이력을 조회할 수 있다.

- 우선순위: P1
- 주 도메인: 회원·제재
- 에픽: 회원·제재 (v.0.2) — CC-570
- Jira 스토리: CC-598
- 원문 근거: INTAKE-001 US-ADM-MEMBER-006
- 기존 관계: 신규 (v.0.2)
- UI: [회원 상세](../ux/wireframes/pages/06-member-detail/index.html)

## Acceptance Criteria (원문 전사)

1. AC-01 로그인 기록: 로그인·토큰 갱신 종료 시 회원, 제공자, 성공 여부, 시각, IP, 기기 식별 정보를 기록한다.
2. AC-02 회원별 조회: 최신 기록부터 페이징 반환한다.
3. AC-03 기기별 탐색: 기기 기준 검색 시 연결 회원과 최근 이용 시각을 반환한다.

## Tasks

- [ ] AC-01 로그인 기록 — 구현·검증 (CC-766)
- [ ] AC-02 회원별 조회 — 구현·검증 (CC-767)
- [ ] AC-03 기기별 탐색 — 구현·검증 (CC-768)
- [ ] 관리 화면 구현 (와이어프레임 기준) (CC-769)

## Dev Notes

- Architecture: [architecture.md](../architecture.md) — React+TS admin-frontend / Kotlin Spring Boot admin-backend / 운영 MySQL 공유 / MFA=TOTP.
- 불변 규칙: 인증·RBAC 통과 없이는 반환·변경 금지, 자산 변경은 원장 필수, 변경은 감사와 원자적 커밋.
- DB 테이블·컬럼·API 스키마는 구현(4단계)에서 확정한다.
