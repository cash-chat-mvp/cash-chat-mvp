## UI Task 1: 주간 계산 순수함수 (TDD)
- 상태: ✅
- 변경 파일:
  - apps/frontend/app/src/test/java/com/nomadclub/cashchat/feature/rewards/WeeklyAttendanceTest.kt (신규)
  - apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/rewards/WeeklyAttendance.kt (신규)
- 검증: `./gradlew :app:testDebugUnitTest --tests "*WeeklyAttendanceTest*"` → 테스트 2개 PASS (failures=0, errors=0)
- 인계 메모: weeklyAttendanceCells(displayedYear, displayedMonth, todayYear, todayMonth, todayDay, checkedDays: Set<Int>): List<AttendanceDayCell>; AttendanceDayCell(dayOfMonth, inDisplayedMonth, checked, isToday). java.util.Calendar 기반(일요일 시작), java.time 미사용.
