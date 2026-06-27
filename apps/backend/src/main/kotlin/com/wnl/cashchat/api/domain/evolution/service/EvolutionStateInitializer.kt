package com.wnl.cashchat.api.domain.evolution.service

import com.wnl.cashchat.api.domain.evolution.persistence.entity.UserEvolution
import com.wnl.cashchat.api.domain.evolution.persistence.repository.UserEvolutionRepository
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import com.wnl.cashchat.api.domain.user.persistence.repository.UserRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class EvolutionStateInitializer(
    private val userEvolutionRepository: UserEvolutionRepository,
    private val userRepository: UserRepository,
) {
    fun ensureInitialized(user: User): UserEvolution =
        userEvolutionRepository.findByUserId(user.id) ?: createInitial(user)

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun initializeExistingUser(userId: Long): UserEvolution {
        val user = userRepository.findByIdForUpdate(userId)
            ?: throw IllegalStateException("User not found for userId=$userId")
        userEvolutionRepository.findByUserId(userId)?.let { return it }
        return createInitial(user)
    }

    private fun createInitial(user: User): UserEvolution =
        try {
            userEvolutionRepository.saveAndFlush(UserEvolution(user = user, level = 1))
        } catch (e: DataIntegrityViolationException) {
            userEvolutionRepository.findByUserId(user.id) ?: throw e
        }
}
