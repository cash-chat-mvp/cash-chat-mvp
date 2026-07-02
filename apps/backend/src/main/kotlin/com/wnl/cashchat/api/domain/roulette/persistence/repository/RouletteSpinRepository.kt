package com.wnl.cashchat.api.domain.roulette.persistence.repository

import com.wnl.cashchat.api.domain.roulette.persistence.entity.RouletteSpin
import org.springframework.data.jpa.repository.JpaRepository

interface RouletteSpinRepository : JpaRepository<RouletteSpin, Long> {
    fun findByNonce(nonce: String): RouletteSpin?
}
