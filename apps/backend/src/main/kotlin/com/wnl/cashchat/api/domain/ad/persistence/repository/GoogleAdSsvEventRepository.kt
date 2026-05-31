package com.wnl.cashchat.api.domain.ad.persistence.repository

import com.wnl.cashchat.api.domain.ad.persistence.entity.GoogleAdSsvEvent
import org.springframework.data.jpa.repository.JpaRepository

interface GoogleAdSsvEventRepository : JpaRepository<GoogleAdSsvEvent, Long> {
    fun findByTransactionId(transactionId: String): GoogleAdSsvEvent?
}
