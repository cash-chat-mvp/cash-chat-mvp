package com.wnl.cashchat.api.domain.evolution.persistence.repository

import com.wnl.cashchat.api.domain.evolution.persistence.entity.EvolutionAttempt
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface EvolutionAttemptRepository : JpaRepository<EvolutionAttempt, Long> {
    fun findByUserIdAndIdempotencyKey(userId: Long, idempotencyKey: String): EvolutionAttempt?
    fun findByUserIdOrderByCreatedAtDesc(userId: Long, pageable: Pageable): List<EvolutionAttempt>
}