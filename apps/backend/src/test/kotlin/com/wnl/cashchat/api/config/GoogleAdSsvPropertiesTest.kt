package com.wnl.cashchat.api.config

import com.wnl.cashchat.api.domain.ad.properties.GoogleAdSsvProperties
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import jakarta.validation.Validation
import java.time.Duration

class GoogleAdSsvPropertiesTest : FunSpec({
    test("uses Google SSV defaults") {
        val properties = GoogleAdSsvProperties()

        properties.ssvPublicKeysUri shouldBe "https://www.gstatic.com/admob/reward/verifier-keys.json"
        properties.publicKeyCacheTtl shouldBe Duration.ofHours(24)
        properties.rewardedAdUnitIds shouldBe emptyList()
        properties.isRewardedAdUnitValidationEnabled() shouldBe false
    }

    test("enables rewarded ad unit validation when at least one ad unit id is configured") {
        val properties = GoogleAdSsvProperties(
            rewardedAdUnitIds = listOf("ca-app-pub-3940256099942544/5224354917"),
        )

        properties.isRewardedAdUnitValidationEnabled() shouldBe true
    }

    test("allows every configured ad unit (Android and iOS) and rejects unknown ones") {
        val android = "ca-app-pub-5280178196982923/6512984753"
        val ios = "ca-app-pub-5280178196982923/2647937531"
        val properties = GoogleAdSsvProperties(rewardedAdUnitIds = listOf(android, ios))

        properties.isRewardedAdUnitValidationEnabled() shouldBe true
        properties.isAllowedAdUnit(android) shouldBe true
        properties.isAllowedAdUnit(ios) shouldBe true
        properties.isAllowedAdUnit("ca-app-pub-5280178196982923/0000000000") shouldBe false
    }

    test("treats blank-only ad unit ids as validation disabled") {
        val properties = GoogleAdSsvProperties(rewardedAdUnitIds = listOf("", "   "))

        properties.isRewardedAdUnitValidationEnabled() shouldBe false
        properties.isAllowedAdUnit("") shouldBe false
    }

    test("rejects public key cache TTL longer than 24 hours") {
        val validator = Validation.buildDefaultValidatorFactory().validator

        val violations = validator.validate(
            GoogleAdSsvProperties(publicKeyCacheTtl = Duration.ofHours(25)),
        )

        violations.map { it.propertyPath.toString() } shouldContain "publicKeyCacheTtl"
    }
})
