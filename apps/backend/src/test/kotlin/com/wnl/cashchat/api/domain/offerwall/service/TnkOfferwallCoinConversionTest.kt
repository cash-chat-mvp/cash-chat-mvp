package com.wnl.cashchat.api.domain.offerwall.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TnkOfferwallCoinConversionTest : FunSpec({
    test("1:1 ratio credits the raw pay_pnt") {
        toCoinAmount(1500, 1.0) shouldBe 1500L
    }

    test("fractional ratio floors the result") {
        toCoinAmount(1501, 0.5) shouldBe 750L // 750.5 → 750
    }

    test("BigDecimal avoids the Double floor precision drop") {
        // 50 * 0.58 = 29.0 정확값. Double 곱은 28.999999999999996 → floor 28(버그).
        // BigDecimal + FLOOR 은 29 를 보장한다.
        toCoinAmount(50, 0.58) shouldBe 29L
    }

    test("zero pay_pnt yields zero coins") {
        toCoinAmount(0, 1.0) shouldBe 0L
    }
})
