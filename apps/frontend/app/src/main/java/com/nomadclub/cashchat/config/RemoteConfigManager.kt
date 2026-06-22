package com.nomadclub.cashchat.config

import android.util.Log
import com.nomadclub.cashchat.BuildConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Firebase Remote Config 래퍼.
 *
 * fetch/activate 전략(혼합 — 설계 Q4=C):
 *  - 시작 시 캐시된 값을 즉시 activate → 이번 세션은 직전 값으로 안정 동작
 *    (광고/정책 키가 세션 도중 바뀌지 않아 UI 흔들림 없음).
 *  - 동시에 최신 값을 fetchAndActivate → 완료되면 **긴급 게이트(점검/강제업데이트)만**
 *    reactive 하게 재평가([gateState]).
 *  - 광고/정책 값은 [AppConfig.resolve]가 1회만 읽어 세션 내 변동 없음(다음 실행 반영).
 *
 * minimumFetchInterval: dev=0(즉시 테스트), prod=12h.
 */
class RemoteConfigManager(
    private val appVersionName: String,
    private val remoteConfig: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance(),
    isDebug: Boolean = BuildConfig.DEBUG,
) {
    private val _gateState = MutableStateFlow<AppGateState>(AppGateState.None)
    val gateState: StateFlow<AppGateState> = _gateState.asStateFlow()

    init {
        val settings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(if (isDebug) 0L else MIN_FETCH_INTERVAL_PROD)
            .build()
        remoteConfig.setConfigSettingsAsync(settings)
        remoteConfig.setDefaultsAsync(RemoteConfigKeys.DEFAULTS)
    }

    /** [com.nomadclub.cashchat.CashChatApplication.onCreate]에서 1회 호출. */
    fun initialize() {
        // 1) 캐시된 값 즉시 반영(네트워크 불필요) → 게이트 1차 평가
        remoteConfig.activate().addOnCompleteListener { recomputeGate() }
        // 2) 최신 값 fetch → 긴급 게이트 reactive 재평가
        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d(TAG, "fetchAndActivate 성공(updated=${task.result})")
            } else {
                Log.w(TAG, "fetch 실패 — 캐시/기본값 사용", task.exception)
            }
            recomputeGate()
        }
    }

    private fun recomputeGate() {
        _gateState.value = when {
            getBoolean(RemoteConfigKeys.MAINTENANCE_MODE) ->
                AppGateState.Maintenance(getString(RemoteConfigKeys.MAINTENANCE_MESSAGE))
            isUpdateRequired() ->
                AppGateState.ForceUpdate(getString(RemoteConfigKeys.FORCE_UPDATE_MESSAGE))
            else -> AppGateState.None
        }
    }

    private fun isUpdateRequired(): Boolean {
        val min = getString(RemoteConfigKeys.FORCE_UPDATE_MIN_VERSION).trim()
        if (min.isEmpty()) return false
        return compareVersions(appVersionName, min) < 0
    }

    fun getString(key: String): String = remoteConfig.getString(key)
    fun getBoolean(key: String): Boolean = remoteConfig.getBoolean(key)
    fun getLong(key: String): Long = remoteConfig.getLong(key)

    companion object {
        private const val TAG = "RemoteConfig"
        private const val MIN_FETCH_INTERVAL_PROD = 43_200L // 12h

        /**
         * semver 단순 비교. a<b → 음수, a==b → 0, a>b → 양수.
         * 각 파트의 숫자만 비교하며 접미사(`-rc1` 등)는 0으로 취급한다.
         */
        fun compareVersions(a: String, b: String): Int {
            val pa = a.split(".")
            val pb = b.split(".")
            val n = maxOf(pa.size, pb.size)
            for (i in 0 until n) {
                val x = pa.getOrNull(i)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
                val y = pb.getOrNull(i)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
                if (x != y) return x - y
            }
            return 0
        }
    }
}
