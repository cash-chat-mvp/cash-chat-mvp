package com.nomadclub.cashchat.shared.invite

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 친구 초대 상태 보유 + redeem 오케스트레이션.
 * @param onRewardChanged redeem 성공 시 잔액/에너지가 바뀌었을 수 있어 HUD 등을 갱신하는 콜백.
 * iOS 에서 호출하므로 suspend 는 @Throws.
 */
class InviteStore(
    private val repo: InviteRepository,
    private val onRewardChanged: suspend () -> Unit,
) {
    private val _status = MutableStateFlow<InviteStatus?>(null)
    val status: StateFlow<InviteStatus?> = _status.asStateFlow()

    @Throws(Exception::class)
    suspend fun refresh(): InviteStatus = repo.getInviteStatus().also { _status.value = it }

    /** 추천 코드 입력. 성공 시 보상 콜백 + status 갱신. */
    @Throws(Exception::class)
    suspend fun redeem(code: String): RedeemResult {
        val result = repo.redeemCode(code)
        if (result.success) {
            onRewardChanged()
            _status.value = repo.getInviteStatus()
        }
        return result
    }
}
