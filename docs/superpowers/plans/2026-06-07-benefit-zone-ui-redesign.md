# 혜택존 UI 개편 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 혜택존을 출석 주간(7일) 히어로 + 미구현 섹션 소개 카드(우선순위 순)로 Android·iOS 동일하게 개편하고, iOS의 기존 목업 미션을 제거한다.

**Architecture:** shared/BE는 변경하지 않는다. 출석 주간 7칸은 UI 레이어에서 `java.util.Calendar`(Android)/`Calendar`(iOS)로 계산한다. Android는 주간 계산을 순수 함수로 분리해 JVM 단위 테스트(TDD)로 검증하고, 위젯·화면은 빌드+수동 검증한다. iOS는 동일 디자인을 SwiftUI로 구현하되 빌드·런타임은 사용자 Xcode에서 검증한다.

**Tech Stack:** Jetpack Compose (Material3), Kotlin, JUnit4(app `src/test`), SwiftUI, java.util.Calendar/Foundation Calendar.

**관련 spec:** `docs/superpowers/specs/2026-06-07-benefit-zone-ui-redesign-design.md`

**진행 로그 규약:** 각 task 완료 시 서브에이전트는 `docs/superpowers/specs/2026-06-07-benefit-zone-ui-redesign-progress.md`에 항목을 **append**(한국어, append-only): Task 번호/제목, 상태(✅/⚠️/❌), 변경·추가 파일, 검증 결과, 다음 task 인계 메모.

**공통 작업 디렉토리:** `apps/frontend/`.

**커밋 규약:** Conventional Commits, 한국어. ⚠️ commitlint `subject-case`가 제목을 PascalCase 영문 식별자(예: `AttendanceWidget`, `RewardsView`)로 **시작**하면 거부함 → 제목은 한국어로 시작할 것. app 모듈은 product flavor 없음 → 빌드는 `:app:assembleDebug` / `:app:compileDebugKotlin`. iOS Gradle 빌드 시 `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`(이 머신은 `java_home -v 21` 실패). 커밋 끝에 `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>` trailer.

---

## 파일 구조

**Android (`apps/frontend/app`):**
- Create `src/main/java/com/nomadclub/cashchat/feature/rewards/WeeklyAttendance.kt` — 주간 7칸 순수 계산 함수 + `AttendanceDayCell`
- Create `src/test/java/com/nomadclub/cashchat/feature/rewards/WeeklyAttendanceTest.kt` — JVM 단위 테스트
- Modify `src/main/java/com/nomadclub/cashchat/feature/rewards/AttendanceWidget.kt` — 월 그리드 → 주간 히어로
- Create `src/main/java/com/nomadclub/cashchat/feature/rewards/BenefitInfoCard.kt` — 미구현 소개 카드 컴포넌트
- Modify `src/main/java/com/nomadclub/cashchat/feature/rewards/BenefitZoneScreen.kt` — 헤더/카드 재구성

**iOS (`apps/frontend/CashChatIOS/CashChatIOS`):**
- Modify `BenefitZone/AttendanceViewModel.swift` — `AttendanceWidgetView` 주간 히어로로 개편 + `BenefitInfoCardView` 추가(같은 파일 → 신규 pbxproj 멤버십 불필요)
- Modify `ContentView.swift` — `RewardsView`(1050–1162) 목업 제거 + 신규 구성, `MissionItem` struct(1041–1048) 제거

**shared / BE:** 변경 없음.

---

## Task 1: Android 주간 계산 순수 함수 (TDD)

**Files:**
- Create: `apps/frontend/app/src/test/java/com/nomadclub/cashchat/feature/rewards/WeeklyAttendanceTest.kt`
- Create: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/rewards/WeeklyAttendance.kt`

- [ ] **Step 1: 실패 테스트 작성**

```kotlin
package com.nomadclub.cashchat.feature.rewards

import org.junit.Assert.assertEquals
import org.junit.Test

class WeeklyAttendanceTest {

    // 2026-05-20은 수요일 → 그 주(일~토)는 5/17~5/23
    @Test
    fun `이번 주 7칸은 일요일부터 토요일까지이며 오늘과 완료를 표시한다`() {
        val cells = weeklyAttendanceCells(
            displayedYear = 2026, displayedMonth = 5,
            todayYear = 2026, todayMonth = 5, todayDay = 20,
            checkedDays = setOf(17, 18, 19, 20),
        )
        assertEquals(listOf(17, 18, 19, 20, 21, 22, 23), cells.map { it.dayOfMonth })
        assertEquals(List(7) { true }, cells.map { it.inDisplayedMonth })
        assertEquals(listOf(true, true, true, true, false, false, false), cells.map { it.checked })
        assertEquals(listOf(false, false, false, true, false, false, false), cells.map { it.isToday })
    }

    // 2026-05-01은 금요일 → 그 주는 4/26~5/2 (월 경계)
    @Test
    fun `월 경계 주에서는 다른 달 칸을 중립으로 표시한다`() {
        val cells = weeklyAttendanceCells(
            displayedYear = 2026, displayedMonth = 5,
            todayYear = 2026, todayMonth = 5, todayDay = 1,
            checkedDays = setOf(1),
        )
        assertEquals(listOf(26, 27, 28, 29, 30, 1, 2), cells.map { it.dayOfMonth })
        // 4월 칸(26~30)은 표시월(5월) 아님 → inDisplayedMonth=false, checked=false
        assertEquals(listOf(false, false, false, false, false, true, true), cells.map { it.inDisplayedMonth })
        assertEquals(listOf(false, false, false, false, false, true, false), cells.map { it.checked })
        assertEquals(listOf(false, false, false, false, false, true, false), cells.map { it.isToday })
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "*WeeklyAttendanceTest*"`
Expected: FAIL — `weeklyAttendanceCells` 미존재(컴파일 에러).

- [ ] **Step 3: 구현 작성**

```kotlin
package com.nomadclub.cashchat.feature.rewards

import java.util.Calendar

/** 출석 주간 뷰의 한 칸. */
data class AttendanceDayCell(
    val dayOfMonth: Int,
    val inDisplayedMonth: Boolean,
    val checked: Boolean,
    val isToday: Boolean,
)

/**
 * 오늘이 포함된 주(일요일~토요일) 7칸을 계산한다.
 * 표시 월(displayedYear/Month)에 속하는 칸만 checkedDays 로 완료 판정하고,
 * 다른 달 칸은 inDisplayedMonth=false(중립)로 둔다.
 * java.time 미사용(desugaring 미설정으로 런타임 크래시 회피) — java.util.Calendar 사용.
 */
fun weeklyAttendanceCells(
    displayedYear: Int,
    displayedMonth: Int,
    todayYear: Int,
    todayMonth: Int,
    todayDay: Int,
    checkedDays: Set<Int>,
): List<AttendanceDayCell> {
    val cal = Calendar.getInstance()
    cal.clear()
    cal.set(todayYear, todayMonth - 1, todayDay)
    // 주 시작을 일요일로 맞춘다 (DAY_OF_WEEK: SUNDAY=1 .. SATURDAY=7)
    val offset = cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
    cal.add(Calendar.DAY_OF_MONTH, -offset)
    return (0 until 7).map {
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        val inMonth = y == displayedYear && m == displayedMonth
        val cell = AttendanceDayCell(
            dayOfMonth = d,
            inDisplayedMonth = inMonth,
            checked = inMonth && checkedDays.contains(d),
            isToday = y == todayYear && m == todayMonth && d == todayDay,
        )
        cal.add(Calendar.DAY_OF_MONTH, 1)
        cell
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "*WeeklyAttendanceTest*"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/rewards/WeeklyAttendance.kt apps/frontend/app/src/test/java/com/nomadclub/cashchat/feature/rewards/WeeklyAttendanceTest.kt
git commit -m "feat(rewards): 출석 주간 7칸 계산 순수 함수 추가 (TDD)"
```

---

## Task 2: Android 출석 주간 히어로 위젯

**Files:**
- Modify: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/rewards/AttendanceWidget.kt` (전체 교체)

- [ ] **Step 1: AttendanceWidget 전체 교체**

기존 파일 내용을 아래로 **전부 교체**한다(시그니처 `AttendanceWidget(state, onCheckIn, modifier)` 유지 — Task 3/BenefitZoneScreen 호출부 불변).

```kotlin
package com.nomadclub.cashchat.feature.rewards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nomadclub.cashchat.shared.attendance.AttendanceUiState
import java.util.Calendar

private val HeroStart = Color(0xFF5C6BFA)
private val HeroEnd = Color(0xFF8466FA)
private val Accent = Color(0xFFFFB800)
private val White = Color(0xFFFFFFFF)
private val DayLabels = listOf("일", "월", "화", "수", "목", "금", "토")

@Composable
fun AttendanceWidget(
    state: AttendanceUiState,
    onCheckIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cal = Calendar.getInstance()
    val ty = cal.get(Calendar.YEAR)
    val tm = cal.get(Calendar.MONTH) + 1
    val td = cal.get(Calendar.DAY_OF_MONTH)
    val dispYear = if (state.year > 0) state.year else ty
    val dispMonth = if (state.month in 1..12) state.month else tm
    val cells = weeklyAttendanceCells(dispYear, dispMonth, ty, tm, td, state.checkedDays.toSet())

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(HeroStart, HeroEnd)))
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🔥 ${state.currentStreak}일 연속 출석", color = White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            Text("${dispMonth}월", color = White.copy(alpha = 0.8f), fontSize = 12.sp)
        }
        Spacer(Modifier.height(14.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            cells.forEachIndexed { idx, cell ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        DayLabels[idx],
                        color = if (cell.isToday) Accent else White.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        fontWeight = if (cell.isToday) FontWeight.ExtraBold else FontWeight.Medium,
                    )
                    Spacer(Modifier.height(6.dp))
                    val dotColor = when {
                        cell.checked -> White
                        cell.isToday -> Accent
                        else -> White.copy(alpha = 0.18f)
                    }
                    val contentColor = when {
                        cell.checked -> HeroStart
                        cell.isToday -> Color(0xFF1B1B2A)
                        else -> White
                    }
                    Box(
                        modifier = Modifier.size(30.dp).clip(CircleShape).background(dotColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (cell.checked) "✓" else "${cell.dayOfMonth}",
                            color = contentColor,
                            fontSize = if (cell.checked) 14.sp else 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        state.nextReward?.let { r ->
            val bonus = r.bonusItems.joinToString(" ") { "📦 ${it.itemCode} ${it.quantity}개" }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(White.copy(alpha = 0.16f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text("🎁 오늘 보상 🪙+${r.coin}  $bonus", color = White, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(12.dp))
        }

        Button(
            onClick = onCheckIn,
            enabled = !state.todayChecked && !state.isCheckingIn,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(99.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Accent,
                contentColor = Color(0xFF1B1B2A),
                disabledContainerColor = White.copy(alpha = 0.25f),
                disabledContentColor = White.copy(alpha = 0.7f),
            ),
        ) {
            Text(if (state.todayChecked) "오늘 출석 완료" else "출석 도장 찍기", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }
    }
}
```

> 참고: `12.5.sp`는 유효한 Kotlin(`Double.sp` 확장). 컴파일 에러 시 `13.sp`로 대체. `width` import는 미사용 시 제거 가능(경고는 무방).

- [ ] **Step 2: 빌드 확인**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/rewards/AttendanceWidget.kt
git commit -m "feat(rewards): 출석 위젯을 주간 7일 히어로로 개편"
```

---

## Task 3: Android 소개 카드 + 혜택존 화면 재구성

**Files:**
- Create: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/rewards/BenefitInfoCard.kt`
- Modify: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/rewards/BenefitZoneScreen.kt`

- [ ] **Step 1: BenefitInfoCard 작성**

```kotlin
package com.nomadclub.cashchat.feature.rewards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class BenefitBadge(val label: String, val bg: Color, val fg: Color) {
    NEXT("곧 출시", Color(0xFFE3F0FF), Color(0xFF2D6FE0)),
    SOON("준비중", Color(0xFFF0EEF8), Color(0xFF9A95AD)),
}

/** 미구현 혜택 섹션의 소개 카드(가짜 데이터 없음). */
@Composable
fun BenefitInfoCard(
    icon: String,
    title: String,
    badge: BenefitBadge,
    description: String,
    dimmed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = if (dimmed) 0.72f else 1f))
            .border(1.dp, Color(0xFFF0EEF8), RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1B1B2A))
            Spacer(Modifier.width(8.dp))
            Text(
                badge.label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = badge.fg,
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(badge.bg)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(description, fontSize = 12.5.sp, color = Color(0xFF6B6979))
    }
}
```

> import `Arrangement`, `Spacer`, `Modifier.height`은 `androidx.compose.foundation.layout.*`. 누락 시 추가. `12.5.sp` 에러 시 `13.sp`.

- [ ] **Step 2: BenefitZoneScreen 교체**

`BenefitZoneScreen.kt`의 `LazyColumn` 내부 item들과 하단 `PhasePlaceholder`를 아래로 교체한다. 헤더는 코인 칩 스타일로 다듬고, placeholder 3개를 `BenefitInfoCard`로 교체. 상단 import에 추가: `import androidx.compose.foundation.shape.RoundedCornerShape`(이미 있음), `import androidx.compose.foundation.background`(있음). 토스트용 `context` 재사용.

`LazyColumn { ... }` 블록을 다음으로 교체:

```kotlin
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text("혜택존", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    "🪙 $balance",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFFB07C00),
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(Color(0xFFFFF7E6))
                        .padding(horizontal = 11.dp, vertical = 5.dp),
                )
            }
        }
        item { AttendanceWidget(state = state, onCheckIn = store::checkIn) }
        item {
            BenefitInfoCard(
                icon = "📺", title = "리워드 광고", badge = BenefitBadge.NEXT,
                description = "광고 1회 시청 → 🪙+40 코인 · 하루 10회까지",
                dimmed = false,
                onClick = { Toast.makeText(context, "곧 만나요!", Toast.LENGTH_SHORT).show() },
            )
        }
        item {
            BenefitInfoCard(
                icon = "🎯", title = "데일리 미션", badge = BenefitBadge.SOON,
                description = "매일 바뀌는 3가지 미션을 완료하고 코인 적립",
                dimmed = true,
                onClick = { Toast.makeText(context, "곧 만나요!", Toast.LENGTH_SHORT).show() },
            )
        }
        item {
            BenefitInfoCard(
                icon = "🎮", title = "TNK 오퍼월", badge = BenefitBadge.SOON,
                description = "앱 설치·설문 참여로 대량 코인 (최대 🪙+1,500)",
                dimmed = true,
                onClick = { Toast.makeText(context, "곧 만나요!", Toast.LENGTH_SHORT).show() },
            )
        }
    }
}
```

그리고 파일 하단의 기존 `@Composable private fun PhasePlaceholder(...)` 함수를 **삭제**한다. 상단 import 중 `import androidx.compose.foundation.shape.RoundedCornerShape`가 없으면 추가.

- [ ] **Step 3: 빌드 확인**

Run: `./gradlew :app:assembleDebug -q`
Expected: BUILD SUCCESSFUL (APK 생성).

- [ ] **Step 4: Commit**

```bash
git add apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/rewards/
git commit -m "feat(rewards): 혜택존 화면 재구성 (소개 카드 + 코인 칩 헤더)"
```

---

## Task 4: iOS 주간 히어로 + 소개 카드

**Files:**
- Modify: `apps/frontend/CashChatIOS/CashChatIOS/BenefitZone/AttendanceViewModel.swift`

> ⚠️ Xcode 빌드는 이 환경에서 불가 → Swift 문법/타입만 신중 검토. 빌드·런타임은 사용자. `AttendanceViewModel.swift`는 이미 Xcode 타깃에 포함되어 있어 신규 멤버십 불필요.

- [ ] **Step 1: AttendanceWidgetView를 주간 히어로로 교체 + BenefitInfoCardView 추가**

`AttendanceViewModel.swift`의 기존 `struct AttendanceWidgetView: View { ... }` 전체를 아래로 교체하고, 파일 끝에 `BenefitInfoCardView`를 추가한다. (`AttendanceViewModel` 클래스 부분은 그대로 둔다.)

```swift
struct AttendanceWidgetView: View {
    @ObservedObject var vm: AttendanceViewModel

    private let dayLabels = ["일", "월", "화", "수", "목", "금", "토"]
    private let heroStart = Color(red: 0.36, green: 0.42, blue: 0.98)
    private let heroEnd = Color(red: 0.52, green: 0.40, blue: 0.98)
    private let accent = Color(red: 1.0, green: 0.72, blue: 0.0)

    private struct DayCell {
        let dayOfMonth: Int
        let inMonth: Bool
        let checked: Bool
        let isToday: Bool
    }

    /// 오늘이 포함된 주(일~토) 7칸. vm.month 기준 checkedDays로 완료 판정.
    private func weekCells() -> [DayCell] {
        var cal = Calendar(identifier: .gregorian)
        cal.firstWeekday = 1 // Sunday
        let now = Date()
        let todayComps = cal.dateComponents([.year, .month, .day], from: now)
        let dispMonth = vm.month > 0 ? vm.month : (todayComps.month ?? 1)
        guard let weekInterval = cal.dateInterval(of: .weekOfYear, for: now) else { return [] }
        var result: [DayCell] = []
        for i in 0..<7 {
            guard let date = cal.date(byAdding: .day, value: i, to: weekInterval.start) else { continue }
            let c = cal.dateComponents([.year, .month, .day], from: date)
            let m = c.month ?? 0
            let d = c.day ?? 0
            let inMonth = (m == dispMonth)
            result.append(DayCell(
                dayOfMonth: d,
                inMonth: inMonth,
                checked: inMonth && vm.checkedDays.contains(d),
                isToday: (c.year == todayComps.year && m == todayComps.month && d == todayComps.day)
            ))
        }
        return result
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text("🔥 \(vm.streak)일 연속 출석").font(.system(size: 15, weight: .heavy)).foregroundStyle(.white)
                Spacer()
                Text("\(vm.month)월").font(.system(size: 12)).foregroundStyle(.white.opacity(0.8))
            }

            HStack(spacing: 0) {
                ForEach(Array(weekCells().enumerated()), id: \.offset) { idx, cell in
                    VStack(spacing: 6) {
                        Text(dayLabels[idx])
                            .font(.system(size: 10, weight: cell.isToday ? .heavy : .medium))
                            .foregroundStyle(cell.isToday ? accent : .white.opacity(0.7))
                        ZStack {
                            Circle().fill(cell.checked ? Color.white : (cell.isToday ? accent : Color.white.opacity(0.18)))
                                .frame(width: 30, height: 30)
                            Text(cell.checked ? "✓" : "\(cell.dayOfMonth)")
                                .font(.system(size: cell.checked ? 14 : 11, weight: .bold))
                                .foregroundStyle(cell.checked ? heroStart : (cell.isToday ? Color(red:0.1,green:0.1,blue:0.16) : .white))
                        }
                    }
                    .frame(maxWidth: .infinity)
                }
            }

            Text("🎁 오늘 보상 🪙+\(vm.nextRewardCoin)")
                .font(.system(size: 12.5))
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 12).padding(.vertical, 10)
                .background(Color.white.opacity(0.16))
                .clipShape(RoundedRectangle(cornerRadius: 12))

            Button(action: { vm.checkIn() }) {
                Text(vm.todayChecked ? "오늘 출석 완료" : "출석 도장 찍기")
                    .font(.system(size: 15, weight: .heavy))
                    .foregroundStyle(Color(red: 0.1, green: 0.1, blue: 0.16))
                    .frame(maxWidth: .infinity, minHeight: 50)
                    .background(vm.todayChecked ? Color.white.opacity(0.25) : accent)
                    .clipShape(Capsule())
            }
            .disabled(vm.todayChecked)
        }
        .padding(18)
        .background(LinearGradient(colors: [heroStart, heroEnd], startPoint: .topLeading, endPoint: .bottomTrailing))
        .clipShape(RoundedRectangle(cornerRadius: 22))
    }
}

struct BenefitInfoCardView: View {
    enum Badge { case next, soon
        var text: String { self == .next ? "곧 출시" : "준비중" }
        var bg: Color { self == .next ? Color(red:0.89,green:0.94,blue:1.0) : Color(red:0.94,green:0.93,blue:0.97) }
        var fg: Color { self == .next ? Color(red:0.18,green:0.44,blue:0.88) : Color(red:0.60,green:0.58,blue:0.68) }
    }
    let icon: String
    let title: String
    let badge: Badge
    let description: String
    let dimmed: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 7) {
            HStack(spacing: 8) {
                Text(icon).font(.system(size: 18))
                Text(title).font(.system(size: 15, weight: .bold)).foregroundStyle(Color(red:0.1,green:0.1,blue:0.16))
                Text(badge.text).font(.system(size: 10, weight: .bold)).foregroundStyle(badge.fg)
                    .padding(.horizontal, 8).padding(.vertical, 2)
                    .background(badge.bg).clipShape(Capsule())
            }
            Text(description).font(.system(size: 12.5)).foregroundStyle(Color(red:0.42,green:0.41,blue:0.47))
        }
        .padding(15)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.systemBackground))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(Color(red:0.94,green:0.93,blue:0.97), lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .opacity(dimmed ? 0.72 : 1.0)
    }
}
```

이 변경은 `vm.streak`(연속 출석)을 사용한다. `AttendanceViewModel`에 `streak`가 없으므로 **Step 2에서 추가**한다.

- [ ] **Step 2: AttendanceViewModel에 streak 추가**

`AttendanceViewModel` 클래스의 `@Published` 프로퍼티에 추가:
```swift
@Published var streak: Int = 0
```
그리고 `collectAttendance` 콜백 안(기존 `self.month = Int(s.month)` 부근)에 추가:
```swift
self.streak = Int(s.currentStreak)
```
(`AttendanceUiState.currentStreak`는 `Int` → Swift `Int32` → `Int(...)`.)

- [ ] **Step 3: 문법 검토 (빌드 불가)**

xcodebuild 실행하지 말 것. 검토 항목: `weekCells()`의 옵셔널 처리, `ForEach(Array(...).enumerated(), id: \.offset)`, `12.5` CGFloat 리터럴 유효성, `vm.streak` 추가 일치, 중괄호 균형. 진행 로그에 "빌드·런타임은 사용자 Xcode 검증 PENDING" 명시.

- [ ] **Step 4: Commit**

```bash
git add apps/frontend/CashChatIOS/CashChatIOS/BenefitZone/AttendanceViewModel.swift
git commit -m "feat(ios): 출석 위젯 주간 히어로 개편 + 혜택 소개 카드 추가"
```

---

## Task 5: iOS RewardsView 목업 제거 + 재구성

**Files:**
- Modify: `apps/frontend/CashChatIOS/CashChatIOS/ContentView.swift`

> ⚠️ 빌드 불가 환경. 정확한 라인 기준으로 최소 침습 편집. 편집 전 해당 영역을 다시 읽어 라인이 이동했는지 확인할 것(이전 task가 같은 파일을 안 건드렸다면 1041–1162 유지).

- [ ] **Step 1: MissionItem struct 제거**

`ContentView.swift`의 `private struct MissionItem: Identifiable { ... }` (대략 1041–1048)를 **삭제**한다. (RewardsView 외에 사용처 없음 — `grep -n "MissionItem" ContentView.swift`로 확인 후 삭제.)

- [ ] **Step 2: RewardsView 본문 교체**

`private struct RewardsView: View { ... }` (대략 1050–1162) 전체를 아래로 교체한다:

```swift
    private struct RewardsView: View {
        @StateObject private var attendanceVM = AttendanceViewModel()
        @State private var animateIn = false

        var body: some View {
            ScrollView {
                VStack(spacing: 12) {
                    HStack {
                        Text("혜택존").font(.system(size: 22, weight: .heavy))
                        Spacer()
                        Text("🪙 \(attendanceVM.balance)")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(Color(red: 0.69, green: 0.49, blue: 0.0))
                            .padding(.horizontal, 11).padding(.vertical, 5)
                            .background(Color(red: 1.0, green: 0.97, blue: 0.90))
                            .clipShape(Capsule())
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 8)

                    AttendanceWidgetView(vm: attendanceVM)
                        .padding(.horizontal, 16)

                    BenefitInfoCardView(icon: "📺", title: "리워드 광고", badge: .next,
                        description: "광고 1회 시청 → 🪙+40 코인 · 하루 10회까지", dimmed: false)
                        .padding(.horizontal, 16)
                    BenefitInfoCardView(icon: "🎯", title: "데일리 미션", badge: .soon,
                        description: "매일 바뀌는 3가지 미션을 완료하고 코인 적립", dimmed: true)
                        .padding(.horizontal, 16)
                    BenefitInfoCardView(icon: "🎮", title: "TNK 오퍼월", badge: .soon,
                        description: "앱 설치·설문 참여로 대량 코인 (최대 🪙+1,500)", dimmed: true)
                        .padding(.horizontal, 16)
                }
                .padding(.bottom, 16)
            }
            .background(Color(.systemGroupedBackground))
            .opacity(animateIn ? 1 : 0)
            .offset(y: animateIn ? 0 : 14)
            .animation(.easeOut(duration: 0.34), value: animateIn)
            .onAppear {
                animateIn = false
                attendanceVM.load()
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.03) {
                    withAnimation(.easeOut(duration: 0.34)) { animateIn = true }
                }
            }
            .onDisappear { animateIn = false }
        }
    }
```

제거되는 것: `appState`(EnvironmentObject) 의존, `claimedIDs`, `targetPoints`, `missions` 배열, 커피교환 progress 카드, mission `ForEach`. (헤더 코인은 `attendanceVM.balance` 사용.)

- [ ] **Step 3: 문법 검토 (빌드 불가)**

`grep -n "MissionItem\|RewardsView\|appState.addPoints\|claimedIDs\|targetPoints" ContentView.swift`로 잔재가 없는지 확인. RewardsView가 더 이상 `appState`를 안 쓰면 `@EnvironmentObject appState` 제거됨을 확인. 중괄호 균형/인덴트 확인. 진행 로그에 사용자 Xcode 검증 PENDING 명시.

- [ ] **Step 4: Commit**

```bash
git add apps/frontend/CashChatIOS/CashChatIOS/ContentView.swift
git commit -m "feat(ios): 리워드 탭 기존 목업 제거 및 혜택존 신규 구성으로 교체"
```

---

## Task 6: 통합 검증 + 진행 로그 마감

- [ ] **Step 1: Android 단위 테스트 + 빌드**

Run: `./gradlew :app:testDebugUnitTest --tests "*WeeklyAttendanceTest*"` → PASS
Run: `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL

- [ ] **Step 2: shared 회귀 확인 (무변경이지만 확인)**

Run: `./gradlew :shared:testDebugUnitTest` → PASS (6/6)

- [ ] **Step 3: 진행 로그 최종 요약 append**

`docs/superpowers/specs/2026-06-07-benefit-zone-ui-redesign-progress.md`에 완료 요약 append: 완료 Task, Android 빌드/테스트 결과, iOS는 Swift 작성 완료·빌드/런타임 사용자 Xcode PENDING(신규 파일 없음 → pbxproj 멤버십 변경 불필요, 기존 2파일 수정만), 월 경계 단순화 유지.

- [ ] **Step 4: 마무리**

`superpowers:finishing-a-development-branch`로 머지/PR 옵션 결정(커밋·머지는 사용자 승인 후).

---

## Self-Review 메모 (작성자 확인 완료)

- **Spec 커버리지:** §1 화면구성→Task2/3/4/5, §2 주간로직→Task1(+위젯), §3 미구현카드→Task3/4, §4 변경범위→Task2~5, §5 검증→Task6. 누락 없음.
- **타입 일관성:** Android `weeklyAttendanceCells(...)`/`AttendanceDayCell`/`BenefitBadge`/`BenefitInfoCard` 시그니처가 Task1↔2↔3에서 일치. iOS `AttendanceWidgetView(vm:)`/`BenefitInfoCardView(icon:title:badge:description:dimmed:)`/`vm.streak`가 Task4↔5에서 일치(`streak`는 Task4 Step2에서 추가).
- **Placeholder 없음:** 모든 코드 스텝에 실제 코드 포함.
