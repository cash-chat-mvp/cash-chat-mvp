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

## UI Task 4: iOS 주간 히어로 + 소개카드
- 상태: ⚠️ (빌드 미검증 — 사용자 Xcode)
- 변경 파일:
  - apps/frontend/CashChatIOS/CashChatIOS/BenefitZone/AttendanceViewModel.swift (streak @Published 추가, AttendanceWidgetView 전체를 주간 히어로 카드로 교체, BenefitInfoCardView 신규 추가)
- 검증: Swift 문법검토만 (xcodebuild 불가 환경)
  - vm.streak: `self.streak = Int(s.currentStreak)` — Kotlin `Int` → Swift `Int32` → `Int(...)` 일치 (KMM 공통 변환 패턴, AttendanceModels.kt currentStreak: Int 확인)
  - 기존 daysInMonth/cellColor/dayCell/todayNum/columns/GridItem/LazyVGrid 헬퍼 전부 제거 확인 (grep 결과 없음)
  - 중괄호 균형 확인 (open 38 / close 38)
- 인계 메모: BenefitInfoCardView(icon:title:badge:description:dimmed:) 시그니처는 Task 5(RewardsView) 호출부와 일치해야 함. weekCells()는 vm.month 기준으로 checkedDays를 매칭하므로 월 경계(주가 두 달에 걸칠 때) 표시 로직 주의.

## UI Task 5: iOS RewardsView 목업 제거+재구성
- 상태: ⚠️ (빌드 미검증 — 사용자 Xcode)
- 변경 파일:
  - apps/frontend/CashChatIOS/CashChatIOS/ContentView.swift (MissionItem struct 삭제, RewardsView body 전체 교체: 약 1041~1162줄 → 1041~1089줄)
- 검증:
  - 잔재 grep: `grep -n "MissionItem\|claimedIDs\|targetPoints\|appState.addPoints\|\.missions" ContentView.swift` → 유일하게 773줄 `appState.addPoints(30)`만 남음(이는 ChatView의 RewardAdModalView onComplete 콜백, RewardsView와 무관 — 정상)
  - RewardsView 내부 `@EnvironmentObject appState` 선언/사용 0건 확인
  - BenefitInfoCardView 시그니처(icon:title:badge:description:dimmed:) 및 Badge.next/.soon 일치 확인 (AttendanceViewModel.swift:151~181)
  - 중괄호/들여쓰기 육안 검토 — 정상 (struct 닫힘 1089줄, 다음 ShopCategory enum과 경계 정상)
- 인계 메모: 사용자가 Xcode에서 빌드 후 (1) 헤더 코인 칩이 attendanceVM.balance를 정상 표시하는지, (2) AttendanceWidgetView/BenefitInfoCardView 3장이 정상 렌더링되는지, (3) 탭 전환 애니메이션(animateIn) 동작 확인 필요.

---

## ✅ UI 개편 완료 요약 (Task 6)

### 완료
- Android: 주간 7칸 계산 순수함수(TDD) + 출석 주간 히어로 위젯 + 소개 카드(BenefitInfoCard) + 화면 재구성(코인 칩 헤더, PhasePlaceholder 제거). — ✅ 빌드+테스트
- iOS: AttendanceWidgetView 주간 히어로 개편 + BenefitInfoCardView + vm.streak + RewardsView 기존 목업(MissionItem/커피교환/미션 ForEach) 전면 제거. — ⚠️ Swift 작성 완료, 빌드 미검증

### 최종 검증 (컨트롤러 직접)
- `./gradlew :app:testDebugUnitTest --tests "*WeeklyAttendanceTest*"` → PASS (2)
- `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL (APK)
- `./gradlew :shared:testDebugUnitTest` → PASS (6/6, 무변경 회귀 확인)

### 미결 / 인계 (PENDING)
1. **iOS Xcode 검증 (사용자)**: 기존 2파일 수정만(신규 파일 없음 → pbxproj 멤버십 변경 불필요). `embedAndSignAppleFrameworkForXcode`(JAVA_HOME=Android Studio JBR) 후 Xcode 빌드 → 런타임: 리워드 탭에서 (1) 주간 히어로(일~토, 오늘 강조, 출석 도장→완료), (2) 코인 칩, (3) 소개 카드 3장(배지/흐림), (4) 기존 목업 사라짐 확인.
2. **Android 런타임 (사용자)**: 출석 주간 뷰 + 소개 카드 탭 "곧 만나요" 토스트.
3. 월 경계 주간뷰: 다른 달 칸 중립 표시(단순화 유지).
