# Story: REQ-013 회원 상세 조회

Status: Draft

## Story

운영·CS 관리자로서 회원 상태를 종합적으로 파악하기 위해 회원 상세정보를 조회할 수 있다.

- 우선순위: P0
- 주 도메인: 회원·제재
- 에픽: 회원·제재 (v.0.2) — CC-570
- Jira 스토리: CC-594
- 원문 근거: INTAKE-001 US-ADM-MEMBER-002
- 기존 관계: 신규 (v.0.2)
- UI: [회원 상세](../ux/wireframes/pages/06-member-detail/index.html)

## Acceptance Criteria (원문 전사)

1. AC-01 기본·자산 정보: 가입 정보, 상태, 포인트, 사용·예약 Energy, 진화 등급·경험치를 반환한다.
2. AC-02 활동 요약: 채팅, 광고, 출석, 오퍼월, 초대, 룰렛, 구매 누적 수를 반환한다.
3. AC-03 미생성 데이터: 미생성 도메인 데이터는 전체 요청 실패 없이 미생성으로 표시한다.
4. AC-04 조회 감사: 개인정보 포함 상세 조회 시 대상, 조회자, 범위, 시각을 감사한다.

## Tasks

- [ ] AC-01 기본·자산 정보 — 구현·검증 (CC-749)
- [ ] AC-02 활동 요약 — 구현·검증 (CC-750)
- [ ] AC-03 미생성 데이터 — 구현·검증 (CC-751)
- [ ] AC-04 조회 감사 — 구현·검증 (CC-752)
- [ ] 관리 화면 구현 (와이어프레임 기준) (CC-753)

## Dev Notes

- Architecture: [architecture.md](../architecture.md) — React+TS admin-frontend / Kotlin Spring Boot admin-backend / 운영 MySQL 공유 / MFA=TOTP.
- 불변 규칙: 인증·RBAC 통과 없이는 반환·변경 금지, 자산 변경은 원장 필수, 변경은 감사와 원자적 커밋.
- DB 테이블·컬럼·API 스키마는 구현(4단계)에서 확정한다.
