package com.nomadclub.cashchat.di

import com.nomadclub.cashchat.config.AppConfig
import org.koin.dsl.module

/**
 * 앱 전역 Koin 모듈.
 * 각 Epic에서 필요한 의존성을 여기에 추가합니다.
 *
 * Epic B: AdManager (광고 관리)
 * Epic C: AnalyticsManager, RemoteConfigManager
 * Epic D: SentryManager
 * Epic E: ChatRepository, HttpClient
 */
val appModule = module {
    // AppConfig — BuildConfig 기반 환경별 설정 싱글톤
    single { AppConfig.fromBuildConfig() }
}
