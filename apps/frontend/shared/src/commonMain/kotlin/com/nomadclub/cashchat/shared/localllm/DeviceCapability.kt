package com.nomadclub.cashchat.shared.localllm

sealed interface CapabilityResult {
    data object Ok : CapabilityResult

    data class Insufficient(val reason: String) : CapabilityResult
}

private const val STORAGE_HEADROOM_MULTIPLIER = 1.1

fun canRunGemma(
    spec: GemmaModelSpec,
    ramBytes: Long = totalRamBytes(),
    freeStorageBytes: Long = availableStorageBytes(),
): CapabilityResult {
    if (ramBytes in 1 until spec.minRamBytes) {
        return CapabilityResult.Insufficient("RAM is below ${spec.minRamBytes} bytes.")
    }

    val requiredStorageBytes = (spec.sizeBytes.toDouble() * STORAGE_HEADROOM_MULTIPLIER).toLong()
    if (freeStorageBytes < requiredStorageBytes) {
        return CapabilityResult.Insufficient("Storage is below $requiredStorageBytes bytes.")
    }

    return CapabilityResult.Ok
}
