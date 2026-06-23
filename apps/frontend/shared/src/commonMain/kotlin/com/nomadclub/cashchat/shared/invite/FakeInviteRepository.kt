package com.nomadclub.cashchat.shared.invite

/**
 * 로컬 스텁. 고정 코드·보상값 보유. redeemCode 는 형식·자기코드·중복만 검증하고 성공/실패를 모사한다.
 * 실제 적립·서버 검증은 BE 몫 — UI 검증용.
 */
class FakeInviteRepository : InviteRepository {

    private companion object {
        const val MY_CODE = "ABC123"
        const val REWARD_COIN = 500
        const val REWARD_ENERGY = 10
    }

    private var redeemed = false

    override suspend fun getInviteStatus(): InviteStatus = InviteStatus(
        myCode = MY_CODE,
        invitedCount = 3,
        redeemAvailable = !redeemed,
        rewardCoin = REWARD_COIN,
        rewardEnergy = REWARD_ENERGY,
    )

    override suspend fun redeemCode(code: String): RedeemResult {
        val trimmed = code.trim()
        return when {
            trimmed.isEmpty() -> RedeemResult(false, 0, "코드를 입력해주세요")
            trimmed.equals(MY_CODE, ignoreCase = true) -> RedeemResult(false, 0, "본인 코드는 사용할 수 없어요")
            redeemed -> RedeemResult(false, 0, "이미 추천 코드를 사용했어요")
            else -> {
                redeemed = true
                RedeemResult(true, REWARD_ENERGY, null)
            }
        }
    }
}
