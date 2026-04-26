package com.wnl.cashchat.api.domain.point.persistence.entity

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.user.persistence.entity.Role
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class UserPointTest : FunSpec({
    test("deduct rejects amounts that would make balance negative") {
        val user = User(id = 1L, role = Role.GUEST, provider = AuthProviderType.NONE, name = "Guest")
        val userPoint = UserPoint(user = user, balance = 1L)

        shouldThrow<IllegalArgumentException> {
            userPoint.deduct(2L)
        }

        userPoint.balance shouldBe 1L
    }

    test("charge increases balance through the domain method") {
        val user = User(id = 1L, role = Role.GUEST, provider = AuthProviderType.NONE, name = "Guest")
        val userPoint = UserPoint(user = user, balance = 1L)

        userPoint.charge(2L)

        userPoint.balance shouldBe 3L
    }
})
