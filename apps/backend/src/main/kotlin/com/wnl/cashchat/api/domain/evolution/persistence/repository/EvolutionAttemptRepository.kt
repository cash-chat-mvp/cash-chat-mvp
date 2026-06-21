package com.wnl.cashchat.api.domain.evolution.persistence.repository

import com.wnl.cashchat.api.domain.evolution.persistence.entity.EvolutionAttempt
import org.springframework.data.jpa.repository.JpaRepository

interface EvolutionAttemptRepository : JpaRepository<EvolutionAttempt, Long> {
    fun findByUserIdAndAttemptKey(userId: Long, attemptKey: String): EvolutionAttempt?
    fun findByIdAndUserId(id: Long, userId: Long): EvolutionAttempt?
}
