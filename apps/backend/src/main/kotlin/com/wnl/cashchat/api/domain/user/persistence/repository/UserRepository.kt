package com.wnl.cashchat.api.domain.user.persistence.repository

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param


interface UserRepository : JpaRepository<User, Long> {

    fun findByDeviceToken(deviceToken: String): User?

    fun findByProviderAndProviderId(provider: AuthProviderType, providerId: String): User?
    fun findByEmail(email: String): User?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): User?

}
