package com.wnl.cashchat.api.common.security

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.core.Authentication

/**
 * 인증 principal 에서 Long userId 를 추출한다.
 *
 * JwtAuthenticationFilter 가 인증된 요청의 principal 에 Long userId 를 세팅한다(도달 가능성 낮은 방어 경로).
 * principal 이 Long 이 아니면 인증 문제이므로, 500 이 아니라 401 흐름을 타도록 AuthenticationException 을 던진다.
 */
fun Authentication.userId(): Long =
    principal as? Long ?: throw AuthenticationCredentialsNotFoundException("Invalid authenticated principal")
