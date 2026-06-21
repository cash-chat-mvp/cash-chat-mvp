package com.wnl.cashchat.api.domain.attendance.service

import com.wnl.cashchat.api.domain.attendance.exception.AlreadyCheckedInException
import com.wnl.cashchat.api.domain.attendance.exception.InvalidAttendanceQueryException
import com.wnl.cashchat.api.domain.attendance.persistence.entity.AttendanceLog
import com.wnl.cashchat.api.domain.attendance.persistence.repository.AttendanceLogRepository
import com.wnl.cashchat.api.domain.attendance.persistence.repository.AttendanceRewardBonusRepository
import com.wnl.cashchat.api.domain.attendance.persistence.repository.AttendanceRewardRepository
import com.wnl.cashchat.api.domain.economy.persistence.entity.EnergySourceType
import com.wnl.cashchat.api.domain.economy.properties.EconomyProperties
import com.wnl.cashchat.api.domain.economy.service.EnergyService
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.DateTimeException
import java.time.LocalDate
import java.time.ZoneId

/**
 * 일일 출석 도장과 누적 일차 보상.
 *
 * checkIn 은 단일 @Transactional 안에서 출석 로그 INSERT 와 Energy 적립(energyService.grant)을 함께 수행해
 * "도장만 찍히고 에너지 없음" 같은 부분 성공을 차단한다. 적립의 멱등성/동시성은 energyService.grant 가 보장한다.
 *
 * 지갑은 grant 호출 시 lazy bootstrap(insertIfAbsent)으로 자동 생성되므로 사전 초기화 불필요.
 */
@Service
class AttendanceService(
    private val attendanceLogRepository: AttendanceLogRepository,
    private val attendanceRewardRepository: AttendanceRewardRepository,
    private val attendanceRewardBonusRepository: AttendanceRewardBonusRepository,
    private val energyService: EnergyService,
    private val economyProperties: EconomyProperties,
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

        // saveAndFlush 로 INSERT 를 즉시 강제해, exists 검사 통과 후 동시에 도착한 다른 요청과
        // uq_attendance_log_user_date 제약이 충돌하면 여기서 잡아 409(ALREADY_CHECKED_IN)로 변환한다.
        // (exists 사전 검사는 순차 중복의 fast-path, 이 catch 는 동시성 race 의 방어선)
        try {
            attendanceLogRepository.saveAndFlush(
                AttendanceLog(userId = userId, checkInDate = today, streakDayCount = streak)
            )
        } catch (e: DataIntegrityViolationException) {
            // uq_attendance_log_user_date(같은 날 중복) 위반만 409로 변환하고,
            // FK 등 다른 무결성 오류는 그대로 전파해 가리지 않는다.
            if (isDuplicateCheckInViolation(e)) {
                throw AlreadyCheckedInException()
            }
            throw e
        }

        energyService.grant(
            userId = userId,
            amount = economyProperties.attendanceEnergyReward,
            sourceType = EnergySourceType.ATTENDANCE_AD,
            expiresAt = today.plusDays(economyProperties.attendanceEnergyExpirationDays).atStartOfDay(KST).toInstant(),
            idempotencyKey = "attendance:$userId:$today",
        )

        return CheckInResult(
            awardedEnergy = economyProperties.attendanceEnergyReward,
            streakDayCount = streak,
            bonusItems = reward.bonusItems,
            nextReward = rewardView(streak + 1),
        )
    }

    /**
     * 월간 출석 현황 조회.
     *
     * checkedDays 는 요청한 year/month 범위로 한정되지만, currentStreak·todayChecked·nextReward 는
     * 항상 "오늘 기준" 전역 상태다(특정 월에 종속되지 않음 — 과거 월을 조회해도 streak 은 리셋되지 않는다).
     * nextReward 는 다음 체크인 시 받을 보상 미리보기로, streak 이 살아있으면(마지막 출석이 오늘/어제) currentStreak+1,
     * 끊겼으면 currentStreak=0 이므로 day 1(재시작) 보상을 가리킨다.
     */
    @Transactional(readOnly = true)
    fun getMonthly(userId: Long, year: Int, month: Int, today: LocalDate): MonthlyAttendance {
        val start = try {
            LocalDate.of(year, month, 1)
        } catch (e: DateTimeException) {
            throw InvalidAttendanceQueryException("year/month is out of the supported range", e)
        }
        val end = start.plusMonths(1).minusDays(1)

        val logs = attendanceLogRepository.findByUserIdAndCheckInDateBetweenOrderByCheckInDateAsc(userId, start, end)
        val checkedDays = logs.map { it.checkInDate.dayOfMonth }

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
        val bonuses = attendanceRewardBonusRepository.findByDayCountOrderByItemCodeAsc(dayCount)
            .map { BonusItem(itemCode = it.itemCode, quantity = it.quantity) }
        return RewardView(dayCount = dayCount, coin = reward.coin, bonusItems = bonuses)
    }

    /**
     * 예외 원인 체인의 메시지에 같은 날 중복 유니크 제약명이 포함되는지 검사한다.
     * (H2 MySQL 모드·MySQL 8 모두 제약/인덱스명이 메시지에 노출됨)
     */
    private fun isDuplicateCheckInViolation(e: DataIntegrityViolationException): Boolean =
        generateSequence(e as Throwable) { it.cause }
            .any { it.message?.contains(ATTENDANCE_UNIQUE_CONSTRAINT, ignoreCase = true) == true }

    private companion object {
        private const val BASE_DAY_COUNT = 0
        private const val ATTENDANCE_UNIQUE_CONSTRAINT = "uq_attendance_log_user_date"
        private val KST = ZoneId.of("Asia/Seoul")
    }
}
