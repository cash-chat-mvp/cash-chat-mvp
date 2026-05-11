package com.wnl.cashchat.api.domain.auth.persistence.entity

import com.wnl.cashchat.api.domain.user.persistence.entity.User
import jakarta.persistence.*
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.time.LocalDateTime

@Entity
@Table(
    name = "refresh_tokens",
    indexes = [Index(name = "idx_refresh_tokens_user_id", columnList = "user_id")]
)
class RefreshToken(

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "user_id",
        nullable = false,
        foreignKey = ForeignKey(name = "fk_refresh_token_user")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    val user: User,

    @Column(nullable = false, unique = true)
    val token: String,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    val expiresAt: LocalDateTime

)
