package com.wnl.cashchat.api.domain.roulette.service

import com.wnl.cashchat.api.domain.roulette.persistence.entity.RoulettePrize
import com.wnl.cashchat.api.domain.roulette.properties.RouletteProperties
import org.springframework.stereotype.Service
import kotlin.random.Random

data class RoulettePrizeDraw(
    val prize: RoulettePrize,
    val prizeEnergy: Int,
)

@Service
class RoulettePrizeDrawService(
    private val properties: RouletteProperties,
    private val roll: (Int) -> Int = { bound -> Random.nextInt(bound) },
) {
    fun draw(): RoulettePrizeDraw {
        val totalWeight = properties.prizes.sumOf { it.weight }
        val selected = roll(totalWeight)
        var cumulative = 0
        properties.prizes.forEach { prize ->
            cumulative += prize.weight
            if (selected < cumulative) {
                return RoulettePrizeDraw(prize.prize, prize.prizeEnergy)
            }
        }
        val fallback = properties.prizes.last()
        return RoulettePrizeDraw(fallback.prize, fallback.prizeEnergy)
    }
}
