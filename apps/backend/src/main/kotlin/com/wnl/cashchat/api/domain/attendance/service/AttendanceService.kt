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
