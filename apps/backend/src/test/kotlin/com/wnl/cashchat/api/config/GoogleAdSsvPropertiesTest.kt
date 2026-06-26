package com.wnl.cashchat.api.config

import com.wnl.cashchat.api.domain.ad.properties.GoogleAdSsvProperties
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import jakarta.validation.Validation
import java.time.Duration
import java.time.Instant

class GoogleAdSsvPropertiesTest : FunSpec({
    test("uses Google SSV defaults") {
        val properties = GoogleAdSsvProperties()

        properties.ssvPublicKeysUri shouldBe "https://www.gstatic.com/admob/reward/verifier-keys.json"
        properties.publicKeyCacheTtl shouldBe Duration.ofHours(24)
        properties.rewardedAdUnitIds shouldBe emptyList()
        properties.isRewardedAdUnitValidationEnabled() shouldBe false
        properties.timestampTolerance shouldBe Duration.ofHours(1)
        properties.timestampFutureSkew shouldBe Duration.ofMinutes(5)
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

    test("allows callback ad_unit sent in AdMob's numeric-only form against full configured ids") {
        // AdMob SSV 콜백은 ad_unit 을 숫자 부분만 보낸다(예: 2647937531)지만,
        // 설정값은 전체 형식(ca-app-pub-.../2647937531)이다. 둘을 매칭해야 적립된다.
        val android = "ca-app-pub-5280178196982923/6512984753"
        val ios = "ca-app-pub-5280178196982923/2647937531"
        val properties = GoogleAdSsvProperties(rewardedAdUnitIds = listOf(android, ios))

        properties.isAllowedAdUnit("6512984753") shouldBe true
        properties.isAllowedAdUnit("2647937531") shouldBe true
        properties.isAllowedAdUnit("0000000000") shouldBe false
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

    test("accepts a timestamp inside the freshness window") {
        val properties = GoogleAdSsvProperties()
        val now = Instant.ofEpochMilli(1710000000123L)

        properties.isTimestampFresh(now.toEpochMilli(), now) shouldBe true
        // 과거 경계(정확히 tolerance 만큼 이전) 포함
        properties.isTimestampFresh(now.minus(Duration.ofHours(1)).toEpochMilli(), now) shouldBe true
        // 미래 경계(정확히 future-skew 만큼 이후) 포함
        properties.isTimestampFresh(now.plus(Duration.ofMinutes(5)).toEpochMilli(), now) shouldBe true
    }

    test("rejects a timestamp older than the tolerance") {
        val properties = GoogleAdSsvProperties()
        val now = Instant.ofEpochMilli(1710000000123L)

        properties.isTimestampFresh(now.minus(Duration.ofHours(1).plusMillis(1)).toEpochMilli(), now) shouldBe false
    }

    test("rejects a timestamp further in the future than the skew allowance") {
        val properties = GoogleAdSsvProperties()
        val now = Instant.ofEpochMilli(1710000000123L)

        properties.isTimestampFresh(now.plus(Duration.ofMinutes(5).plusMillis(1)).toEpochMilli(), now) shouldBe false
    }

    test("rejects non-positive freshness window durations") {
        val validator = Validation.buildDefaultValidatorFactory().validator

        val violations = validator.validate(
            GoogleAdSsvProperties(
                timestampTolerance = Duration.ZERO,
                timestampFutureSkew = Duration.ofMinutes(-1),
            ),
        )

        violations.map { it.propertyPath.toString() } shouldContain "timestampTolerance"
        violations.map { it.propertyPath.toString() } shouldContain "timestampFutureSkew"
    }
})
