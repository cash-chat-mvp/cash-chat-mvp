package com.nomadclub.cashchat.core.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.util.UUID

/**
 * CC-154: 앱 최초 실행 시 UUID v4 생성 및 Android Keystore 저장.
 *
 * - UUID는 앱 인스톨마다 한 번 생성되어 이후에는 재사용됩니다.
 * - 데이터는 Android Keystore 기반 AES256-GCM MasterKey로 암호화된
 *   EncryptedSharedPreferences에 저장됩니다.
 * - 앱 재설치 시 UUID가 재생성되어 새로운 Guest 계정으로 처리됩니다.
 *
 * security-crypto:1.0.0 API 사용:
 *   MasterKeys.getOrCreate()  → Android Keystore 키 별칭(String) 반환
 *   EncryptedSharedPreferences.create(fileName, masterKeyAlias, context, ...)
 */
class DeviceTokenManager(context: Context) {

    private val prefs: SharedPreferences by lazy {
        // AES256-GCM 스펙으로 Android Keystore 키 별칭 생성 (이미 있으면 재사용)
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

        EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * 저장된 DeviceToken을 반환합니다.
     * 없으면 UUID v4를 새로 생성하여 Keystore에 저장한 뒤 반환합니다.
     */
    fun getOrCreateDeviceToken(): String {
        return prefs.getString(KEY_DEVICE_TOKEN, null)
            ?: UUID.randomUUID().toString().also { newUuid ->
                prefs.edit().putString(KEY_DEVICE_TOKEN, newUuid).apply()
            }
    }

    /** 저장된 DeviceToken만 조회 (없으면 null) */
    fun getDeviceToken(): String? = prefs.getString(KEY_DEVICE_TOKEN, null)

    companion object {
        private const val PREFS_NAME = "cashchat_device_prefs"
        private const val KEY_DEVICE_TOKEN = "device_token"
    }
}
