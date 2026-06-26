package com.wnl.cashchat.api.domain.ad.service

/**
 * verifyAndStore 결과.
 * - newlyStored=true 면 이번 콜백으로 이벤트가 새로 저장된 것이다(동일 transaction_id 중복이면 false).
 * - eligibleForGranting=true 면 적립 시도 대상이다. 서명·ad_unit·timestamp 게이트를 통과한 콜백
 *   (신규 저장 + 기존 이벤트 재시도)은 true, ad_unit 불일치·timestamp 윈도우 밖처럼 미저장으로
 *   거절된 콜백은 false 다. 컨트롤러는 false 면 grantFromCallback 의 무의미한 행 락 조회를 건너뛴다.
 */
data class GoogleAdSsvVerificationResult(
    val callback: GoogleAdSsvCallback,
    val newlyStored: Boolean,
    val eligibleForGranting: Boolean,
)
