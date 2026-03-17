package com.wnl.cashchat.api.domain.user.service

import com.wnl.cashchat.api.domain.user.persistence.entity.User
import com.wnl.cashchat.api.domain.user.persistence.repository.UserRepository
import org.springframework.stereotype.Service

@Service
class UserService(
    private val userRepository: UserRepository
) {
    fun findById(id: Long): User? = userRepository.findById(id).orElse(null)
}
