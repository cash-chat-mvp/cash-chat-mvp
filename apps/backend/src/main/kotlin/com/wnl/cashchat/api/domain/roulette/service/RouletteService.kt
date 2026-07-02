package com.wnl.cashchat.api.domain.roulette.service

import com.wnl.cashchat.api.domain.energy.service.EnergyService
import com.wnl.cashchat.api.domain.roulette.exception.AdNotVerifiedException
import com.wnl.cashchat.api.domain.roulette.exception.DailyLimitReachedException
import com.wnl.cashchat.api.domain.roulette.exception.FreeSpinAvailableException
import com.wnl.cashchat.api.domain.roulette.exception.FreeSpinUsedException
import com.wnl.cashchat.api.domain.roulette.exception.NonceAlreadyUsedException
import com.wnl.cashchat.api.domain.roulette.persistence.entity.RouletteAdNonce
import com.wnl.cashchat.api.domain.roulette.persistence.entity.RouletteDailyState
import com.wnl.cashchat.api.domain.roulette.persistence.entity.RoulettePrize
import com.wnl.cashchat.api.domain.roulette.persistence.entity.RouletteSpin
import com.wnl.cashchat.api.domain.roulette.persistence.entity.RouletteSpinType
import com.wnl.cashchat.api.domain.roulette.persistence.repository.RouletteAdNonceRepository
import com.wnl.cashchat.api.domain.roulette.persistence.repository.RouletteDailyStateRepository
import com.wnl.cashchat.api.domain.roulette.persistence.repository.RouletteSpinRepository
import com.wnl.cashchat.api.domain.roulette.properties.RouletteProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class RouletteSegment(
    val index: Int,
    val prize: RoulettePrize,
    val energy: Int,
)

data class RouletteStatus(
    val date: LocalDate,
    val dailyLimit: Int,
    val spinsUsedToday: Int,
    val freeSpinAvailable: Boolean,
    val remaining: Int,
    val resetAtKst: Instant,
    val segments: List<RouletteSegment>,
)

data class RouletteSpinResult(
    val prize: RoulettePrize,
    val segmentIndex: Int,
    val prizeEnergy: Int,
    val awardedEnergy: Int,
    val energyAfter: Int,
    val status: RouletteStatus,
)

@Service
class RouletteService(
    private val dailyStateRepository: RouletteDailyStateRepository,
    private val adNonceRepository: RouletteAdNonceRepository,
    private val spinRepository: RouletteSpinRepository,
    private val energyService: EnergyService,
    private val prizeDrawService: RoulettePrizeDrawService,
    private val properties: RouletteProperties,
) {
    @Transactional(readOnly = true)
    fun statusOf(userId: Long, now: Instant): RouletteStatus {
        val date = LocalDate.ofInstant(now, KST)
        val state = dailyStateRepository.findByUserIdAndKstDate(userId, date)
        return statusFrom(date, state?.spinsUsed ?: 0, state?.freeSpinsUsed ?: 0)
    }

    @Transactional
    fun issueNonce(userId: Long, now: Instant): RouletteAdNonce {
        val state = lockState(userId, now)
        ensureAdSpinAllowed(state)
        return adNonceRepository.save(
            RouletteAdNonce(
                nonce = UUID.randomUUID().toString().replace("-", ""),
                userId = userId,
                expiresAt = now.plus(properties.nonceTtl),
            )
        )
    }

    @Transactional
    fun spinFree(userId: Long, now: Instant): RouletteSpinResult {
        val state = lockState(userId, now)
        if (state.spinsUsed >= properties.dailyLimit) throw DailyLimitReachedException()
        if (state.freeSpinsUsed >= properties.freeSpinCount) throw FreeSpinUsedException()
        state.recordFreeSpin()
        return createSpin(userId, state, RouletteSpinType.FREE, nonce = null)
    }

    @Transactional
    fun spinWithAd(userId: Long, nonceToken: String, now: Instant): RouletteSpinResult {
        val existing = spinRepository.findByNonce(nonceToken)
        if (existing != null) return replay(existing, now)

        val state = lockState(userId, now)
        ensureAdSpinAllowed(state)
        val nonce = adNonceRepository.findForUpdate(nonceToken) ?: throw AdNotVerifiedException()
        if (nonce.userId != userId || !nonce.verified) throw AdNotVerifiedException()
        if (nonce.used) throw NonceAlreadyUsedException()
        if (!nonce.isVerifiedAndUsable(now)) throw AdNotVerifiedException()
        nonce.markUsed()
        state.recordAdSpin()
        return createSpin(userId, state, RouletteSpinType.AD, nonce = nonceToken)
    }

    @Transactional
    fun verifyAdNonce(nonceToken: String?, transactionId: String): Boolean {
        if (nonceToken.isNullOrBlank()) return false
        val nonce = adNonceRepository.findForUpdate(nonceToken) ?: return false
        nonce.markVerified(transactionId)
        return true
    }

    private fun lockState(userId: Long, now: Instant): RouletteDailyState {
        val date = LocalDate.ofInstant(now, KST)
        dailyStateRepository.insertIfAbsent(userId, date)
        return dailyStateRepository.findForUpdate(userId, date)
            ?: throw IllegalStateException("roulette_daily_state row must exist for userId=$userId on $date")
    }

    private fun ensureAdSpinAllowed(state: RouletteDailyState) {
        if (state.freeSpinsUsed < properties.freeSpinCount) throw FreeSpinAvailableException()
        if (state.spinsUsed >= properties.dailyLimit) throw DailyLimitReachedException()
    }

    private fun createSpin(
        userId: Long,
        state: RouletteDailyState,
        spinType: RouletteSpinType,
        nonce: String?,
    ): RouletteSpinResult {
        val draw = prizeDrawService.draw()
        val before = energyService.getEnergy(userId)
        if (draw.prizeEnergy > 0) {
            energyService.charge(userId, draw.prizeEnergy)
        }
        val after = energyService.getEnergy(userId)
        val awardedEnergy = (after.energy - before.energy).coerceAtLeast(0)
        val segmentIndex = segmentFor(draw.prize)
        val spin = spinRepository.saveAndFlush(
            RouletteSpin(
                userId = userId,
                kstDate = state.kstDate,
                spinType = spinType,
                prize = draw.prize,
                prizeEnergy = draw.prizeEnergy,
                awardedEnergy = awardedEnergy,
                energyAfter = after.energy,
                segmentIndex = segmentIndex,
                nonce = nonce,
            )
        )
        return resultFrom(spin, statusFrom(state.kstDate, state.spinsUsed, state.freeSpinsUsed))
    }

    private fun replay(spin: RouletteSpin, now: Instant): RouletteSpinResult =
        resultFrom(spin, statusOf(spin.userId, now))

    private fun resultFrom(spin: RouletteSpin, status: RouletteStatus): RouletteSpinResult =
        RouletteSpinResult(
            prize = spin.prize,
            segmentIndex = spin.segmentIndex,
            prizeEnergy = spin.prizeEnergy,
            awardedEnergy = spin.awardedEnergy,
            energyAfter = spin.energyAfter,
            status = status,
        )

    private fun statusFrom(date: LocalDate, spinsUsed: Int, freeSpinsUsed: Int): RouletteStatus =
        RouletteStatus(
            date = date,
            dailyLimit = properties.dailyLimit,
            spinsUsedToday = spinsUsed,
            freeSpinAvailable = freeSpinsUsed < properties.freeSpinCount,
            remaining = (properties.dailyLimit - spinsUsed).coerceAtLeast(0),
            resetAtKst = date.plusDays(1).atStartOfDay(KST).toInstant(),
            segments = segments,
        )

    private fun segmentFor(prize: RoulettePrize): Int = segments.first { it.prize == prize }.index

    private val segments: List<RouletteSegment> = listOf(
        RouletteSegment(0, RoulettePrize.JACKPOT_100, 100),
        RouletteSegment(1, RoulettePrize.E3, 3),
        RouletteSegment(2, RoulettePrize.MISS, 0),
        RouletteSegment(3, RoulettePrize.E10, 10),
        RouletteSegment(4, RoulettePrize.E3, 3),
        RouletteSegment(5, RoulettePrize.MISS, 0),
        RouletteSegment(6, RoulettePrize.E10, 10),
        RouletteSegment(7, RoulettePrize.E3, 3),
    )

    private companion object {
        private val KST = ZoneId.of("Asia/Seoul")
    }
}
