package com.nomadclub.cashchat.config

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 강제 업데이트 게이트의 핵심 로직(버전 비교) 검증.
 * 현재 버전 < 최소 요구 버전이면 게이트가 떠야 한다.
 */
class RemoteConfigVersionTest {

    @Test
    fun `현재 버전이 최소 버전보다 낮으면 음수`() {
        assertTrue(RemoteConfigManager.compareVersions("1.0.0", "1.2.0") < 0)
        assertTrue(RemoteConfigManager.compareVersions("1.0", "1.0.1") < 0)
        assertTrue(RemoteConfigManager.compareVersions("2.9", "10.0") < 0)
    }

    @Test
    fun `같은 버전은 0`() {
        assertTrue(RemoteConfigManager.compareVersions("1.2.3", "1.2.3") == 0)
        assertTrue(RemoteConfigManager.compareVersions("1.2", "1.2.0") == 0)
    }

    @Test
    fun `현재 버전이 더 높으면 양수`() {
        assertTrue(RemoteConfigManager.compareVersions("1.3.0", "1.2.9") > 0)
        assertTrue(RemoteConfigManager.compareVersions("2.0", "1.99") > 0)
    }

    @Test
    fun `접미사는 숫자 파트만 비교`() {
        assertTrue(RemoteConfigManager.compareVersions("1.2.0-rc1", "1.2.0") == 0)
        assertTrue(RemoteConfigManager.compareVersions("1.2.0", "1.3.0-beta") < 0)
    }
}
