package com.nomadclub.cashchat.flavor

import android.app.Activity
import com.nomadclub.cashchat.core.data.TokenDataStore
import com.nomadclub.cashchat.mock.MockBackendState
import com.nomadclub.cashchat.mock.mockModule
import com.nomadclub.cashchat.shared.auth.model.AuthResponse
import kotlinx.coroutines.runBlocking
import org.koin.core.Koin
import org.koin.core.module.Module

object FlavorModules {
    val overrides: List<Module> = listOf(mockModule)

    /** 로그인/네비 게이트 우회: MEMBER 세션을 DataStore 에 직접 심는다. */
    fun onAppCreated(koin: Koin) {
        runBlocking {
            koin.get<TokenDataStore>().saveAuthResponse(
                AuthResponse(userId = 1L, role = "MEMBER", accessToken = "mock-token", refreshToken = null),
            )
        }
    }

    /** Maestro launchArguments 의 "scenario" extra → MockBackendState 반영. */
    fun onMainActivityCreated(activity: Activity) {
        val scenario = activity.intent?.getStringExtra("scenario") ?: "happy"
        val koin = org.koin.core.context.GlobalContext.get()
        koin.get<MockBackendState>().apply {
            this.scenario = scenario
            applyScenarioDefaults()
        }
    }
}
