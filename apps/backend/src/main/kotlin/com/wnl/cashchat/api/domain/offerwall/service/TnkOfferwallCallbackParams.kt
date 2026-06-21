package com.wnl.cashchat.api.domain.offerwall.service

/**
 * TNK 서버 포스트백 파라미터(HTTP POST form body). md_chk = MD5(appKey + mdUserNm + seqId) (lowercase hex) —
 * TNK 공식 가이드(tnk_sdk_rwd_br / ios-sdk-rwd2 / s2s-api-rwd)로 확인됨. TNK 는 HTTP 200 응답만으로 성공 판정하며
 * 본문은 무시한다(ack.successBody="SUCCESS" 는 무해한 관례). rawQuery 는 원장 기록용 콜백 원본 표현.
 */
data class TnkOfferwallCallbackParams(
    val seqId: String,
    val payPnt: Long,
    val mdUserNm: String,
    val mdChk: String,
    val rawQuery: String,
)
