package com.wnl.cashchat.api.domain.offerwall.persistence.repository

import com.wnl.cashchat.api.domain.offerwall.persistence.entity.OfferwallUserToken
import org.springframework.data.jpa.repository.JpaRepository

interface OfferwallUserTokenRepository : JpaRepository<OfferwallUserToken, Long> {
    fun findByUserId(userId: Long): OfferwallUserToken?
    fun findByToken(token: String): OfferwallUserToken?
}
