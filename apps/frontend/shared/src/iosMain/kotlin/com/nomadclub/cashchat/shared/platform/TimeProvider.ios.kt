package com.nomadclub.cashchat.shared.platform

import platform.CoreFoundation.CFAbsoluteTimeGetCurrent

private const val APPLE_EPOCH_TO_UNIX_SECONDS = 978307200.0

actual fun currentTimeMillis(): Long = ((CFAbsoluteTimeGetCurrent() + APPLE_EPOCH_TO_UNIX_SECONDS) * 1000.0).toLong()
