package com.nomadclub.cashchat.offerwall

import android.app.Activity
import android.util.Log
import com.nomadclub.cashchat.shared.offerwall.OfferwallApi
import com.tnkfactory.ad.TnkOfferwall

/**
 * TNK 오퍼월 노출 오케스트레이션.
 *  1. BE 에서 불투명 사용자 토큰 발급
 *  2. TNK SDK setUserName 에 토큰 설정
 *  3. 오퍼월 전체화면(AdWallActivity) 노출
 * 토큰 발급 실패 시 오퍼월을 띄우지 않는다(잘못된 사용자로 적립되는 사고 방지).
 *
 * 주의: SDK 8.09.07 의 `TnkOfferwall` 은 정적 객체가 아니라 Context 를 받는 인스턴스다.
 * `setUserName(String)` / `startOfferwallActivity(Context)` 는 인스턴스 메서드이므로
 * Activity 로 생성한 인스턴스에서 호출한다.
 */
class TnkOfferwallManager(private val offerwallApi: OfferwallApi) {
    suspend fun launch(activity: Activity): Result<Unit> = runCatching {
        val token = offerwallApi.issueUserToken().token
        val offerwall = TnkOfferwall(activity)
        offerwall.setUserName(token)
        offerwall.startOfferwallActivity(activity)
    }.onFailure {
        Log.e("TnkOfferwall", "오퍼월 진입 실패", it)
    }
}
