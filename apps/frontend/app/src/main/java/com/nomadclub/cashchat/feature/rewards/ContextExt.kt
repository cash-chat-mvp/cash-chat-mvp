package com.nomadclub.cashchat.feature.rewards

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * Compose 의 `LocalContext.current` 는 테마/DI 로 인해 `ContextWrapper` 로 감싸져 있을 수 있어
 * 단순 `as? Activity` 캐스팅이 실제 Activity 임에도 null 을 반환할 수 있다.
 * ContextWrapper 체인을 재귀적으로 풀어 실제 Activity 를 안전하게 찾는다.
 */
internal fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
