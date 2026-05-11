package com.wnl.cashchat.api.domain.user.persistence.entity

import com.wnl.cashchat.api.common.entity.BaseEntity
import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import jakarta.persistence.*

@Entity
@Table(
    name = "users",
    uniqueConstraints = [UniqueConstraint(columnNames = ["provider", "provider_id"])]
)
class User(

    // PK
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    // 권한
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var role: Role = Role.GUEST,

    // 게스트 식별자
    @Column(unique = true)
    var deviceToken: String? = null,

    // OAuth 식별자
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var provider: AuthProviderType = AuthProviderType.NONE,

    @Column(name = "provider_id")
    var providerId: String? = null,

    // 프로필
    var email: String? = null,

    @Column(nullable = false)
    var name: String,

    var profileImageUrl: String? = null,

) : BaseEntity()
