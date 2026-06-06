package com.wnl.cashchat.api.domain.ad.service

/**
 * verifyAndStore 결과. newlyStored=true 면 이번 콜백으로 이벤트가 새로 저장된 것이며,
 * 리워드 적립 대상이다. false 면 동일 transaction_id 중복(이미 처리)으로 적립을 건너뛴다.
 */
data class GoogleAdSsvVerificationResult(
    val callback: GoogleAdSsvCallback,
    val newlyStored: Boolean,
)
