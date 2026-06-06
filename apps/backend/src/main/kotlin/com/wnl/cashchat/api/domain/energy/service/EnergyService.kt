package com.wnl.cashchat.api.domain.energy.service

import com.wnl.cashchat.api.domain.energy.exception.InsufficientEnergyException
import com.wnl.cashchat.api.domain.energy.persistence.entity.UserEnergy
import com.wnl.cashchat.api.domain.energy.persistence.repository.UserEnergyRepository
import com.wnl.cashchat.api.domain.energy.properties.EnergyProperties
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.math.floor

/**
 * 밥(채팅 연료) 지갑. 충전·차감·보정은 user_energy 행을 비관락으로 잡고 원자 처리한다.
 * 가입 시 ensureInitialized 로 보너스 밥을 1회 지급한다(획득 비용/CAC). 돈(point)은 건드리지 않는다.
 */
@Service
class EnergyService(
    private val userEnergyRepository: UserEnergyRepository,
    private val energyProperties: EnergyProperties,
) {
    fun ensureInitialized(user: User): UserEnergy =
        userEnergyRepository.findByUserId(user.id) ?: createInitial(user)

    @Transactional(readOnly = true)
    fun getEnergy(userId: Long): EnergyView {
        val energy = userEnergyRepository.findByUserId(userId)
            ?: throw IllegalStateException("UserEnergy not initialized for userId=$userId")
        return EnergyView(energy = energy.energy, maxEnergy = energyProperties.maxEnergy)
    }

    /** 채팅 1회 = 밥 1개 차감. 잔여 밥이 없으면 게이트(InsufficientEnergyException). */
    @Transactional
    fun consume(userId: Long) {
        val energy = userEnergyRepository.findByUserIdForUpdate(userId)
            ?: throw IllegalStateException("UserEnergy not initialized for userId=$userId")
        if (energy.energy < 1) throw InsufficientEnergyException()
        energy.consume()
    }

    @Transactional
    fun charge(userId: Long, amount: Int) {
        val energy = userEnergyRepository.findByUserIdForUpdate(userId)
            ?: throw IllegalStateException("UserEnergy not initialized for userId=$userId")
        energy.charge(amount, energyProperties.maxEnergy)
    }

    /** 진화 성공 직후 1회 보정: 밥을 floor(max * ratio) 까지 끌어올린다(이미 많으면 그대로). */
    @Transactional
    fun applyPostEvolutionBoost(userId: Long) {
        val energy = userEnergyRepository.findByUserIdForUpdate(userId)
            ?: throw IllegalStateException("UserEnergy not initialized for userId=$userId")
        val boostFloor = floor(energyProperties.maxEnergy * energyProperties.postEvolutionRatio).toInt()
        energy.boostTo(boostFloor)
    }

    private fun createInitial(user: User): UserEnergy =
        try {
            userEnergyRepository.saveAndFlush(UserEnergy(user = user, energy = energyProperties.signupBonus))
        } catch (e: DataIntegrityViolationException) {
            userEnergyRepository.findByUserId(user.id) ?: throw e
        }
}
