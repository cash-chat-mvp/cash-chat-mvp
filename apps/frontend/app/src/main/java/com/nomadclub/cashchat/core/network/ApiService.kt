package com.nomadclub.cashchat.core.network

import com.nomadclub.cashchat.data.model.AuthResponse
import com.nomadclub.cashchat.data.model.TokenRefreshRequest
import com.nomadclub.cashchat.data.model.UserResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ApiService {

    @POST("api/auth/guest")
    suspend fun loginAsGuest(
        @Query("deviceToken") deviceToken: String
    ): Response<AuthResponse>

    @GET("api/auth/callback/google")
    suspend fun loginWithGoogle(
        @Query("code") code: String,
        @Query("deviceToken") deviceToken: String? = null
    ): Response<AuthResponse>

    @POST("api/auth/refresh")
    suspend fun refreshToken(
        @Body request: TokenRefreshRequest
    ): Response<AuthResponse>

    @GET("api/users/me")
    suspend fun getMe(): Response<UserResponse>
}
