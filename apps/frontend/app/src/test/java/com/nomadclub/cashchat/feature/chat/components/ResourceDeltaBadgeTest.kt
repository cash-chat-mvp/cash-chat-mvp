package com.nomadclub.cashchat.feature.chat.components

import com.nomadclub.cashchat.shared.chat.ChatResourceFeedback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResourceDeltaBadgeTest {

    @Test
    fun `matching energy feedback creates android badge model`() {
        val badge = ChatResourceFeedback.EnergySpent(
            eventId = 7L,
            messageId = "u123",
            amount = -1,
        ).toResourceDeltaBadge(messageId = "u123")

        assertEquals(ResourceDeltaBadgeModel(eventId = 7L, label = "⚡ -1"), badge)
    }

    @Test
    fun `non matching feedback does not create badge model`() {
        assertNull(
            ChatResourceFeedback.RewardEarned(
                eventId = 8L,
                messageId = "a123",
            ).toResourceDeltaBadge(messageId = "u123"),
        )
        assertNull(
            ChatResourceFeedback.EnergySpent(
                eventId = 9L,
                messageId = "u999",
                amount = -1,
            ).toResourceDeltaBadge(messageId = "u123"),
        )
    }
}
