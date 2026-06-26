package com.nomadclub.cashchat.shared.evolution

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EvolutionStateDtoTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `currentExp 없는 응답은 null로 역직렬화된다`() {
        val dto = json.decodeFromString<EvolutionStateDto>(
            """{ "level": 2, "isMaxLevel": false, "nextAttemptCost": 1200, "nextSuccessRate": 0.5 }"""
        )
        assertEquals(2, dto.level)
        assertNull(dto.currentExp)
    }

    @Test
    fun `currentExp 있는 응답은 값으로 역직렬화된다`() {
        val dto = json.decodeFromString<EvolutionStateDto>(
            """{ "level": 2, "isMaxLevel": false, "nextAttemptCost": 1200, "nextSuccessRate": 0.5, "currentExp": 3400 }"""
        )
        assertEquals(3400L, dto.currentExp)
    }
}
