package com.wnl.cashchat.api.domain.ad.service

import com.wnl.cashchat.api.domain.ad.exception.InvalidGoogleAdSsvCallbackException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class GoogleAdSsvQueryParser {
    fun parse(rawQueryString: String?): GoogleAdSsvCallback {
        val rawQuery = rawQueryString?.takeIf { it.isNotBlank() }
            ?: throw InvalidGoogleAdSsvCallbackException("Google Ad SSV query string must not be blank")
        val parts = rawQuery.split("&")
        validateSignaturePosition(parts)

        val parameters = parts.associate { part ->
            val key = parameterKey(part)
            key to decode(parameterValue(part))
        }

        val signedPayload = rawQuery.substringBefore("&signature=")
        if (signedPayload == rawQuery) {
            throw InvalidGoogleAdSsvCallbackException("Google Ad SSV query string must include signature")
        }

        return GoogleAdSsvCallback(
            adUnit = required(parameters, "ad_unit"),
            rewardAmount = required(parameters, "reward_amount").toIntOrNull()
                ?: throw InvalidGoogleAdSsvCallbackException("Google Ad SSV reward_amount must be numeric"),
            rewardItem = required(parameters, "reward_item"),
            timestamp = required(parameters, "timestamp").toLongOrNull()
                ?: throw InvalidGoogleAdSsvCallbackException("Google Ad SSV timestamp must be numeric"),
            transactionId = required(parameters, "transaction_id"),
            userId = required(parameters, "user_id"),
            signature = required(parameters, "signature"),
            keyId = required(parameters, "key_id").toLongOrNull()
                ?: throw InvalidGoogleAdSsvCallbackException("Google Ad SSV key_id must be numeric"),
            rawQueryString = rawQuery,
            signedPayload = signedPayload,
        )
    }

    private fun validateSignaturePosition(parts: List<String>) {
        if (parts.size < 2 ||
            parameterKey(parts[parts.lastIndex - 1]) != "signature" ||
            parameterKey(parts.last()) != "key_id"
        ) {
            throw InvalidGoogleAdSsvCallbackException(
                "Google Ad SSV signature and key_id must be the last two parameters",
            )
        }
    }

    private fun required(
        parameters: Map<String, String>,
        key: String,
    ): String = parameters[key]?.takeIf { it.isNotBlank() }
        ?: throw InvalidGoogleAdSsvCallbackException("Google Ad SSV query string is missing $key")

    private fun parameterKey(part: String): String = part.substringBefore("=")

    private fun parameterValue(part: String): String = part.substringAfter("=", "")

    private fun decode(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8)
}
