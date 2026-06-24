package com.wnl.cashchat.api.domain.ad.service

import com.wnl.cashchat.api.domain.ad.exception.InvalidGoogleAdSsvCallbackException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class GoogleAdSsvQueryParserTest : FunSpec({
    val parser = GoogleAdSsvQueryParser()

    test("valid parse decodes the signed payload to match Google's signed content") {
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
        // Google 은 percent-encoding 된 전송 문자열이 아니라 URL 디코딩된 콘텐츠에 서명한다.
        callback.signedPayload shouldBe
            "ad_unit=ca-app-pub-3940256099942544/5224354917" +
                "&reward_amount=10" +
                "&reward_item=coin pack" +
                "&timestamp=1710000000123" +
                "&transaction_id=txn-123" +
                "&user_id=user+42"
    }

    test("signed payload decodes non-ascii reward_item (real AdMob verification ping case)") {
        // 실제 AdMob 확인 핑: reward_item 이 한글 '에너지' → %EC%97%90%EB%84%88%EC%A7%80 로 전송된다.
        // Google 은 디코딩된 '에너지' 에 서명하므로 검증 페이로드도 디코딩돼야 한다.
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
        callback.signedPayload shouldBe
            "ad_network=5450213213286189855" +
                "&ad_unit=1234567890" +
                "&reward_amount=1" +
                "&reward_item=에너지" +
                "&timestamp=1782250214931" +
                "&transaction_id=123456789" +
                "&user_id=1"
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

    test("custom_data is extracted and url-decoded") {
        val rawQuery =
            "ad_unit=au&reward_amount=1&reward_item=coin&timestamp=1&transaction_id=t" +
                "&custom_data=nonce%2Babc&user_id=u&signature=sig&key_id=1"

        val callback = parser.parse(rawQuery)

        callback.customData shouldBe "nonce+abc"
    }
})
