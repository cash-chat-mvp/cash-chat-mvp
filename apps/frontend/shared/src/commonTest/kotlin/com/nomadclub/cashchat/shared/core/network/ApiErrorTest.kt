package com.nomadclub.cashchat.shared.core.network

import kotlin.test.Test
import kotlin.test.assertEquals

class ApiErrorTest {

    @Test
    fun `에러 본문을 ApiException으로 파싱한다`() {
        val exception = parseApiError(
            httpStatus = 409,
            body = """{ "code": "INSUFFICIENT_ENERGY", "message": "에너지가 부족합니다." }""",
        )
        assertEquals("INSUFFICIENT_ENERGY", exception.code)
        assertEquals(409, exception.httpStatus)
        assertEquals("에너지가 부족합니다.", exception.message)
    }

    @Test
    fun `본문이 JSON이 아니면 UNKNOWN 코드로 폴백한다`() {
        val exception = parseApiError(httpStatus = 500, body = "Internal Server Error")
        assertEquals("UNKNOWN", exception.code)
        assertEquals(500, exception.httpStatus)
    }
}
