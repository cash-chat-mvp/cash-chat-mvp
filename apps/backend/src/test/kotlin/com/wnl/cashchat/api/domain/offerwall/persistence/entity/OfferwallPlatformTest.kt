package com.wnl.cashchat.api.domain.offerwall.persistence.entity

import com.wnl.cashchat.api.domain.offerwall.exception.UnknownOfferwallPlatformException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class OfferwallPlatformTest : FunSpec({
    test("from parses lowercase path values") {
        OfferwallPlatform.from("android") shouldBe OfferwallPlatform.ANDROID
        OfferwallPlatform.from("ios") shouldBe OfferwallPlatform.IOS
    }

    test("from is case-insensitive") {
        OfferwallPlatform.from("Android") shouldBe OfferwallPlatform.ANDROID
        OfferwallPlatform.from("IOS") shouldBe OfferwallPlatform.IOS
    }

    test("from throws on unknown platform") {
        val ex = shouldThrow<UnknownOfferwallPlatformException> { OfferwallPlatform.from("web") }
        ex.raw shouldBe "web"
    }
})
