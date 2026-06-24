package com.wnl.cashchat.api.domain.ad.service

import com.wnl.cashchat.api.domain.ad.exception.InvalidGoogleAdSsvCallbackException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class GoogleAdSsvQueryParserTest : FunSpec({
    val parser = GoogleAdSsvQueryParser()

    test("valid parse keeps the signed payload raw (undecoded)") {
        val rawSignedPayload =
            "ad_unit=ca-app-pub-3940256099942544%2F5224354917" +
                "&reward_amount=10" +
                "&reward_item=coin%20pack" +
                "&timestamp=1710000000123" +
                "&transaction_id=txn-123" +
                "&user_id=user%2B42"
        val rawQuery = "$rawSignedPayload&signature=MEUCIQDabc%2Bdef&key_id=12345"

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
        // 검증기가 raw·decoded 둘 다 시도하므로 파서는 원문(raw)을 그대로 보존한다.
        callback.signedPayload shouldBe rawSignedPayload
    }

    test("signed payload keeps non-ascii reward_item percent-encoded (raw)") {
        val rawQuery =
            "ad_network=5450213213286189855" +
                "&ad_unit=1234567890" +
                "&reward_amount=1" +
                "&reward_item=%EC%97%90%EB%84%88%EC%A7%80" +
                "&timestamp=1782250214931" +
                "&transaction_id=123456789" +
                "&user_id=1" +
                "&signature=sig" +
                "&key_id=3335741209"

        val callback = parser.parse(rawQuery)

        callback.rewardItem shouldBe "에너지"
        // 파서는 디코딩하지 않고 raw 를 보존한다(검증기가 raw·decoded 둘 다 시도).
        callback.signedPayload shouldBe
            "ad_network=5450213213286189855" +
                "&ad_unit=1234567890" +
                "&reward_amount=1" +
                "&reward_item=%EC%97%90%EB%84%88%EC%A7%80" +
                "&timestamp=1782250214931" +
                "&transaction_id=123456789" +
                "&user_id=1"
    }

    test("missing user_id yields null (user_id is optional)") {
        val rawQuery =
            "ad_unit=ad-unit" +
                "&reward_amount=10" +
                "&reward_item=coin" +
                "&timestamp=1710000000123" +
                "&transaction_id=txn-123" +
                "&signature=sig" +
                "&key_id=12345"

        val callback = parser.parse(rawQuery)

        callback.userId shouldBe null
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

    test("custom_data is extracted and url-decoded") {
        val rawQuery =
            "ad_unit=au&reward_amount=1&reward_item=coin&timestamp=1&transaction_id=t" +
                "&custom_data=nonce%2Babc&user_id=u&signature=sig&key_id=1"

        val callback = parser.parse(rawQuery)

        callback.customData shouldBe "nonce+abc"
    }

    test("user_id is optional and absent yields null userId") {
        val rawQuery =
            "ad_unit=au&reward_amount=1&reward_item=coin&timestamp=1&transaction_id=t" +
                "&custom_data=nonce123&signature=sig&key_id=1"

        val callback = parser.parse(rawQuery)

        callback.userId shouldBe null
        callback.customData shouldBe "nonce123"
    }
})
