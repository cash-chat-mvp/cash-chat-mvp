package com.wnl.cashchat.api.domain.user.persistence.repository

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import org.springframework.data.jpa.repository.JpaRepository


interface UserRepository : JpaRepository<User, Long> {

    fun findByDeviceToken(deviceToken: String): User?

    fun findByProviderAndProviderId(provider: AuthProviderType, providerId: String): User?
    fun findByEmail(email: String): User?

}
