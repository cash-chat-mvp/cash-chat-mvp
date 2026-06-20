package com.wnl.cashchat.api.domain.ad.web.response

import com.wnl.cashchat.api.domain.ad.persistence.entity.AdRewardNonce
import java.time.Instant

data class IssueNonceResponse(
    val nonce: String,
    val expiresAt: Instant,
) {
    companion object {
        fun from(entity: AdRewardNonce): IssueNonceResponse =
            IssueNonceResponse(nonce = entity.nonce, expiresAt = entity.expiresAt)
    }
}
