package com.wnl.cashchat.api.domain.roulette.web.response

import com.wnl.cashchat.api.domain.roulette.persistence.entity.RouletteAdNonce
import com.wnl.cashchat.api.domain.roulette.persistence.entity.RoulettePrize
import com.wnl.cashchat.api.domain.roulette.service.RouletteSegment
import com.wnl.cashchat.api.domain.roulette.service.RouletteSpinResult
import com.wnl.cashchat.api.domain.roulette.service.RouletteStatus
import java.time.Instant
import java.time.LocalDate

data class RouletteStatusResponse(
    val date: LocalDate,
    val dailyLimit: Int,
    val spinsUsedToday: Int,
    val freeSpinAvailable: Boolean,
    val remaining: Int,
    val resetAtKst: Instant,
    val segments: List<RouletteSegmentResponse>,
) {
    companion object {
        fun from(status: RouletteStatus): RouletteStatusResponse =
            RouletteStatusResponse(
                date = status.date,
                dailyLimit = status.dailyLimit,
                spinsUsedToday = status.spinsUsedToday,
                freeSpinAvailable = status.freeSpinAvailable,
                remaining = status.remaining,
                resetAtKst = status.resetAtKst,
                segments = status.segments.map(RouletteSegmentResponse::from),
            )
    }
}

data class RouletteSegmentResponse(
    val index: Int,
    val prize: RoulettePrize,
    val energy: Int,
) {
    companion object {
        fun from(segment: RouletteSegment): RouletteSegmentResponse =
            RouletteSegmentResponse(segment.index, segment.prize, segment.energy)
    }
}

data class RouletteSpinResponse(
    val prize: RoulettePrize,
    val segmentIndex: Int,
    val prizeEnergy: Int,
    val awardedEnergy: Int,
    val energyAfter: Int,
    val status: RouletteStatusResponse,
) {
    companion object {
        fun from(result: RouletteSpinResult): RouletteSpinResponse =
            RouletteSpinResponse(
                prize = result.prize,
                segmentIndex = result.segmentIndex,
                prizeEnergy = result.prizeEnergy,
                awardedEnergy = result.awardedEnergy,
                energyAfter = result.energyAfter,
                status = RouletteStatusResponse.from(result.status),
            )
    }
}

data class RouletteIssueNonceResponse(
    val nonce: String,
    val expiresAt: Instant,
) {
    companion object {
        fun from(nonce: RouletteAdNonce): RouletteIssueNonceResponse =
            RouletteIssueNonceResponse(nonce.nonce, nonce.expiresAt)
    }
}
