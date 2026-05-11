package com.wnl.cashchat.api.domain.user.web.controller

import com.wnl.cashchat.api.domain.user.service.UserService
import com.wnl.cashchat.api.domain.user.web.response.UserResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/users")
class UserController(
    private val userService: UserService
) {

    @GetMapping("/me")
    fun getMe(authentication: Authentication): ResponseEntity<UserResponse> {

        val userId = authentication.principal as? Long
            ?: return ResponseEntity.badRequest().build()
        val user = userService.findById(userId) ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(UserResponse.from(user))

    }

}
