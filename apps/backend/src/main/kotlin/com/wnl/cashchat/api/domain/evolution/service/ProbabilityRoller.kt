package com.wnl.cashchat.api.domain.evolution.service

import org.springframework.stereotype.Component
import java.security.SecureRandom

/**
 * 진화 성공 확률 판정. 주입형이라 테스트에서 결과를 결정적으로 제어할 수 있다.
 */
interface ProbabilityRoller {
    fun succeeds(successRate: Double): Boolean
}

@Component
class SecureRandomProbabilityRoller : ProbabilityRoller {
    private val random = SecureRandom()

    // nextDouble() 은 [0.0, 1.0) → rate=0.0 이면 항상 false, rate=1.0 이면 항상 true.
    override fun succeeds(successRate: Double): Boolean = random.nextDouble() < successRate
}