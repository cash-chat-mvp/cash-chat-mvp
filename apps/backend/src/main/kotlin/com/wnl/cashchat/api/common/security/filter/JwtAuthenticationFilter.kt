package com.wnl.cashchat.api.common.security.filter

import com.wnl.cashchat.api.common.security.jwt.JwtTokenHandler
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import kotlin.text.startsWith
import kotlin.text.substring

class JwtAuthenticationFilter(
    private val jwtTokenHandler: JwtTokenHandler
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {

        val authToken = extractAuthToken(request)

        if (authToken != null && jwtTokenHandler.validateToken(authToken)) {
            try {
                val userId = jwtTokenHandler.getUserId(authToken)
                val role = jwtTokenHandler.getRole(authToken)

                SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    listOf(SimpleGrantedAuthority("ROLE_${role.name}"))
                )
            } catch (e: Exception) {
                log.debug("Malformed JWT claims, treating as anonymous: ${e.message}")
                SecurityContextHolder.clearContext()
            }
        }

        filterChain.doFilter(request, response)
    }

    private fun extractAuthToken(request: HttpServletRequest): String? {
        val authHeader = request.getHeader("Authorization") ?: return null
        return if (authHeader.startsWith("Bearer ")) authHeader.substring(7) else null
    }
}
