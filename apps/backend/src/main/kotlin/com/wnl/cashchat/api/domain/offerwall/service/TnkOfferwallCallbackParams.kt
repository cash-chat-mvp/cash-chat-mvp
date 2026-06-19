package com.wnl.cashchat.api.domain.offerwall.service

/**
 * TNK 서버 포스트백 파라미터. md_chk = MD5(appKey + mdUserNm + seqId) (가정, spec 검증 TODO).
 * rawQuery 는 원장 기록용 콜백 원본 표현.
 */
data class TnkOfferwallCallbackParams(
    val seqId: String,
    val payPnt: Long,
    val mdUserNm: String,
    val mdChk: String,
    val rawQuery: String,
)
