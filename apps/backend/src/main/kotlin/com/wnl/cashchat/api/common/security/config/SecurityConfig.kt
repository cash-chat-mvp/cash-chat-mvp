package com.wnl.cashchat.api.common.security.config

import com.wnl.cashchat.api.common.security.filter.JwtAuthenticationFilter
import com.wnl.cashchat.api.common.security.jwt.JwtTokenHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtTokenHandler: JwtTokenHandler
) {

    companion object {
        private val SWAGGER_PATHS = arrayOf(
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
        )
    }

    @Bean
    fun jwtAuthenticationFilter(): JwtAuthenticationFilter =
        JwtAuthenticationFilter(jwtTokenHandler)

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .headers { it.frameOptions { frame -> frame.disable() } } // H2
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter::class.java)
            .authorizeHttpRequests {
                it.requestMatchers(
                    *SWAGGER_PATHS,
                    "/api/auth/**",
                    "/favicon.ico",
                ).permitAll()
                    .anyRequest().authenticated()
            }

        return http.build()

    }

}
