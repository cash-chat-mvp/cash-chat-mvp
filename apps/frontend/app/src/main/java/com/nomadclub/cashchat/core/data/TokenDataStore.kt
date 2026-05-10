package com.nomadclub.cashchat.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nomadclub.cashchat.shared.auth.model.AuthResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth")

class TokenDataStore(private val context: Context) {

    private val store get() = context.dataStore

    private companion object {
        val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val KEY_ROLE = stringPreferencesKey("role")
        val KEY_USER_ID = longPreferencesKey("user_id")
        val KEY_DEVICE_TOKEN = stringPreferencesKey("device_token")
    }

    val accessTokenFlow: Flow<String?> = store.data.map { it[KEY_ACCESS_TOKEN] }
    val roleFlow: Flow<String?> = store.data.map { it[KEY_ROLE] }

    suspend fun saveAuthResponse(response: AuthResponse) {
        store.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = response.accessToken
            prefs[KEY_ROLE] = response.role
            prefs[KEY_USER_ID] = response.userId
            val refreshToken = response.refreshToken
            if (refreshToken != null) {
                prefs[KEY_REFRESH_TOKEN] = refreshToken
            } else {
                prefs.remove(KEY_REFRESH_TOKEN)
            }
        }
    }

    suspend fun getOrCreateDeviceToken(): String {
        val existing = store.data.first()[KEY_DEVICE_TOKEN]
        if (existing != null) return existing
        val new = UUID.randomUUID().toString()
        store.edit { it[KEY_DEVICE_TOKEN] = new }
        return new
    }

    suspend fun clearTokens() {
        store.edit { prefs ->
            prefs.remove(KEY_ACCESS_TOKEN)
            prefs.remove(KEY_REFRESH_TOKEN)
            prefs.remove(KEY_ROLE)
            prefs.remove(KEY_USER_ID)
        }
    }

    // OkHttp Authenticator/Interceptor는 백그라운드 스레드에서 동기적으로 호출되므로 runBlocking 사용
    fun getAccessTokenBlocking(): String? = runBlocking { store.data.first()[KEY_ACCESS_TOKEN] }
    fun getRefreshTokenBlocking(): String? = runBlocking { store.data.first()[KEY_REFRESH_TOKEN] }
    fun getRoleBlocking(): String? = runBlocking { store.data.first()[KEY_ROLE] }
    fun getUserIdBlocking(): Long? = runBlocking { store.data.first()[KEY_USER_ID] }
    fun getDeviceTokenBlocking(): String? = runBlocking { store.data.first()[KEY_DEVICE_TOKEN] }

    /**
     * 동기 경로(TokenAuthenticator)에서도 "없으면 생성" 보장.
     * 신규 설치 직후 게스트 토큰 갱신 시 deviceToken이 없는 엣지케이스 방어.
     */
    fun getOrCreateDeviceTokenBlocking(): String = runBlocking { getOrCreateDeviceToken() }
}
