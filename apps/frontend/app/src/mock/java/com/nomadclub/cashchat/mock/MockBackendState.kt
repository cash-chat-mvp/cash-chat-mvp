package com.nomadclub.cashchat.mock

/**
 * 인앱 Fake 백엔드의 가변 상태. Koin single 로 공유되어
 * FakeBackendEngine(응답 생성)과 Fake SDK(보상 반영)가 같은 인스턴스를 본다.
 * scenario 는 Maestro launchArguments 의 "scenario" extra 로 주입(MainActivity → FlavorModules).
 */
class MockBackendState {
    @Volatile var scenario: String = "happy"      // happy | chat_error | ad_quota_exceeded | offerwall_fail
    @Volatile var pointsBalance: Long = 0
    @Volatile var energy: Int = 10
    @Volatile var maxEnergy: Int = 10
    @Volatile var usedToday: Int = 0
    @Volatile var dailyLimit: Int = 5

    val remaining: Int get() = (dailyLimit - usedToday).coerceAtLeast(0)

    /** ad_quota_exceeded 시나리오면 한도를 소진 상태로 초기화. */
    fun applyScenarioDefaults() {
        if (scenario == "ad_quota_exceeded") usedToday = dailyLimit
    }
}
