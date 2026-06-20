package com.nomadclub.cashchat.shared.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 백엔드 공통 에러 `{ code, message }`. 화면 분기는 HTTP status가 아닌 code로 한다. */
class ApiException(
    val code: String,
    override val message: String,
    val httpStatus: Int,
) : Exception(message) {
    companion object {
        const val INSUFFICIENT_ENERGY = "INSUFFICIENT_ENERGY"
        const val INSUFFICIENT_POINTS = "INSUFFICIENT_POINTS"
        const val ALREADY_MAX_LEVEL = "ALREADY_MAX_LEVEL"
        const val CONVERSATION_NOT_FOUND = "CONVERSATION_NOT_FOUND"
        const val UNKNOWN = "UNKNOWN"
    }
}

@Serializable
private data class ErrorBody(val code: String? = null, val message: String? = null)

private val errorJson = Json { ignoreUnknownKeys = true }

fun parseApiError(httpStatus: Int, body: String): ApiException {
    val parsed = runCatching { errorJson.decodeFromString<ErrorBody>(body) }.getOrNull()
    return ApiException(
        code = parsed?.code ?: ApiException.UNKNOWN,
        message = parsed?.message ?: "요청에 실패했어요 ($httpStatus)",
        httpStatus = httpStatus,
    )
}
