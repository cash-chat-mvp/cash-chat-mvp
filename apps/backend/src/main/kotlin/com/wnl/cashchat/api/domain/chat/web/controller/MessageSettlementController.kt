package com.wnl.cashchat.api.domain.chat.web.controller

import com.wnl.cashchat.api.domain.chat.exception.SettlementNotFoundException
import com.wnl.cashchat.api.domain.chat.service.ChatRewardSettlementService
import com.wnl.cashchat.api.domain.chat.web.response.MessageSettlementResponse
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/messages")
class MessageSettlementController(private val settlementService: ChatRewardSettlementService) {

    @GetMapping("/{messageId}/settlement")
    fun settlement(authentication: Authentication, @PathVariable messageId: String): MessageSettlementResponse =
        settlementService.findForUser(authentication.userId(), messageId)
            ?: throw SettlementNotFoundException(messageId)

    private fun Authentication.userId(): Long =
        principal as? Long ?: throw IllegalArgumentException("Invalid authenticated principal")
}
