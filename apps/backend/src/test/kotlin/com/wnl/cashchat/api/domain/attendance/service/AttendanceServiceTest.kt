package com.wnl.cashchat.api.domain.attendance.service

import com.wnl.cashchat.api.domain.attendance.exception.AlreadyCheckedInException
import com.wnl.cashchat.api.domain.attendance.exception.InvalidAttendanceQueryException
import com.wnl.cashchat.api.domain.attendance.persistence.entity.AttendanceLog
import com.wnl.cashchat.api.domain.attendance.persistence.entity.AttendanceReward
import com.wnl.cashchat.api.domain.attendance.persistence.entity.AttendanceRewardBonus
import com.wnl.cashchat.api.domain.attendance.persistence.repository.AttendanceLogRepository
import com.wnl.cashchat.api.domain.attendance.persistence.repository.AttendanceRewardBonusRepository
import com.wnl.cashchat.api.domain.attendance.persistence.repository.AttendanceRewardRepository
import com.wnl.cashchat.api.domain.economy.persistence.entity.EnergySourceType
import com.wnl.cashchat.api.domain.economy.properties.EconomyProperties
import com.wnl.cashchat.api.domain.economy.service.EnergyService
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
import org.springframework.dao.DataIntegrityViolationException
import java.time.LocalDate

class AttendanceServiceTest : FunSpec({
    lateinit var attendanceLogRepository: AttendanceLogRepository
    lateinit var attendanceRewardRepository: AttendanceRewardRepository
    lateinit var attendanceRewardBonusRepository: AttendanceRewardBonusRepository
    lateinit var energyService: EnergyService
    lateinit var service: AttendanceService

    val userId = 1L
    val today = LocalDate.of(2026, 5, 30)

    beforeTest {
        attendanceLogRepository = mock()
        attendanceRewardRepository = mock()
        attendanceRewardBonusRepository = mock()
        energyService = mock()
        service = AttendanceService(
            attendanceLogRepository,
            attendanceRewardRepository,
            attendanceRewardBonusRepository,
            energyService,
            EconomyProperties(),
        )
        whenever(attendanceRewardRepository.findByDayCount(0)).thenReturn(AttendanceReward(dayCount = 0, coin = 20))
        whenever(attendanceRewardBonusRepository.findByDayCountOrderByItemCodeAsc(any())).thenReturn(emptyList())
    }

    test("first check-in: streak 1, fixed 4 energy, log saved, grant called with idempotency key") {
        whenever(attendanceLogRepository.existsByUserIdAndCheckInDate(userId, today)).thenReturn(false)
        whenever(attendanceLogRepository.findTopByUserIdOrderByCheckInDateDesc(userId)).thenReturn(null)
        whenever(attendanceRewardRepository.findByDayCount(1)).thenReturn(null)

        val result = service.checkIn(userId, today)

        result.streakDayCount shouldBe 1
        result.awardedEnergy shouldBe 4L
        result.bonusItems shouldBe emptyList()
        verify(attendanceLogRepository).saveAndFlush(argThat<AttendanceLog> {
            this.userId == userId && checkInDate == today && streakDayCount == 1
        })
        verify(energyService).grant(
            eq(userId), eq(4L), eq(EnergySourceType.ATTENDANCE_AD), any(), eq("attendance:1:2026-05-30"),
        )
    }

    test("duplicate same-day check-in throws and writes nothing") {
        whenever(attendanceLogRepository.existsByUserIdAndCheckInDate(userId, today)).thenReturn(true)

        shouldThrow<AlreadyCheckedInException> { service.checkIn(userId, today) }

        verify(attendanceLogRepository, never()).saveAndFlush(any())
        verify(energyService, never()).grant(any(), any(), any(), any(), any())
    }

    test("concurrent check-in losing the unique-constraint race is mapped to AlreadyCheckedInException") {
        whenever(attendanceLogRepository.existsByUserIdAndCheckInDate(userId, today)).thenReturn(false)
        whenever(attendanceLogRepository.findTopByUserIdOrderByCheckInDateDesc(userId)).thenReturn(null)
        whenever(attendanceRewardRepository.findByDayCount(1)).thenReturn(null)
        whenever(attendanceLogRepository.saveAndFlush(any<AttendanceLog>()))
            .thenThrow(DataIntegrityViolationException("uq_attendance_log_user_date"))

        shouldThrow<AlreadyCheckedInException> { service.checkIn(userId, today) }

        verify(energyService, never()).grant(any(), any(), any(), any(), any())
    }

    test("a non-duplicate integrity violation is rethrown, not masked as AlreadyCheckedInException") {
        whenever(attendanceLogRepository.existsByUserIdAndCheckInDate(userId, today)).thenReturn(false)
        whenever(attendanceLogRepository.findTopByUserIdOrderByCheckInDateDesc(userId)).thenReturn(null)
        whenever(attendanceRewardRepository.findByDayCount(1)).thenReturn(null)
        whenever(attendanceLogRepository.saveAndFlush(any<AttendanceLog>()))
            .thenThrow(DataIntegrityViolationException("could not execute statement [fk_attendance_log_user]"))

        shouldThrow<DataIntegrityViolationException> { service.checkIn(userId, today) }

        verify(energyService, never()).grant(any(), any(), any(), any(), any())
    }

    test("getMonthly rejects an out-of-range year with InvalidAttendanceQueryException") {
        shouldThrow<InvalidAttendanceQueryException> {
            service.getMonthly(userId, 1_000_000_000, 5, today)
        }
    }

    test("consecutive day increments streak") {
        whenever(attendanceLogRepository.existsByUserIdAndCheckInDate(userId, today)).thenReturn(false)
        whenever(attendanceLogRepository.findTopByUserIdOrderByCheckInDateDesc(userId)).thenReturn(
            AttendanceLog(userId = userId, checkInDate = today.minusDays(1), streakDayCount = 3)
        )
        whenever(attendanceRewardRepository.findByDayCount(4)).thenReturn(null)

        val result = service.checkIn(userId, today)

        result.streakDayCount shouldBe 4
        result.awardedEnergy shouldBe 4L
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

    test("day 7 milestone: fixed 4 energy, EVO_STONE bonus, preview uses seeded coin") {
        whenever(attendanceLogRepository.existsByUserIdAndCheckInDate(userId, today)).thenReturn(false)
        whenever(attendanceLogRepository.findTopByUserIdOrderByCheckInDateDesc(userId)).thenReturn(
            AttendanceLog(userId = userId, checkInDate = today.minusDays(1), streakDayCount = 6)
        )
        whenever(attendanceRewardRepository.findByDayCount(7)).thenReturn(AttendanceReward(dayCount = 7, coin = 50))
        whenever(attendanceRewardBonusRepository.findByDayCountOrderByItemCodeAsc(7)).thenReturn(
            listOf(AttendanceRewardBonus(dayCount = 7, itemCode = "EVO_STONE", quantity = 1))
        )

        val result = service.checkIn(userId, today)

        result.streakDayCount shouldBe 7
        result.awardedEnergy shouldBe 4L
        result.bonusItems shouldBe listOf(BonusItem("EVO_STONE", 1))
        verify(energyService).grant(
            eq(userId), eq(4L), eq(EnergySourceType.ATTENDANCE_AD), any(), eq("attendance:1:2026-05-30"),
        )
    }

    test("getMonthly returns calendar, active streak, todayChecked, and next reward preview") {
        val logs = (1..7).map {
            AttendanceLog(userId = userId, checkInDate = LocalDate.of(2026, 5, it), streakDayCount = it)
        }
        whenever(attendanceLogRepository.findByUserIdAndCheckInDateBetweenOrderByCheckInDateAsc(userId, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)))
            .thenReturn(logs)
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
        whenever(attendanceLogRepository.findByUserIdAndCheckInDateBetweenOrderByCheckInDateAsc(userId, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)))
            .thenReturn(listOf(log))
        whenever(attendanceLogRepository.findTopByUserIdOrderByCheckInDateDesc(userId)).thenReturn(log)
        whenever(attendanceRewardRepository.findByDayCount(6)).thenReturn(null)

        val result = service.getMonthly(userId, 2026, 5, today)

        result.currentStreak shouldBe 5
        result.todayChecked shouldBe true
        result.checkedDays shouldBe listOf(30)
        result.nextReward.dayCount shouldBe 6
    }

    test("getMonthly keeps the streak alive when latest log is yesterday but today is unchecked") {
        val log = AttendanceLog(userId = userId, checkInDate = today.minusDays(1), streakDayCount = 4)
        whenever(attendanceLogRepository.findByUserIdAndCheckInDateBetweenOrderByCheckInDateAsc(userId, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)))
            .thenReturn(listOf(log))
        whenever(attendanceLogRepository.findTopByUserIdOrderByCheckInDateDesc(userId)).thenReturn(log)
        whenever(attendanceRewardRepository.findByDayCount(5)).thenReturn(null)

        val result = service.getMonthly(userId, 2026, 5, today)

        result.currentStreak shouldBe 4
        result.todayChecked shouldBe false
        result.checkedDays shouldBe listOf(29)
        result.nextReward.dayCount shouldBe 5
    }
})
