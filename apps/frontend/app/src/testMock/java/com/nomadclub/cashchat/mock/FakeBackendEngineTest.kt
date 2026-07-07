package com.nomadclub.cashchat.mock

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeBackendEngineTest {
    private fun client(state: MockBackendState) = HttpClient(fakeBackendEngine(state)) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    @Test
    fun `points balance reflects state`() = runTest {
        val state = MockBackendState().apply { pointsBalance = 1500 }
        val body = client(state).get("https://mock.local/api/points/me").bodyAsText()
        assertTrue(body.contains("1500"))
    }

    @Test
    fun `quota reflects remaining`() = runTest {
        val state = MockBackendState().apply { usedToday = 5; dailyLimit = 5 }
        val body = client(state).get("https://mock.local/api/ads/reward/quota").bodyAsText()
        assertTrue(body.contains("\"remaining\":0"))
    }
}
