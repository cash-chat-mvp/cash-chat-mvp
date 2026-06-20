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
        properties.rewardedAdUnitId shouldBe ""
        properties.isRewardedAdUnitValidationEnabled() shouldBe false
    }

    test("enables rewarded ad unit validation when an ad unit id is configured") {
        val properties = GoogleAdSsvProperties(rewardedAdUnitId = "ca-app-pub-3940256099942544/5224354917")

        properties.isRewardedAdUnitValidationEnabled() shouldBe true
    }

    test("rejects public key cache TTL longer than 24 hours") {
        val validator = Validation.buildDefaultValidatorFactory().validator

        val violations = validator.validate(
            GoogleAdSsvProperties(publicKeyCacheTtl = Duration.ofHours(25)),
        )

        violations.map { it.propertyPath.toString() } shouldContain "publicKeyCacheTtl"
    }
})
