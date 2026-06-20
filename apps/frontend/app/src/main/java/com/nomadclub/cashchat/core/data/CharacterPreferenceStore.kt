package com.nomadclub.cashchat.core.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.characterDataStore by preferencesDataStore(name = "character_prefs")

/** 캐릭터 닉네임 로컬 저장. BE PATCH /api/users/me/character-name 배포 시 동기화 추가(P3-2). */
class CharacterPreferenceStore(private val context: Context) {
    private val keyName = stringPreferencesKey("character_name")

    val name: Flow<String> = context.characterDataStore.data.map { it[keyName] ?: "미래" }

    suspend fun setName(value: String) {
        val trimmed = value.trim().take(10)
        if (trimmed.isEmpty()) return
        context.characterDataStore.edit { it[keyName] = trimmed }
    }
}
