package com.wnl.cashchat.api.domain.economy.service

import com.wnl.cashchat.api.domain.economy.persistence.repository.SharedQualityPoolRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class SharedQualityPoolService(
    private val sharedQualityPoolRepository: SharedQualityPoolRepository,
) {
    /** 싱글톤 풀 행을 멱등 보장한 뒤 원자적 가산한다(I9: 가산만이므로 음수 불가). amount<=0이면 적립하지 않는다. */
    @Transactional(propagation = Propagation.MANDATORY)
    fun accrue(amount: BigDecimal) {
        if (amount <= BigDecimal.ZERO) return
        sharedQualityPoolRepository.insertSingletonIfAbsent()
        sharedQualityPoolRepository.accrue(amount)
    }

    /** premiumDelta 만큼 조건부 차감(잔액 충분할 때만). I9: WHERE balance >= delta 로 음수 불가. 성공 시 true. */
    @Transactional(propagation = Propagation.MANDATORY)
    fun tryConsumePremium(premiumDelta: BigDecimal): Boolean {
        sharedQualityPoolRepository.insertSingletonIfAbsent()
        return sharedQualityPoolRepository.tryDebit(premiumDelta) == 1
    }
}
