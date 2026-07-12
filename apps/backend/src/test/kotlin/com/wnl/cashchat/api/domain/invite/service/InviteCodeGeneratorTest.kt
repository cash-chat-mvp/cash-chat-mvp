package com.wnl.cashchat.api.domain.invite.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldNotBeIn
import io.kotest.matchers.shouldBe

class InviteCodeGeneratorTest : FunSpec({
    val generator = InviteCodeGenerator()

    test("generate returns a code of the requested length") {
        generator.generate(6).length shouldBe 6
        generator.generate(10).length shouldBe 10
    }

    test("generate uses only the unambiguous alphabet") {
        val allowed = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toList()
        repeat(500) {
            generator.generate(8).forEach { c -> c shouldBeIn allowed }
        }
    }

    test("generate never emits ambiguous characters O/0/I/1/L") {
        val ambiguous = listOf('O', '0', 'I', '1', 'L')
        repeat(500) {
            generator.generate(10).forEach { c -> c shouldNotBeIn ambiguous }
        }
    }
})
