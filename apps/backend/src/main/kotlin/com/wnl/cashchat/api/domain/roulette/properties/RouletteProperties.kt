package com.wnl.cashchat.api.domain.roulette.properties

import com.wnl.cashchat.api.domain.roulette.persistence.entity.RoulettePrize
import com.wnl.cashchat.api.domain.ad.properties.PositiveDuration
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.Duration

@Validated
@ConfigurationProperties(prefix = "app.roulette")
data class RouletteProperties(
    @field:Positive
    val dailyLimit: Int = 5,

    @field:Positive
    val freeSpinCount: Int = 1,

    @field:Positive
    val segmentCount: Int = 8,

    @field:PositiveDuration
    val nonceTtl: Duration = Duration.ofMinutes(10),

    @field:NotEmpty
    @field:Valid
    val prizes: List<PrizeWeight> = listOf(
        PrizeWeight(RoulettePrize.JACKPOT_100, prizeEnergy = 100, weight = 1),
        PrizeWeight(RoulettePrize.E10, prizeEnergy = 10, weight = 10),
        PrizeWeight(RoulettePrize.E3, prizeEnergy = 3, weight = 70),
        PrizeWeight(RoulettePrize.MISS, prizeEnergy = 0, weight = 19),
    ),
) {
    data class PrizeWeight(
        val prize: RoulettePrize,
        @field:Min(0)
        val prizeEnergy: Int,
        @field:Positive
        val weight: Int,
    )
}
