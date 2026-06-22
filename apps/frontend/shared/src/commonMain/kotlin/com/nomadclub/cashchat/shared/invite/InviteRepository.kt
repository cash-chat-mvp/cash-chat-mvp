package com.nomadclub.cashchat.shared.invite

/**
 * 친구 초대 데이터 소스. 지금은 FakeInviteRepository(로컬 스텁), BE 준비 시 RemoteInviteRepository 로 교체.
 * iOS 에서 호출하므로 suspend 는 @Throws.
 */
interface InviteRepository {
    @Throws(Exception::class) suspend fun getInviteStatus(): InviteStatus
    @Throws(Exception::class) suspend fun redeemCode(code: String): RedeemResult
}
