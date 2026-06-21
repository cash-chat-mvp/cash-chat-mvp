// RewardAlreadySettledException.kt
package com.wnl.cashchat.api.domain.chat.exception
class RewardAlreadySettledException(val messageId: String) :
    RuntimeException("Reward already settled or in progress for messageId=$messageId")
