package com.wnl.cashchat.api.domain.ad.service

import com.wnl.cashchat.api.domain.ad.exception.InvalidGoogleAdSsvCallbackException
import org.springframework.stereotype.Component
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@Component
class GoogleAdSsvQueryParser {
    fun parse(rawQueryString: String?): GoogleAdSsvCallback {
        val rawQuery = rawQueryString?.takeIf { it.isNotBlank() }
            ?: throw InvalidGoogleAdSsvCallbackException("Google Ad SSV query string must not be blank")
        val parts = rawQuery.split("&")
        validateNoDuplicateKeys(parts)
        validateSignaturePosition(parts)

        val parameters = parts.associate { part ->
            val key = parameterKey(part)
            key to decode(parameterValue(part))
        }

        val rawSignedPayload = rawQuery.substringBefore("&signature=")
        if (rawSignedPayload == rawQuery) {
            throw InvalidGoogleAdSsvCallbackException("Google Ad SSV query string must include signature")
        }
        // Google 은 percent-encoding 된 전송 문자열이 아니라 URL 디코딩된 콘텐츠에 서명한다
        // (예: reward_item 이 한글 '에너지' 면 %EC%97%90.. 가 아니라 '에너지' 에 서명). 따라서
        // 서명 검증용 페이로드는 raw 가 아니라 디코딩한 값으로 재구성한다. 구조 구분자(&, =)는
        // percent-encoding 대상이 아니므로 그대로 유지되고, 값의 %XX 만 디코딩된다.
        val signedPayload = decode(rawSignedPayload)

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

    private fun validateNoDuplicateKeys(parts: List<String>) {
        val keys = mutableSetOf<String>()
        parts.forEach { part ->
            val key = parameterKey(part)
            if (!keys.add(key)) {
                throw InvalidGoogleAdSsvCallbackException("Google Ad SSV query string has duplicate $key")
            }
        }
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

    private fun decode(value: String): String =
        try {
            URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8)
        } catch (exception: IllegalArgumentException) {
            throw InvalidGoogleAdSsvCallbackException(
                message = "Google Ad SSV query string contains malformed percent encoding",
                cause = exception,
            )
        }
}
