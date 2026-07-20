package com.nomadclub.cashchat.flavor

import android.app.Activity
import org.koin.core.Koin
import org.koin.core.module.Module

object FlavorModules {
    val overrides: List<Module> = emptyList()
    fun onAppCreated(koin: Koin) { /* no-op */ }
    fun onMainActivityCreated(activity: Activity) { /* no-op */ }
}
