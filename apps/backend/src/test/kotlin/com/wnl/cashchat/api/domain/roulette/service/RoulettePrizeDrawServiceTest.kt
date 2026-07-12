package com.wnl.cashchat.api.domain.roulette.service

import com.wnl.cashchat.api.domain.roulette.persistence.entity.RoulettePrize
import com.wnl.cashchat.api.domain.roulette.properties.RouletteProperties
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Duration

class RoulettePrizeDrawServiceTest : FunSpec({
    fun serviceFor(roll: Int) = RoulettePrizeDrawService(
        properties = RouletteProperties(
            nonceTtl = Duration.ofMinutes(10),
            prizes = listOf(
                RouletteProperties.PrizeWeight(RoulettePrize.JACKPOT_100, 100, 1),
                RouletteProperties.PrizeWeight(RoulettePrize.E10, 10, 10),
                RouletteProperties.PrizeWeight(RoulettePrize.E3, 3, 70),
                RouletteProperties.PrizeWeight(RoulettePrize.MISS, 0, 19),
            ),
        ),
        roll = { roll },
    )

    test("draw maps configured cumulative weight boundaries") {
        serviceFor(0).draw().prize shouldBe RoulettePrize.JACKPOT_100
        serviceFor(1).draw().prize shouldBe RoulettePrize.E10
        serviceFor(10).draw().prize shouldBe RoulettePrize.E10
        serviceFor(11).draw().prize shouldBe RoulettePrize.E3
        serviceFor(80).draw().prize shouldBe RoulettePrize.E3
        serviceFor(81).draw().prize shouldBe RoulettePrize.MISS
        serviceFor(99).draw().prize shouldBe RoulettePrize.MISS
    }
})
