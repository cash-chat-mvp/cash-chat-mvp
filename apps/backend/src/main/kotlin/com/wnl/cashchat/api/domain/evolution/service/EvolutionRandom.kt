package com.wnl.cashchat.api.domain.evolution.service

import org.springframework.stereotype.Component
import java.security.SecureRandom

interface EvolutionRandom {
    /** @return [0.0, 1.0) 균등 분포 보안 난수 */
    fun roll(): Double
}

@Component
class SecureRandomEvolutionRandom : EvolutionRandom {
    private val random = SecureRandom()
    override fun roll(): Double = random.nextDouble()
}
