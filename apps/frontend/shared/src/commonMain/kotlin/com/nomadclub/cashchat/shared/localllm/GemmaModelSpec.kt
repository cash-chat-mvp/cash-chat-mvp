package com.nomadclub.cashchat.shared.localllm

const val UNRESOLVED_GEMMA_MODEL_URL = "UNRESOLVED_GEMMA_MODEL_URL"
const val UNRESOLVED_GEMMA_MODEL_SHA256 = "UNRESOLVED_GEMMA_MODEL_SHA256"

data class GemmaModelSpec(
    val variantId: String,
    val fileName: String,
    val url: String,
    val sha256: String,
    val sizeBytes: Long,
    val minRamBytes: Long,
)

val DEFAULT_GEMMA_SPEC = GemmaModelSpec(
    variantId = "gemma4-e2b-it",
    fileName = "gemma-4-E2B-it.litertlm",
    // 자체 백엔드(OCI nginx)에서 직접 서빙 — 외부 CDN(HF/Cloudflare)이 일부 iOS/IPv6 망에서
    // QUIC 정체로 안 닿던 문제를 우회한다(앱이 이미 정상 통신하는 백엔드 도메인이라 항상 도달).
    // 원본은 litert-community/gemma-4-E2B-it-litert-lm (Apache 2.0). Remote Config `gemma_model_url`로 오버라이드 가능.
    url = "https://cashchat.duckdns.org/models/gemma-4-E2B-it.litertlm",
    sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
    sizeBytes = 2_588_147_712L,
    minRamBytes = 4L * 1024L * 1024L * 1024L,
)

fun modelFilePath(
    spec: GemmaModelSpec,
    modelDirectory: String = localModelsDir(),
): String = joinPath(modelDirectory, spec.fileName)

internal fun isUnresolvedModelValue(value: String): Boolean {
    return value.isBlank() ||
        value.contains("UNRESOLVED", ignoreCase = true) ||
        value.contains("REPLACE_WITH_REAL", ignoreCase = true)
}
