package com.nomadclub.cashchat.shared.localllm

import android.app.ActivityManager
import android.content.Context
import java.io.File

object AndroidLocalLlmContext {
    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun filesDirPath(): String {
        val context = appContext
        if (context != null) {
            return context.filesDir.absolutePath
        }
        return File(System.getProperty("java.io.tmpdir") ?: "/tmp", "cashchat-shared").absolutePath
    }

    fun totalRamBytes(): Long {
        val context = appContext ?: return 0L
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 0L
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        return info.totalMem
    }
}
