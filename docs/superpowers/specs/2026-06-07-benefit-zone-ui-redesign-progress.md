## UI Task 1: 주간 계산 순수함수 (TDD)
- 상태: ✅
- 변경 파일:
  - apps/frontend/app/src/test/java/com/nomadclub/cashchat/feature/rewards/WeeklyAttendanceTest.kt (신규)
  - apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/rewards/WeeklyAttendance.kt (신규)
- 검증: `./gradlew :app:testDebugUnitTest --tests "*WeeklyAttendanceTest*"` → 테스트 2개 PASS (failures=0, errors=0)
- 인계 메모: weeklyAttendanceCells(displayedYear, displayedMonth, todayYear, todayMonth, todayDay, checkedDays: Set<Int>): List<AttendanceDayCell>; AttendanceDayCell(dayOfMonth, inDisplayedMonth, checked, isToday). java.util.Calendar 기반(일요일 시작), java.time 미사용.

## UI Task 2: Android 주간 히어로 위젯
- 상태: ✅
- 변경 파일:
  - apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/rewards/AttendanceWidget.kt (전체 교체)
- 검증: `./gradlew :app:compileDebugKotlin -q` → BUILD SUCCESSFUL (exit code 0, 컴파일 에러 없음)
- 인계 메모: AttendanceWidget(state, onCheckIn, modifier) 시그니처 유지, 호출부(BenefitZoneScreen) 변경 불필요. weeklyAttendanceCells/AttendanceDayCell 사용해 일~토 7칸 히어로 그라디언트 카드로 교체(스트릭/보상 미리보기/CTA 버튼 포함).

## UI Task 3: Android 소개카드 + 화면 재구성
- 상태: ✅
- 변경 파일:
  - apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/rewards/BenefitInfoCard.kt (신규)
  - apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/rewards/BenefitZoneScreen.kt (LazyColumn 블록 교체, PhasePlaceholder 삭제, Alignment import 추가)
- 검증: `./gradlew :app:assembleDebug -q` → BUILD SUCCESSFUL (app-debug.apk 재생성 확인)
- 인계 메모: BenefitBadge(label, bg, fg) enum { NEXT, SOON }; BenefitInfoCard(icon, title, badge, description, dimmed, onClick, modifier). 회색 PhasePlaceholder 3개를 BenefitInfoCard 3개(리워드 광고/데일리 미션/TNK 오퍼월)로 교체, 헤더 우측에 코인 칩(🪙 balance, 라운드 배경) 적용. 기존 LaunchedEffect(loadMonthly/rewardEvents/errorMessage 토스트) 그대로 유지.
