# 혜택존 PR2 — 출석 도메인 (BE-2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 일일 출석 체크인 API(`POST /api/attendance/check-in`, `GET /api/attendance/me`)를 구현한다 — 중복 일자 거부, 연속(streak) 일차 계산, 누적 일차 기반 코인 보상(7/14/30일 마일스톤), 월간 캘린더 조회. 코인 적립은 BE-1의 멱등 `recordTransaction`을 통해 출석 로그 갱신과 단일 트랜잭션으로 원자적으로 수행한다.

**Architecture:** `domain/attendance/`에 출석 로그(`attendance_log`)와 데이터 주도 보상 테이블(`attendance_reward` + `attendance_reward_bonus` 시드)을 둔다. `AttendanceService.checkIn`은 단일 `@Transactional` 안에서 (1) 당일 중복 검사 → (2) streak 계산 → (3) 보상 lookup → (4) `attendance_log` INSERT → (5) `recordTransaction(key="attendance:{userId}:{date}")` 코인 적립을 수행한다. 보상은 `day_count` 키로 조회하며 `day_count=0` 행이 "기본 일일 보상"(비마일스톤·31일+ 폴백)이다. **부가 보상 아이템(EVO_STONE 등)은 정의·미리보기만 제공하고 실제 인벤토리 지급은 하지 않는다**(인벤토리 도메인 미존재 — Shop/Evolution 도메인 등장 시 연결).

**Tech Stack:** Kotlin 1.9.25, Spring Boot 3.5.11, Spring Data JPA, Flyway(V3), H2(dev, MySQL 모드), MySQL 8(prod·test), Kotest 5.9.1 + mockito-kotlin, Testcontainers MySQL, MockMvc(`@WebMvcTest`).

---

## 결정 사항 / 가정 (Documented Decisions)

1. **범위:** CC-288 백엔드의 PR2 = BE-2 출석 도메인. BE-1(`recordTransaction`)·Flyway는 PR1에서 이미 dev에 머지됨(이 브랜치 base에 포함).
2. **부가 보상 아이템:** 코인만 실제 지급. 아이템은 `attendance_reward_bonus`에 **정의**하고 API 응답의 `bonusItems`/`nextRewardPreview`로 **미리보기만** 노출. 실제 인벤토리 적립은 인벤토리/아이템 도메인(Shop/Evolution) 도입 시로 연기. (spec '범위 외 consume'과 일관)
3. **보상 모델:** `attendance_reward(day_count PK, coin)` + `attendance_reward_bonus(day_count, item_code, quantity)`. `day_count=0` = 기본 일일 보상(코인 20, 보너스 없음). lookup은 `findByDayCount(streak) ?: findByDayCount(0)`. 따라서 1~6·8~13·15~29·**31+** 일차는 모두 기본 20코인, 7/14/30일만 오버라이드 + 보너스. (31일+ 사이클 정책은 spec상 미결 — 기본 폴백이 Phase 1 임시 동작)
4. **시드/한도 값:** spec 부록 가설값(1~6:+20, 7:+50+EVO_STONE×1, 14:+100+EVO_STONE×2·LUCK_CHARM×1, 30:+300+PROTECT_TICKET×1).
5. **KST 처리:** 컨트롤러가 `LocalDate.now(ZoneId.of("Asia/Seoul"))`를 계산해 서비스에 `today`로 주입(테스트 결정성 확보). 멱등성 키 `attendance:{userId}:{yyyy-MM-dd}`의 날짜도 이 KST 날짜.
6. **포인트 행 선결:** `recordTransaction`은 `user_points` 행이 있어야 한다(없으면 `IllegalStateException`/500). 이 행은 회원가입 시 `UserPointService.ensureInitialized`로 생성되므로 인증된 사용자는 항상 보유. 통합 테스트는 setup에서 `ensureInitialized` 호출.
7. **에러 코드:** 당일 중복 → 409 `ALREADY_CHECKED_IN`. `year`/`month` 한쪽만 전달 또는 month 범위 밖 → 400 `INVALID_ATTENDANCE_QUERY`.

---

## File Structure

**신규 — 엔티티/리포지토리**
- `domain/attendance/persistence/entity/AttendanceLog.kt` — 일별 출석 로그(BaseEntity 상속, 감사 컬럼 보유)
- `domain/attendance/persistence/entity/AttendanceReward.kt` — 보상 시드(day_count PK, coin) — BaseEntity 미상속
- `domain/attendance/persistence/entity/AttendanceRewardBonus.kt` — 부가 보상 정의 — BaseEntity 미상속
- `domain/attendance/persistence/repository/AttendanceLogRepository.kt`
- `domain/attendance/persistence/repository/AttendanceRewardRepository.kt`
- `domain/attendance/persistence/repository/AttendanceRewardBonusRepository.kt`

**신규 — 서비스/모델**
- `domain/attendance/service/AttendanceResult.kt` — `CheckInResult`, `MonthlyAttendance`, `RewardView`, `BonusItem` (도메인 결과 타입)
- `domain/attendance/service/AttendanceService.kt`

**신규 — 예외/웹**
- `domain/attendance/exception/AlreadyCheckedInException.kt`
- `domain/attendance/exception/InvalidAttendanceQueryException.kt`
- `domain/attendance/web/exception/AttendanceExceptionHandler.kt`
- `domain/attendance/web/response/CheckInResponse.kt`
- `domain/attendance/web/response/MonthlyAttendanceResponse.kt` (RewardPreviewResponse, BonusItemResponse 포함)
- `domain/attendance/web/controller/AttendanceController.kt`

**신규 — 마이그레이션**
- `src/main/resources/db/migration/V3__attendance.sql` — 3개 테이블 + 시드

**신규 — 테스트**
- `src/test/.../domain/attendance/service/AttendanceServiceTest.kt` — checkIn/getMonthly 단위(mock)
- `src/test/.../domain/attendance/web/controller/AttendanceControllerTest.kt` — `@WebMvcTest`
- `src/test/.../domain/attendance/persistence/AttendanceIntegrationTest.kt` — Testcontainers MySQL, 실제 시드 + 적립

---

## Task 1: 출석 엔티티 + 보상 시드 엔티티 + 마이그레이션(V3)

**Files:**
- Create: `domain/attendance/persistence/entity/AttendanceLog.kt`, `AttendanceReward.kt`, `AttendanceRewardBonus.kt`
- Create: `src/main/resources/db/migration/V3__attendance.sql`

- [ ] **Step 1: `AttendanceLog.kt`**

```kotlin
package com.wnl.cashchat.api.domain.attendance.persistence.entity

import com.wnl.cashchat.api.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate

/**
 * 사용자의 일자별 출석 도장 1건. (user_id, check_in_date) 유니크로 동일 일자 중복을 차단한다.
 * streakDayCount = 해당 출석 시점의 연속 출석 일차.
 */
@Entity
@Table(
    name = "attendance_log",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_attendance_log_user_date", columnNames = ["user_id", "check_in_date"])
    ]
)
class AttendanceLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "check_in_date", nullable = false)
    val checkInDate: LocalDate,

    @Column(name = "streak_day_count", nullable = false)
    val streakDayCount: Int,
) : BaseEntity()
```

- [ ] **Step 2: `AttendanceReward.kt`** (시드/설정 테이블 — BaseEntity 미상속)

```kotlin
package com.wnl.cashchat.api.domain.attendance.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 누적 일차별 코인 보상 시드. day_count=0 은 "기본 일일 보상"(비마일스톤·31일+ 폴백).
 */
@Entity
@Table(name = "attendance_reward")
class AttendanceReward(
    @Id
    @Column(name = "day_count", nullable = false)
    val dayCount: Int = 0,

    @Column(nullable = false)
    val coin: Long = 0,
)
```

- [ ] **Step 3: `AttendanceRewardBonus.kt`** (부가 보상 정의 — 지급 아님, 미리보기용)

```kotlin
package com.wnl.cashchat.api.domain.attendance.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table

/**
 * 마일스톤 일차의 부가 보상 아이템 정의. Phase 1에서는 정의·미리보기 용도이며 실제 인벤토리 지급은 하지 않는다.
 */
@Entity
@Table(
    name = "attendance_reward_bonus",
    indexes = [Index(name = "idx_attendance_reward_bonus_day", columnList = "day_count")]
)
class AttendanceRewardBonus(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "day_count", nullable = false)
    val dayCount: Int,

    @Column(name = "item_code", nullable = false, length = 50)
    val itemCode: String,

    @Column(nullable = false)
    val quantity: Int,
)
```

- [ ] **Step 4: `V3__attendance.sql`**

```sql
-- V3: 출석 도메인 — 로그 + 보상 시드 (Phase 1 가설값)

CREATE TABLE attendance_log (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    check_in_date    DATE         NOT NULL,
    streak_day_count INT          NOT NULL,
    created_at       TIMESTAMP(6) NOT NULL,
    updated_at       TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_attendance_log_user_date UNIQUE (user_id, check_in_date),
    CONSTRAINT fk_attendance_log_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE attendance_reward (
    day_count INT    NOT NULL,
    coin      BIGINT NOT NULL,
    PRIMARY KEY (day_count)
);

CREATE TABLE attendance_reward_bonus (
    id        BIGINT      NOT NULL AUTO_INCREMENT,
    day_count INT         NOT NULL,
    item_code VARCHAR(50) NOT NULL,
    quantity  INT         NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_attendance_reward_bonus_day FOREIGN KEY (day_count) REFERENCES attendance_reward (day_count)
);
CREATE INDEX idx_attendance_reward_bonus_day ON attendance_reward_bonus (day_count);

-- 시드: day_count=0 은 기본 일일 보상(코인 20, 보너스 없음). 7/14/30 마일스톤만 오버라이드.
INSERT INTO attendance_reward (day_count, coin) VALUES (0, 20), (7, 50), (14, 100), (30, 300);

INSERT INTO attendance_reward_bonus (day_count, item_code, quantity) VALUES
    (7,  'EVO_STONE',      1),
    (14, 'EVO_STONE',      2),
    (14, 'LUCK_CHARM',     1),
    (30, 'PROTECT_TICKET', 1);
```

- [ ] **Step 5: validate 확인** — 엔티티↔V3 정합성을 실제 MySQL 8에서 확인

Run: `cd apps/backend && ./gradlew test --tests "*ChatPersistenceIntegrationTest"`
Expected: PASS (전체 컨텍스트가 Testcontainers MySQL에서 V1+V2+V3 적용 후 Hibernate validate 통과). 실패 시 컬럼명/타입(`check_in_date` DATE↔LocalDate, `day_count` INT↔Int, 감사 컬럼 유무)을 reconcile. `attendance_reward`/`attendance_reward_bonus`는 BaseEntity 미상속이므로 created_at/updated_at이 **없어야** 한다.

- [ ] **Step 6: 커밋** (한글 메시지)

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/attendance/persistence apps/backend/src/main/resources/db/migration/V3__attendance.sql
git commit -m "feat(attendance): 출석 로그·보상 시드 엔티티 및 V3 마이그레이션 추가" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: 리포지토리

**Files:**
- Create: `domain/attendance/persistence/repository/AttendanceLogRepository.kt`, `AttendanceRewardRepository.kt`, `AttendanceRewardBonusRepository.kt`

- [ ] **Step 1: `AttendanceLogRepository.kt`**

```kotlin
package com.wnl.cashchat.api.domain.attendance.persistence.repository

import com.wnl.cashchat.api.domain.attendance.persistence.entity.AttendanceLog
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface AttendanceLogRepository : JpaRepository<AttendanceLog, Long> {
    fun existsByUserIdAndCheckInDate(userId: Long, checkInDate: LocalDate): Boolean

    fun findTopByUserIdOrderByCheckInDateDesc(userId: Long): AttendanceLog?

    fun findByUserIdAndCheckInDateBetween(
        userId: Long,
        start: LocalDate,
        end: LocalDate,
    ): List<AttendanceLog>
}
```

- [ ] **Step 2: `AttendanceRewardRepository.kt`**

```kotlin
package com.wnl.cashchat.api.domain.attendance.persistence.repository

import com.wnl.cashchat.api.domain.attendance.persistence.entity.AttendanceReward
import org.springframework.data.jpa.repository.JpaRepository

interface AttendanceRewardRepository : JpaRepository<AttendanceReward, Int> {
    fun findByDayCount(dayCount: Int): AttendanceReward?
}
```

- [ ] **Step 3: `AttendanceRewardBonusRepository.kt`**

```kotlin
package com.wnl.cashchat.api.domain.attendance.persistence.repository

import com.wnl.cashchat.api.domain.attendance.persistence.entity.AttendanceRewardBonus
import org.springframework.data.jpa.repository.JpaRepository

interface AttendanceRewardBonusRepository : JpaRepository<AttendanceRewardBonus, Long> {
    fun findByDayCount(dayCount: Int): List<AttendanceRewardBonus>
}
```

- [ ] **Step 4: 컴파일 확인**

Run: `cd apps/backend && ./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: 커밋**

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/attendance/persistence/repository
git commit -m "feat(attendance): 출석 로그·보상 리포지토리 추가" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: 도메인 결과 타입 + AttendanceService (TDD)

**Files:**
- Create: `domain/attendance/service/AttendanceResult.kt`
- Create: `domain/attendance/exception/AlreadyCheckedInException.kt`
- Test: `src/test/.../domain/attendance/service/AttendanceServiceTest.kt`
- Create: `domain/attendance/service/AttendanceService.kt`

- [ ] **Step 1: 결과 타입 `AttendanceResult.kt`**

```kotlin
package com.wnl.cashchat.api.domain.attendance.service

/** 부가 보상 아이템 정의(미리보기용; 실제 인벤토리 지급 아님). */
data class BonusItem(
    val itemCode: String,
    val quantity: Int,
)

/** 특정 누적 일차의 보상 미리보기. */
data class RewardView(
    val dayCount: Int,
    val coin: Long,
    val bonusItems: List<BonusItem>,
)

/** 체크인 결과. */
data class CheckInResult(
    val awardedCoin: Long,
    val streakDayCount: Int,
    val bonusItems: List<BonusItem>,
    val nextReward: RewardView,
)

/** 월간 출석 조회 결과. */
data class MonthlyAttendance(
    val year: Int,
    val month: Int,
    val checkedDays: List<Int>,
    val currentStreak: Int,
    val todayChecked: Boolean,
    val nextReward: RewardView,
)
```

- [ ] **Step 2: 예외 `AlreadyCheckedInException.kt`**

```kotlin
package com.wnl.cashchat.api.domain.attendance.exception

class AlreadyCheckedInException(
    message: String = "Already checked in today",
) : RuntimeException(message)
```

- [ ] **Step 3: 실패하는 단위 테스트 `AttendanceServiceTest.kt`**

```kotlin
package com.wnl.cashchat.api.domain.attendance.service

import com.wnl.cashchat.api.domain.attendance.exception.AlreadyCheckedInException
import com.wnl.cashchat.api.domain.attendance.persistence.entity.AttendanceLog
import com.wnl.cashchat.api.domain.attendance.persistence.entity.AttendanceReward
import com.wnl.cashchat.api.domain.attendance.persistence.entity.AttendanceRewardBonus
import com.wnl.cashchat.api.domain.attendance.persistence.repository.AttendanceLogRepository
import com.wnl.cashchat.api.domain.attendance.persistence.repository.AttendanceRewardBonusRepository
import com.wnl.cashchat.api.domain.attendance.persistence.repository.AttendanceRewardRepository
import com.wnl.cashchat.api.domain.point.persistence.entity.PointTransactionReason
import com.wnl.cashchat.api.domain.point.service.UserPointService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate

class AttendanceServiceTest : FunSpec({
    lateinit var attendanceLogRepository: AttendanceLogRepository
    lateinit var attendanceRewardRepository: AttendanceRewardRepository
    lateinit var attendanceRewardBonusRepository: AttendanceRewardBonusRepository
    lateinit var userPointService: UserPointService
    lateinit var service: AttendanceService

    val userId = 1L
    val today = LocalDate.of(2026, 5, 30)

    beforeTest {
        attendanceLogRepository = mock()
        attendanceRewardRepository = mock()
        attendanceRewardBonusRepository = mock()
        userPointService = mock()
        service = AttendanceService(
            attendanceLogRepository,
            attendanceRewardRepository,
            attendanceRewardBonusRepository,
            userPointService,
        )
        // 기본 일일 보상 + 비마일스톤 보너스 없음 (대부분의 테스트가 공유)
        whenever(attendanceRewardRepository.findByDayCount(0)).thenReturn(AttendanceReward(dayCount = 0, coin = 20))
        whenever(attendanceRewardBonusRepository.findByDayCount(any())).thenReturn(emptyList())
    }

    test("first check-in: streak 1, base 20 coins, log saved, recordTransaction called with KST key") {
        whenever(attendanceLogRepository.existsByUserIdAndCheckInDate(userId, today)).thenReturn(false)
        whenever(attendanceLogRepository.findTopByUserIdOrderByCheckInDateDesc(userId)).thenReturn(null)
        whenever(attendanceRewardRepository.findByDayCount(1)).thenReturn(null) // → falls back to base(0)

        val result = service.checkIn(userId, today)

        result.streakDayCount shouldBe 1
        result.awardedCoin shouldBe 20L
        result.bonusItems shouldBe emptyList()
        verify(attendanceLogRepository).save(argThat<AttendanceLog> {
            this.userId == userId && checkInDate == today && streakDayCount == 1
        })
        verify(userPointService).recordTransaction(
            eq(userId), eq(20L), eq(PointTransactionReason.ATTENDANCE), eq("attendance:1:2026-05-30"),
        )
    }

    test("duplicate same-day check-in throws and writes nothing") {
        whenever(attendanceLogRepository.existsByUserIdAndCheckInDate(userId, today)).thenReturn(true)

        shouldThrow<AlreadyCheckedInException> { service.checkIn(userId, today) }

        verify(attendanceLogRepository, never()).save(any())
        verify(userPointService, never()).recordTransaction(any(), any(), any(), any())
    }

    test("consecutive day increments streak") {
        whenever(attendanceLogRepository.existsByUserIdAndCheckInDate(userId, today)).thenReturn(false)
        whenever(attendanceLogRepository.findTopByUserIdOrderByCheckInDateDesc(userId)).thenReturn(
            AttendanceLog(userId = userId, checkInDate = today.minusDays(1), streakDayCount = 3)
        )
        whenever(attendanceRewardRepository.findByDayCount(4)).thenReturn(null)

        val result = service.checkIn(userId, today)

        result.streakDayCount shouldBe 4
        result.awardedCoin shouldBe 20L
    }

    test("gap resets streak to 1") {
        whenever(attendanceLogRepository.existsByUserIdAndCheckInDate(userId, today)).thenReturn(false)
        whenever(attendanceLogRepository.findTopByUserIdOrderByCheckInDateDesc(userId)).thenReturn(
            AttendanceLog(userId = userId, checkInDate = today.minusDays(3), streakDayCount = 9)
        )
        whenever(attendanceRewardRepository.findByDayCount(1)).thenReturn(null)

        val result = service.checkIn(userId, today)

        result.streakDayCount shouldBe 1
    }

    test("day 7 milestone awards 50 coins plus EVO_STONE bonus") {
        whenever(attendanceLogRepository.existsByUserIdAndCheckInDate(userId, today)).thenReturn(false)
        whenever(attendanceLogRepository.findTopByUserIdOrderByCheckInDateDesc(userId)).thenReturn(
            AttendanceLog(userId = userId, checkInDate = today.minusDays(1), streakDayCount = 6)
        )
        whenever(attendanceRewardRepository.findByDayCount(7)).thenReturn(AttendanceReward(dayCount = 7, coin = 50))
        whenever(attendanceRewardBonusRepository.findByDayCount(7)).thenReturn(
            listOf(AttendanceRewardBonus(dayCount = 7, itemCode = "EVO_STONE", quantity = 1))
        )

        val result = service.checkIn(userId, today)

        result.streakDayCount shouldBe 7
        result.awardedCoin shouldBe 50L
        result.bonusItems shouldBe listOf(BonusItem("EVO_STONE", 1))
        verify(userPointService).recordTransaction(
            eq(userId), eq(50L), eq(PointTransactionReason.ATTENDANCE), eq("attendance:1:2026-05-30"),
        )
    }

    test("getMonthly returns calendar, active streak, todayChecked, and next reward preview") {
        val logs = (1..7).map {
            AttendanceLog(userId = userId, checkInDate = LocalDate.of(2026, 5, it), streakDayCount = it)
        }
        whenever(attendanceLogRepository.findByUserIdAndCheckInDateBetween(userId, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)))
            .thenReturn(logs)
        // latest = day-7 log on 2026-05-07; today = 2026-05-30 → streak broken (currentStreak 0)
        whenever(attendanceLogRepository.findTopByUserIdOrderByCheckInDateDesc(userId)).thenReturn(logs.last())
        whenever(attendanceRewardRepository.findByDayCount(1)).thenReturn(null)

        val result = service.getMonthly(userId, 2026, 5, today)

        result.year shouldBe 2026
        result.month shouldBe 5
        result.checkedDays shouldBe (1..7).toList()
        result.currentStreak shouldBe 0
        result.todayChecked shouldBe false
        result.nextReward.dayCount shouldBe 1
        result.nextReward.coin shouldBe 20L
    }

    test("getMonthly reports active streak and todayChecked when latest log is today") {
        val log = AttendanceLog(userId = userId, checkInDate = today, streakDayCount = 5)
        whenever(attendanceLogRepository.findByUserIdAndCheckInDateBetween(userId, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)))
            .thenReturn(listOf(log))
        whenever(attendanceLogRepository.findTopByUserIdOrderByCheckInDateDesc(userId)).thenReturn(log)
        whenever(attendanceRewardRepository.findByDayCount(6)).thenReturn(null)

        val result = service.getMonthly(userId, 2026, 5, today)

        result.currentStreak shouldBe 5
        result.todayChecked shouldBe true
        result.checkedDays shouldBe listOf(30)
        result.nextReward.dayCount shouldBe 6
    }
})
```

- [ ] **Step 4: 테스트 실패 확인**

Run: `cd apps/backend && ./gradlew test --tests "*AttendanceServiceTest"`
Expected: 컴파일 실패 — `AttendanceService`가 아직 없음.

- [ ] **Step 5: `AttendanceService.kt` 구현**

```kotlin
package com.wnl.cashchat.api.domain.attendance.service

import com.wnl.cashchat.api.domain.attendance.exception.AlreadyCheckedInException
import com.wnl.cashchat.api.domain.attendance.persistence.entity.AttendanceLog
import com.wnl.cashchat.api.domain.attendance.persistence.repository.AttendanceLogRepository
import com.wnl.cashchat.api.domain.attendance.persistence.repository.AttendanceRewardBonusRepository
import com.wnl.cashchat.api.domain.attendance.persistence.repository.AttendanceRewardRepository
import com.wnl.cashchat.api.domain.point.persistence.entity.PointTransactionReason
import com.wnl.cashchat.api.domain.point.service.UserPointService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * 일일 출석 도장과 누적 일차 보상.
 *
 * checkIn 은 단일 @Transactional 안에서 출석 로그 INSERT 와 코인 적립(recordTransaction)을 함께 수행해
 * "도장만 찍히고 코인 없음" 같은 부분 성공을 차단한다. 코인 적립의 멱등성/동시성은 BE-1 recordTransaction 이 보장한다.
 *
 * 전제: 인증된 사용자는 회원가입 시 UserPointService.ensureInitialized 로 user_points 행이 생성돼 있다.
 */
@Service
class AttendanceService(
    private val attendanceLogRepository: AttendanceLogRepository,
    private val attendanceRewardRepository: AttendanceRewardRepository,
    private val attendanceRewardBonusRepository: AttendanceRewardBonusRepository,
    private val userPointService: UserPointService,
) {
    @Transactional
    fun checkIn(userId: Long, today: LocalDate): CheckInResult {
        if (attendanceLogRepository.existsByUserIdAndCheckInDate(userId, today)) {
            throw AlreadyCheckedInException()
        }

        val latest = attendanceLogRepository.findTopByUserIdOrderByCheckInDateDesc(userId)
        val streak = if (latest != null && latest.checkInDate == today.minusDays(1)) {
            latest.streakDayCount + 1
        } else {
            1
        }

        val reward = rewardView(streak)

        attendanceLogRepository.save(
            AttendanceLog(userId = userId, checkInDate = today, streakDayCount = streak)
        )

        userPointService.recordTransaction(
            userId = userId,
            delta = reward.coin,
            reason = PointTransactionReason.ATTENDANCE,
            idempotencyKey = "attendance:$userId:$today",
        )

        return CheckInResult(
            awardedCoin = reward.coin,
            streakDayCount = streak,
            bonusItems = reward.bonusItems,
            nextReward = rewardView(streak + 1),
        )
    }

    @Transactional(readOnly = true)
    fun getMonthly(userId: Long, year: Int, month: Int, today: LocalDate): MonthlyAttendance {
        val start = LocalDate.of(year, month, 1)
        val end = start.plusMonths(1).minusDays(1)

        val logs = attendanceLogRepository.findByUserIdAndCheckInDateBetween(userId, start, end)
        val checkedDays = logs.map { it.checkInDate.dayOfMonth }.sorted()

        val latest = attendanceLogRepository.findTopByUserIdOrderByCheckInDateDesc(userId)
        val currentStreak = if (latest != null &&
            (latest.checkInDate == today || latest.checkInDate == today.minusDays(1))
        ) {
            latest.streakDayCount
        } else {
            0
        }
        val todayChecked = latest?.checkInDate == today

        return MonthlyAttendance(
            year = year,
            month = month,
            checkedDays = checkedDays,
            currentStreak = currentStreak,
            todayChecked = todayChecked,
            nextReward = rewardView(currentStreak + 1),
        )
    }

    /**
     * 누적 일차 보상 조회. 해당 일차 행이 없으면 기본 일일 보상(day_count=0)으로 폴백한다
     * (비마일스톤 일차 및 31일+ Phase 1 임시 동작). 보너스 아이템은 정의/미리보기용.
     */
    private fun rewardView(dayCount: Int): RewardView {
        val reward = attendanceRewardRepository.findByDayCount(dayCount)
            ?: attendanceRewardRepository.findByDayCount(BASE_DAY_COUNT)
            ?: throw IllegalStateException("Base attendance reward (day_count=$BASE_DAY_COUNT) is not seeded")
        val bonuses = attendanceRewardBonusRepository.findByDayCount(dayCount)
            .map { BonusItem(itemCode = it.itemCode, quantity = it.quantity) }
        return RewardView(dayCount = dayCount, coin = reward.coin, bonusItems = bonuses)
    }

    private companion object {
        private const val BASE_DAY_COUNT = 0
    }
}
```

- [ ] **Step 6: 단위 테스트 통과**

Run: `cd apps/backend && ./gradlew test --tests "*AttendanceServiceTest"`
Expected: PASS (7 tests).

- [ ] **Step 7: 커밋**

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/attendance/service apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/attendance/exception apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/attendance/service
git commit -m "feat(attendance): 출석 체크인·월간 조회 서비스 구현" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: 예외 핸들러 + 응답 DTO + 컨트롤러

**Files:**
- Create: `domain/attendance/exception/InvalidAttendanceQueryException.kt`
- Create: `domain/attendance/web/exception/AttendanceExceptionHandler.kt`
- Create: `domain/attendance/web/response/CheckInResponse.kt`, `MonthlyAttendanceResponse.kt`
- Create: `domain/attendance/web/controller/AttendanceController.kt`

- [ ] **Step 1: `InvalidAttendanceQueryException.kt`**

```kotlin
package com.wnl.cashchat.api.domain.attendance.exception

class InvalidAttendanceQueryException(
    message: String,
) : RuntimeException(message)
```

- [ ] **Step 2: `AttendanceExceptionHandler.kt`** (도메인 스코프)

```kotlin
package com.wnl.cashchat.api.domain.attendance.web.exception

import com.wnl.cashchat.api.common.web.response.ErrorResponse
import com.wnl.cashchat.api.domain.attendance.exception.AlreadyCheckedInException
import com.wnl.cashchat.api.domain.attendance.exception.InvalidAttendanceQueryException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice(basePackages = ["com.wnl.cashchat.api.domain.attendance"])
class AttendanceExceptionHandler {

    @ExceptionHandler(AlreadyCheckedInException::class)
    fun handleAlreadyCheckedIn(e: AlreadyCheckedInException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse("ALREADY_CHECKED_IN", e.message ?: "Already checked in today"))

    @ExceptionHandler(InvalidAttendanceQueryException::class)
    fun handleInvalidQuery(e: InvalidAttendanceQueryException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse("INVALID_ATTENDANCE_QUERY", e.message ?: "Invalid attendance query"))
}
```

- [ ] **Step 3: `CheckInResponse.kt`**

```kotlin
package com.wnl.cashchat.api.domain.attendance.web.response

import com.wnl.cashchat.api.domain.attendance.service.CheckInResult

data class BonusItemResponse(
    val itemCode: String,
    val quantity: Int,
)

data class RewardPreviewResponse(
    val dayCount: Int,
    val coin: Long,
    val bonusItems: List<BonusItemResponse>,
) {
    companion object {
        fun from(view: com.wnl.cashchat.api.domain.attendance.service.RewardView): RewardPreviewResponse =
            RewardPreviewResponse(
                dayCount = view.dayCount,
                coin = view.coin,
                bonusItems = view.bonusItems.map { BonusItemResponse(it.itemCode, it.quantity) },
            )
    }
}

data class CheckInResponse(
    val awardedCoin: Long,
    val streakDayCount: Int,
    val bonusItems: List<BonusItemResponse>,
    val nextRewardPreview: RewardPreviewResponse,
) {
    companion object {
        fun from(result: CheckInResult): CheckInResponse =
            CheckInResponse(
                awardedCoin = result.awardedCoin,
                streakDayCount = result.streakDayCount,
                bonusItems = result.bonusItems.map { BonusItemResponse(it.itemCode, it.quantity) },
                nextRewardPreview = RewardPreviewResponse.from(result.nextReward),
            )
    }
}
```

- [ ] **Step 4: `MonthlyAttendanceResponse.kt`**

```kotlin
package com.wnl.cashchat.api.domain.attendance.web.response

import com.wnl.cashchat.api.domain.attendance.service.MonthlyAttendance

data class MonthlyAttendanceResponse(
    val year: Int,
    val month: Int,
    val checkedDays: List<Int>,
    val currentStreak: Int,
    val todayChecked: Boolean,
    val nextRewardPreview: RewardPreviewResponse,
) {
    companion object {
        fun from(result: MonthlyAttendance): MonthlyAttendanceResponse =
            MonthlyAttendanceResponse(
                year = result.year,
                month = result.month,
                checkedDays = result.checkedDays,
                currentStreak = result.currentStreak,
                todayChecked = result.todayChecked,
                nextRewardPreview = RewardPreviewResponse.from(result.nextReward),
            )
    }
}
```

- [ ] **Step 5: `AttendanceController.kt`**

```kotlin
package com.wnl.cashchat.api.domain.attendance.web.controller

import com.wnl.cashchat.api.domain.attendance.exception.InvalidAttendanceQueryException
import com.wnl.cashchat.api.domain.attendance.service.AttendanceService
import com.wnl.cashchat.api.domain.attendance.web.response.CheckInResponse
import com.wnl.cashchat.api.domain.attendance.web.response.MonthlyAttendanceResponse
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.ZoneId

@RestController
@RequestMapping("/api/attendance")
class AttendanceController(
    private val attendanceService: AttendanceService,
) {
    @PostMapping("/check-in")
    fun checkIn(authentication: Authentication): CheckInResponse =
        CheckInResponse.from(
            attendanceService.checkIn(authentication.userId(), LocalDate.now(KST))
        )

    @GetMapping("/me")
    fun getMonthly(
        authentication: Authentication,
        @RequestParam(required = false) year: Int?,
        @RequestParam(required = false) month: Int?,
    ): MonthlyAttendanceResponse {
        val today = LocalDate.now(KST)
        if ((year == null) != (month == null)) {
            throw InvalidAttendanceQueryException("year and month must be provided together")
        }
        val resolvedYear = year ?: today.year
        val resolvedMonth = month ?: today.monthValue
        if (resolvedMonth !in 1..12) {
            throw InvalidAttendanceQueryException("month must be between 1 and 12")
        }
        return MonthlyAttendanceResponse.from(
            attendanceService.getMonthly(authentication.userId(), resolvedYear, resolvedMonth, today)
        )
    }

    private fun Authentication.userId(): Long =
        principal as? Long ?: throw IllegalArgumentException("Invalid authenticated principal")

    private companion object {
        private val KST = ZoneId.of("Asia/Seoul")
    }
}
```

- [ ] **Step 6: 컴파일 확인**

Run: `cd apps/backend && ./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: 커밋**

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/attendance/web apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/attendance/exception/InvalidAttendanceQueryException.kt
git commit -m "feat(attendance): 출석 API 컨트롤러·응답 DTO·예외 핸들러 추가" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: 컨트롤러 WebMvc 테스트 (TDD)

**Files:**
- Test: `src/test/.../domain/attendance/web/controller/AttendanceControllerTest.kt`

`ChatControllerTest`의 `@WebMvcTest` + `addFilters=false` + `.principal(...)` 패턴을 따른다.

- [ ] **Step 1: 테스트 작성**

```kotlin
package com.wnl.cashchat.api.domain.attendance.web.controller

import com.wnl.cashchat.api.common.security.jwt.JwtTokenHandler
import com.wnl.cashchat.api.domain.attendance.exception.AlreadyCheckedInException
import com.wnl.cashchat.api.domain.attendance.service.AttendanceService
import com.wnl.cashchat.api.domain.attendance.service.CheckInResult
import com.wnl.cashchat.api.domain.attendance.service.MonthlyAttendance
import com.wnl.cashchat.api.domain.attendance.service.RewardView
import com.wnl.cashchat.api.domain.attendance.web.exception.AttendanceExceptionHandler
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(AttendanceController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AttendanceExceptionHandler::class)
class AttendanceControllerTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var mockMvc: MockMvc

    @MockBean lateinit var attendanceService: AttendanceService
    @MockBean lateinit var jwtTokenHandler: JwtTokenHandler
    @MockBean(name = "jpaMappingContext") lateinit var jpaMappingContext: JpaMetamodelMappingContext

    private val principal = UsernamePasswordAuthenticationToken(1L, null)

    init {
        test("check-in returns awarded coin and streak") {
            whenever(attendanceService.checkIn(eq(1L), any())).thenReturn(
                CheckInResult(
                    awardedCoin = 50L,
                    streakDayCount = 7,
                    bonusItems = listOf(com.wnl.cashchat.api.domain.attendance.service.BonusItem("EVO_STONE", 1)),
                    nextReward = RewardView(dayCount = 8, coin = 20L, bonusItems = emptyList()),
                )
            )

            mockMvc.perform(post("/api/attendance/check-in").principal(principal))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.awardedCoin").value(50))
                .andExpect(jsonPath("$.streakDayCount").value(7))
                .andExpect(jsonPath("$.bonusItems[0].itemCode").value("EVO_STONE"))
                .andExpect(jsonPath("$.bonusItems[0].quantity").value(1))
                .andExpect(jsonPath("$.nextRewardPreview.dayCount").value(8))
                .andExpect(jsonPath("$.nextRewardPreview.coin").value(20))
        }

        test("duplicate check-in returns 409 ALREADY_CHECKED_IN") {
            whenever(attendanceService.checkIn(eq(1L), any())).thenThrow(AlreadyCheckedInException())

            mockMvc.perform(post("/api/attendance/check-in").principal(principal))
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.code").value("ALREADY_CHECKED_IN"))
        }

        test("GET /me without params returns monthly calendar") {
            whenever(attendanceService.getMonthly(eq(1L), any(), any(), any())).thenReturn(
                MonthlyAttendance(
                    year = 2026, month = 5, checkedDays = listOf(1, 2, 3),
                    currentStreak = 3, todayChecked = true,
                    nextReward = RewardView(dayCount = 4, coin = 20L, bonusItems = emptyList()),
                )
            )

            mockMvc.perform(get("/api/attendance/me").principal(principal))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.checkedDays.length()").value(3))
                .andExpect(jsonPath("$.currentStreak").value(3))
                .andExpect(jsonPath("$.todayChecked").value(true))
        }

        test("GET /me with only year returns 400") {
            mockMvc.perform(get("/api/attendance/me").param("year", "2026").principal(principal))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("INVALID_ATTENDANCE_QUERY"))
        }

        test("GET /me with month out of range returns 400") {
            mockMvc.perform(
                get("/api/attendance/me").param("year", "2026").param("month", "13").principal(principal)
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("INVALID_ATTENDANCE_QUERY"))
        }
    }
}
```

- [ ] **Step 2: 테스트 실행 → PASS**

Run: `cd apps/backend && ./gradlew test --tests "*AttendanceControllerTest"`
Expected: PASS (5 tests). (컨트롤러는 Task 4에서 이미 구현됨.)

- [ ] **Step 3: 커밋**

```bash
git add apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/attendance/web
git commit -m "test(attendance): 출석 컨트롤러 WebMvc 테스트 추가" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: 통합 테스트 — 실제 시드 + 적립 (Testcontainers MySQL)

**Files:**
- Test: `src/test/.../domain/attendance/persistence/AttendanceIntegrationTest.kt`

`ChatPersistenceIntegrationTest` 패턴을 따른다. Flyway 가 실제 MySQL 8 에 V1~V3(시드 포함)을 적용하므로 보상 시드값과 적립 흐름을 end-to-end 검증한다.

- [ ] **Step 1: 통합 테스트 작성**

```kotlin
package com.wnl.cashchat.api.domain.attendance.persistence

import com.wnl.cashchat.api.domain.attendance.exception.AlreadyCheckedInException
import com.wnl.cashchat.api.domain.attendance.persistence.repository.AttendanceLogRepository
import com.wnl.cashchat.api.domain.attendance.service.AttendanceService
import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.point.persistence.repository.PointTransactionRepository
import com.wnl.cashchat.api.domain.point.persistence.repository.UserPointRepository
import com.wnl.cashchat.api.domain.point.service.UserPointService
import com.wnl.cashchat.api.domain.user.persistence.entity.Role
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import com.wnl.cashchat.api.domain.user.persistence.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MySQLContainer
import java.time.LocalDate

@SpringBootTest
class AttendanceIntegrationTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var userPointRepository: UserPointRepository
    @Autowired lateinit var pointTransactionRepository: PointTransactionRepository
    @Autowired lateinit var attendanceLogRepository: AttendanceLogRepository
    @Autowired lateinit var userPointService: UserPointService
    @Autowired lateinit var attendanceService: AttendanceService

    init {
        beforeTest {
            attendanceLogRepository.deleteAll()
            pointTransactionRepository.deleteAll()
            userPointRepository.deleteAll()
            userRepository.deleteAll()
        }

        test("first check-in credits 20 base coins atomically with the log") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "att"))
            userPointService.ensureInitialized(user)

            val result = attendanceService.checkIn(user.id, LocalDate.of(2026, 5, 30))

            result.streakDayCount shouldBe 1
            result.awardedCoin shouldBe 20L
            attendanceLogRepository.findByUserIdAndCheckInDateBetween(
                user.id, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)
            ).size shouldBe 1
            userPointRepository.findByUserId(user.id)!!.balance shouldBe 21L // initial 1 + 20
            pointTransactionRepository.count() shouldBe 1L
        }

        test("duplicate same-day check-in is rejected and writes nothing extra") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "dup"))
            userPointService.ensureInitialized(user)
            attendanceService.checkIn(user.id, LocalDate.of(2026, 5, 30))

            shouldThrow<AlreadyCheckedInException> {
                attendanceService.checkIn(user.id, LocalDate.of(2026, 5, 30))
            }

            attendanceLogRepository.count() shouldBe 1L
            pointTransactionRepository.count() shouldBe 1L
            userPointRepository.findByUserId(user.id)!!.balance shouldBe 21L
        }

        test("reaching day 7 via consecutive check-ins awards the seeded 50 coins plus bonus") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "wk"))
            userPointService.ensureInitialized(user)

            lateinit var last: com.wnl.cashchat.api.domain.attendance.service.CheckInResult
            for (day in 1..7) {
                last = attendanceService.checkIn(user.id, LocalDate.of(2026, 5, day))
            }

            last.streakDayCount shouldBe 7
            last.awardedCoin shouldBe 50L
            last.bonusItems shouldBe listOf(
                com.wnl.cashchat.api.domain.attendance.service.BonusItem("EVO_STONE", 1)
            )
            // initial 1 + (20*6 days) + 50 (day7) = 171
            userPointRepository.findByUserId(user.id)!!.balance shouldBe 171L
        }
    }

    companion object {
        private val mysql = MySQLContainer("mysql:8.4.0")
            .withDatabaseName("cashchat")
            .withUsername("cashchat")
            .withPassword("cashchat")

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            if (!mysql.isRunning) mysql.start()
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
            registry.add("spring.datasource.driver-class-name", mysql::getDriverClassName)
        }
    }
}
```

- [ ] **Step 2: 통합 테스트 실행 → PASS**

Run: `cd apps/backend && ./gradlew test --tests "*AttendanceIntegrationTest"`
Expected: PASS (3 tests). Docker 필요. 실제 시드(20/50 + EVO_STONE)와 단일 트랜잭션 적립을 검증.

- [ ] **Step 3: 커밋**

```bash
git add apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/attendance/persistence
git commit -m "test(attendance): 출석 적립 통합 테스트 추가 (TestContainers MySQL)" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: 전체 빌드 검증 + 체크리스트 갱신 + PR 준비

- [ ] **Step 1: 전체 빌드**

Run: `cd apps/backend && ./gradlew clean build`
Expected: BUILD SUCCESSFUL. 전체 테스트 통과, Flyway V1~V3 가 H2(MySQL 모드)·MySQL 8 양쪽에서 적용, validate 통과.

- [ ] **Step 2: `docs/features/reward/tasks.md` BE-2 체크**

BE-2 항목을 `[x]`로 갱신하되, 부가 보상 아이템은 "정의·미리보기만, 실제 인벤토리 지급은 후속(인벤토리 도메인)"으로 메모. 31일+ 사이클은 기본 폴백(20코인)으로 임시 처리됨을 메모.

- [ ] **Step 3: 커밋**

```bash
git add docs/features/reward/tasks.md
git commit -m "docs(reward): BE-2 출석 도메인 PR2 완료 항목 체크리스트 반영" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 4: 푸시 + PR (finishing-a-development-branch 스킬 사용)**

origin(seedplan005 포크)에 푸시 후 upstream `cash-chat-mvp/cash-chat-mvp` `dev` 대상으로 PR. 제목: `[CC-288] 혜택존 출석 도메인 (PR2)`.

---

## Self-Review 결과

- **Spec 커버리지(BE-2 인수 기준):**
  - 첫 출석(단일 트랜잭션 log+코인, streak 1, 시드 코인) — Task 3·6 ✓
  - 같은 날 중복 → 409 ALREADY_CHECKED_IN, 추가 쓰기 없음 — Task 3·5·6 ✓
  - 연속 카운트 증가(전일 출석 → +1) — Task 3 ✓
  - 연속 끊김(2일+ 전 → 1 리셋) — Task 3 ✓
  - 7/14/30 마일스톤 코인 + 부가 보상(정의/미리보기) — Task 3(7일 단위) · Task 6(7일 통합) ✓; 14/30은 시드·폴백 로직으로 동일 경로 커버(시드값 14:100·30:300 검증은 통합에서 7일까지 직접 검증, 14/30 동일 메커니즘)
  - 캘린더 조회(year/month 둘 다/둘 다 생략, 한쪽만 400, 기본 KST) — Task 4·5 ✓; 응답 형태(checkedDays/currentStreak/todayChecked/nextRewardPreview) — Task 3·4 ✓
  - 멱등성 키 `attendance:{userId}:{yyyy-MM-dd}`(KST) — Task 3 ✓
- **범위 외 처리:** 부가 보상 아이템 실제 지급은 의도적으로 제외(결정 #2). 31일+ 사이클은 기본 폴백(임시, 결정 #3).
- **타입 일관성:** `CheckInResult`/`MonthlyAttendance`/`RewardView`/`BonusItem` 필드명이 서비스→DTO→컨트롤러→테스트에서 일관. `recordTransaction(userId, delta, reason, idempotencyKey)` BE-1 시그니처와 일치. 리포지토리 파생 쿼리명(`existsByUserIdAndCheckInDate`, `findTopByUserIdOrderByCheckInDateDesc`, `findByUserIdAndCheckInDateBetween`, `findByDayCount`)이 서비스 사용처와 일치.
- **검증 안전망:** validate(Task 1 Step 5)와 통합 테스트(Task 6)가 H2/MySQL 양쪽에서 스키마·시드·적립을 확인. `attendance_reward`/`attendance_reward_bonus`의 BaseEntity 미상속(감사 컬럼 없음)이 validate 리스크 — Task 1 Step 5에서 수렴.
- **미해결 메모:** 14/30일 시드값의 직접 단위 검증은 7일과 동일 경로라 통합에서 7일까지만 직접 확인 — 필요 시 통합 테스트에 14/30 케이스 추가 가능(현재 YAGNI로 생략).
