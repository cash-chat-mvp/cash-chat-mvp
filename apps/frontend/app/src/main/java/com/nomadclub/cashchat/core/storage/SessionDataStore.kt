package com.nomadclub.cashchat.core.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// DataStore 인스턴스는 Context 당 하나만 존재해야 합니다 (top-level 프로퍼티)
private val Context.dataStore by preferencesDataStore(name = "cashchat_session")

/**
 * CC-155: Jetpack DataStore를 이용한 게스트 UUID 및 세션 정보 로컬 관리.
 *
 * DataStore는 SharedPreferences의 비동기·타입 안전 대체재입니다.
 * Flow를 통해 값 변경을 실시간으로 수집할 수 있습니다.
 *
 * 저장 항목:
 * - accessToken : JWT (모든 API 호출에 Bearer로 첨부)
 * - userId      : 서버 발급 사용자 ID
 * - userRole    : "GUEST" | "MEMBER" | "ADMIN"
 * - refreshToken: Rotation 방식 (MEMBER만 발급, GUEST는 null)
 */
class SessionDataStore(private val context: Context) {

    /** 현재 저장된 accessToken을 Flow로 관찰 */
    val accessToken: Flow<String?> = context.dataStore.data
        .map { prefs -> prefs[KEY_ACCESS_TOKEN] }

    /** 현재 저장된 userId를 Flow로 관찰 */
    val userId: Flow<Long?> = context.dataStore.data
        .map { prefs -> prefs[KEY_USER_ID] }

    /** 현재 저장된 userRole을 Flow로 관찰 ("GUEST" | "MEMBER") */
    val userRole: Flow<String?> = context.dataStore.data
        .map { prefs -> prefs[KEY_USER_ROLE] }

    /** 현재 저장된 refreshToken을 Flow로 관찰 (GUEST는 항상 null) */
    val refreshToken: Flow<String?> = context.dataStore.data
        .map { prefs -> prefs[KEY_REFRESH_TOKEN] }

    /**
     * 세션 정보를 DataStore에 저장합니다.
     * AuthResponse 수신 직후 호출하세요.
     */
    suspend fun saveSession(
        accessToken: String,
        userId: Long,
        role: String,
        refreshToken: String? = null
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = accessToken
            prefs[KEY_USER_ID] = userId
            prefs[KEY_USER_ROLE] = role
            if (refreshToken != null) {
                prefs[KEY_REFRESH_TOKEN] = refreshToken
            } else {
                prefs.remove(KEY_REFRESH_TOKEN)  // GUEST는 refreshToken 없음
            }
        }
    }

    /**
     * 저장된 세션을 모두 삭제합니다 (로그아웃).
     * DeviceToken은 삭제하지 않습니다 (재로그인 시 동일 UUID 재사용).
     */
    suspend fun clearSession() {
        context.dataStore.edit { prefs -> prefs.clear() }
    }

    companion object {
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val KEY_USER_ID = longPreferencesKey("user_id")
        private val KEY_USER_ROLE = stringPreferencesKey("user_role")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
    }
}
