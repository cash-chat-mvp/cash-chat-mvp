package com.wnl.cashchat.api.domain.evolution.properties

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class EvolutionTimingConfiguration {
    @Bean
    fun evolutionTimingConfig(properties: EvolutionProperties): EvolutionProperties.TimingConfig =
        properties.timing
}
