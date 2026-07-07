package com.nomadclub.cashchat.offerwall

import android.app.Activity

/** 오퍼월 진입 seam. 프로덕션은 [TnkOfferwallManager](TNK), 테스트는 Fake. */
interface OfferwallLauncher {
    suspend fun launch(activity: Activity): Result<Unit>
}
