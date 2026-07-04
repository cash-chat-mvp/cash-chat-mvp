---
id: US-REWARD-001
domain: reward
slug: daily-attendance
status: implemented     # draft | agreed | implemented | deprecated
jira: CC-288            # 혜택존 리뉴얼 BE API (출석체크 포함)
source: docs/features/reward/spec.md
related-domains: [attendance, point, ledger, inventory]
---

# 일일 출석체크

## 스토리

사용자로서, 나는 혜택존 탭에서 하루 1회 출석 도장을 찍어 코인을 받고 싶다.
연속 출석 일차가 쌓이면 7/14/30일 시점에 부가 보상(진화석·확률 부적·보호권)을 함께 받고 싶다.

## 수용 조건 (Acceptance Criteria)

- **AC-01 첫 출석 (원자성)**
  Given 오늘 출석 기록이 없다
  When `POST /api/attendance/check-in`을 호출한다
  Then `attendance_log` 오늘자 1행 INSERT와 코인 적립(`recordTransaction`)이 **단일 `@Transactional`** 안에서 함께 수행된다
  And 연속 일차는 1로 저장되고 1일차 시드 보상(+20코인)이 적립된다
  And 한쪽이라도 실패하면 전체 롤백되어 "도장만 찍히고 코인 없음" 부분 성공이 발생하지 않는다.

- **AC-02 같은 날 중복 출석**
  Given 오늘 이미 출석을 찍었다
  When 같은 날 다시 호출한다
  Then `409 ALREADY_CHECKED_IN`으로 거부하고 추가 행·추가 적립이 없다.

- **AC-03 연속 카운트 증가**
  Given 최근 출석일이 어제(KST)다 → When 오늘 출석 → Then 연속 일차 = 어제 일차 + 1.

- **AC-04 연속 끊김 리셋**
  Given 최근 출석일이 2일 이상 전(KST)이다 → When 오늘 출석 → Then 연속 일차 = 1로 리셋.

- **AC-05 누적 일차 보너스 (7/14/30)**
  Given 누적 출석 일차가 보너스 지급 일차에 도달한다
  When 해당 회차 출석을 찍는다
  Then 코인 외 부가 보상(진화석/확률 부적/보호권) 시드값이 추가 지급되며, 코인·부가 보상 적립은 `attendance_log` 갱신과 동일 트랜잭션이다 (AC-01 원자성 규칙 동일 적용)
  And 본 AC는 누적 1~30일만 정의한다. 31일 이후 사이클은 범위 외.

- **AC-06 캘린더 조회**
  Given 이번 달 7일 출석했다
  When `GET /api/attendance/me`를 호출한다 (`year`·`month`는 둘 다 함께 전달하거나 둘 다 생략; 한쪽만은 400; 생략 시 KST 현재 연·월)
  Then `{ year, month, checkedDays, currentStreak, todayChecked, nextRewardPreview }`를 반환한다.

## 검증 매핑 (Verification)

각 직군은 위 AC ID를 테스트/주석에 역참조한다.
- BE: `attendance` 도메인 통합/단위 테스트 (원자성·연속·멱등)
- FE: 혜택존 출석 화면 상태 테스트
- 시드/API/시퀀스 상세: [source spec](../../features/reward/spec.md) 참조.

## 관련

- 기술 상세(API 계약·시드값·시퀀스 다이어그램): `docs/features/reward/spec.md`
- 기획: [Confluence — 혜택존](https://moneyfactoryslave.atlassian.net/wiki/spaces/FCTC/pages/14909530), `docs/planning/02-rewards-zone.md`
- 용어: [_glossary.md](./_glossary.md)
