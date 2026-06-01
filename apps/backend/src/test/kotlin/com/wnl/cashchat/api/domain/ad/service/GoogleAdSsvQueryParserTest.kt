package com.wnl.cashchat.api.domain.ad.service

import com.wnl.cashchat.api.domain.ad.exception.InvalidGoogleAdSsvCallbackException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class GoogleAdSsvQueryParserTest : FunSpec({
    val parser = GoogleAdSsvQueryParser()

    test("valid parse extracts fields and keeps signed payload unchanged") {
        val signedPayload =
            "ad_unit=ca-app-pub-3940256099942544%2F5224354917" +
                "&reward_amount=10" +
                "&reward_item=coin%20pack" +
                "&timestamp=1710000000123" +
                "&transaction_id=txn-123" +
                "&user_id=user%2B42"
        val rawQuery = "$signedPayload&signature=MEUCIQDabc%2Bdef&key_id=12345"

        val callback = parser.parse(rawQuery)

        callback.adUnit shouldBe "ca-app-pub-3940256099942544/5224354917"
        callback.rewardAmount shouldBe 10
        callback.rewardItem shouldBe "coin pack"
        callback.timestamp shouldBe 1710000000123L
        callback.transactionId shouldBe "txn-123"
        callback.userId shouldBe "user+42"
        callback.signature shouldBe "MEUCIQDabc+def"
        callback.keyId shouldBe 12345L
        callback.rawQueryString shouldBe rawQuery
        callback.signedPayload shouldBe signedPayload
    }

    test("missing user_id is rejected") {
        val rawQuery =
            "ad_unit=ad-unit" +
                "&reward_amount=10" +
                "&reward_item=coin" +
                "&timestamp=1710000000123" +
                "&transaction_id=txn-123" +
                "&signature=sig" +
                "&key_id=12345"

        shouldThrow<InvalidGoogleAdSsvCallbackException> {
            parser.parse(rawQuery)
        }
    }

    test("key_id before signature is rejected") {
        val rawQuery =
            "ad_unit=ad-unit" +
                "&reward_amount=10" +
                "&reward_item=coin" +
                "&timestamp=1710000000123" +
                "&transaction_id=txn-123" +
                "&user_id=user-42" +
                "&key_id=12345" +
                "&signature=sig"

        shouldThrow<InvalidGoogleAdSsvCallbackException> {
            parser.parse(rawQuery)
        }
    }

    test("invalid reward amount is rejected") {
        val rawQuery =
            "ad_unit=ad-unit" +
                "&reward_amount=ten" +
                "&reward_item=coin" +
                "&timestamp=1710000000123" +
                "&transaction_id=txn-123" +
                "&user_id=user-42" +
                "&signature=sig" +
                "&key_id=12345"

        shouldThrow<InvalidGoogleAdSsvCallbackException> {
            parser.parse(rawQuery)
        }
    }

    test("duplicate parameter key is rejected before trusting signature position") {
        val rawQuery =
            "ad_unit=ad-unit" +
                "&signature=earlier-sig" +
                "&reward_amount=10" +
                "&reward_item=coin" +
                "&timestamp=1710000000123" +
                "&transaction_id=txn-123" +
                "&user_id=user-42" +
                "&signature=final-sig" +
                "&key_id=12345"

        shouldThrow<InvalidGoogleAdSsvCallbackException> {
            parser.parse(rawQuery)
        }
    }

    test("malformed percent escape is rejected with invalid callback exception") {
        val rawQuery =
            "ad_unit=ad%ZZunit" +
                "&reward_amount=10" +
                "&reward_item=coin" +
                "&timestamp=1710000000123" +
                "&transaction_id=txn-123" +
                "&user_id=user-42" +
                "&signature=sig" +
                "&key_id=12345"

        shouldThrow<InvalidGoogleAdSsvCallbackException> {
            parser.parse(rawQuery)
        }
    }

    test("literal plus sign in decoded field remains plus") {
        val signedPayload =
            "ad_unit=ad-unit" +
                "&reward_amount=10" +
                "&reward_item=coin+pack" +
                "&timestamp=1710000000123" +
                "&transaction_id=txn-123" +
                "&user_id=user+42"
        val rawQuery = "$signedPayload&signature=sig&key_id=12345"

        val callback = parser.parse(rawQuery)

        callback.rewardItem shouldBe "coin+pack"
        callback.userId shouldBe "user+42"
        callback.signedPayload shouldBe signedPayload
    }
})
