package com.wnl.cashchat.api.domain.quality.persistence.entity

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SharedQualityPoolTest : FunSpec({

    test("new pool starts with zero balance") {
        val pool = SharedQualityPool()
        pool.balanceCentiPt shouldBe 0L
    }

    test("accrue increases balanceCentiPt by the given amount") {
        val pool = SharedQualityPool()
        pool.accrue(100L)
        pool.balanceCentiPt shouldBe 100L

        pool.accrue(32L)
        pool.balanceCentiPt shouldBe 132L
    }

    test("tryConsume returns true and deducts when balance >= delta") {
        val pool = SharedQualityPool(balanceCentiPt = 500L)
        val result = pool.tryConsume(300L)
        result shouldBe true
        pool.balanceCentiPt shouldBe 200L
    }

    test("tryConsume returns true and deducts when balance == delta (exact match)") {
        val pool = SharedQualityPool(balanceCentiPt = 300L)
        val result = pool.tryConsume(300L)
        result shouldBe true
        pool.balanceCentiPt shouldBe 0L
    }

    test("tryConsume returns false and leaves balance unchanged when balance < delta (I6 invariant)") {
        val pool = SharedQualityPool(balanceCentiPt = 100L)
        val result = pool.tryConsume(200L)
        result shouldBe false
        pool.balanceCentiPt shouldBe 100L
    }

    test("tryConsume with zero delta on empty pool returns true") {
        val pool = SharedQualityPool(balanceCentiPt = 0L)
        val result = pool.tryConsume(0L)
        result shouldBe true
        pool.balanceCentiPt shouldBe 0L
    }

    test("accrue rejects negative amount") {
        val pool = SharedQualityPool()
        shouldThrow<IllegalArgumentException> { pool.accrue(-1L) }
        pool.balanceCentiPt shouldBe 0L
    }

    test("tryConsume rejects negative delta") {
        val pool = SharedQualityPool(balanceCentiPt = 100L)
        shouldThrow<IllegalArgumentException> { pool.tryConsume(-1L) }
        pool.balanceCentiPt shouldBe 100L
    }
})
