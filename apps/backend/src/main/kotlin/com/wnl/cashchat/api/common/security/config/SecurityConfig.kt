package com.wnl.cashchat.api.common.security.config

import com.wnl.cashchat.api.common.security.filter.JwtAuthenticationFilter
import com.wnl.cashchat.api.common.security.jwt.JwtTokenHandler
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtTokenHandler: JwtTokenHandler,
    private val environment: Environment
) {

    companion object {
        private const val PROD_PROFILE = "prod"

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
        // Swagger 경로는 비-prod 프로필에서만 인증 없이 접근 가능하게 둔다.
        // prod에서는 springdoc 자체가 비활성화되며(application-prod.yaml), 경로도 공개하지 않는다.
        val isSwaggerEnabled = !environment.activeProfiles.contains(PROD_PROFILE)

        http
            .csrf { it.disable() }
            .headers { it.frameOptions { frame -> frame.disable() } } // H2
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling {
                it.authenticationEntryPoint { _, response, _ ->
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
                }
                it.accessDeniedHandler { _, response, _ ->
                    response.sendError(HttpServletResponse.SC_FORBIDDEN)
                }
            }
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter::class.java)
            .authorizeHttpRequests {
                val publicPaths = mutableListOf(
                    "/api/auth/guest",
                    "/api/auth/callback/google",
                    "/api/auth/callback/apple",
                    "/api/auth/refresh",
                    "/favicon.ico"
                )
                if (isSwaggerEnabled) {
                    publicPaths.addAll(SWAGGER_PATHS)
                }
                it.requestMatchers("/api/auth/logout").authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/ads/google/ssv").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/offerwall/tnk/callback").permitAll()
                    .requestMatchers(*publicPaths.toTypedArray()).permitAll()
                    .anyRequest().authenticated()
            }

        return http.build()
    }
}
