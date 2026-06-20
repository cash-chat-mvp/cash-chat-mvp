package com.wnl.cashchat.api.domain.economy.service

import com.wnl.cashchat.api.domain.economy.exception.WalletNotInitializedException
import com.wnl.cashchat.api.domain.economy.persistence.entity.UserWallet
import com.wnl.cashchat.api.domain.economy.persistence.repository.UserWalletRepository
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class WalletService(
    private val userWalletRepository: UserWalletRepository,
) {
    @Transactional
    fun ensureInitialized(user: User): UserWallet =
        userWalletRepository.findByUserId(user.id) ?: create(user)

    fun getForUpdate(userId: Long): UserWallet =
        userWalletRepository.findByUserIdForUpdate(userId)
            ?: throw WalletNotInitializedException(userId)

    @Transactional(readOnly = true)
    fun snapshot(userId: Long): UserWallet =
        userWalletRepository.findByUserId(userId) ?: throw WalletNotInitializedException(userId)

    private fun create(user: User): UserWallet =
        try {
            userWalletRepository.saveAndFlush(UserWallet(user = user))
        } catch (e: DataIntegrityViolationException) {
            userWalletRepository.findByUserId(user.id) ?: throw e
        }
}
