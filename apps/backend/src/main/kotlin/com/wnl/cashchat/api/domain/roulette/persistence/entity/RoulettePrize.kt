package com.wnl.cashchat.api.domain.roulette.persistence.entity

enum class RoulettePrize(val defaultEnergy: Int) {
    JACKPOT_100(100),
    E10(10),
    E3(3),
    MISS(0),
}
