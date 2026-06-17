package com.wnl.cashchat.api.domain.offerwall.service

import com.wnl.cashchat.api.domain.offerwall.properties.TnkOfferwallProperties
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.security.MessageDigest

class TnkMdChecksumVerifierTest : FunSpec({
    val appKey = "secret-key"
    val verifier = TnkMdChecksumVerifier(TnkOfferwallProperties(appKey = appKey))

    fun md5Hex(input: String): String =
        MessageDigest.getInstance("MD5").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }

    fun params(seqId: String, mdUserNm: String, mdChk: String) =
        TnkOfferwallCallbackParams(seqId = seqId, payPnt = 100, mdUserNm = mdUserNm, mdChk = mdChk, rawQuery = "raw")

    test("valid md_chk passes") {
        val expected = md5Hex(appKey + "user-token" + "seq-1")
        verifier.isValid(params("seq-1", "user-token", expected)) shouldBe true
    }

    test("valid md_chk passes regardless of case") {
        val expected = md5Hex(appKey + "user-token" + "seq-1").uppercase()
        verifier.isValid(params("seq-1", "user-token", expected)) shouldBe true
    }

    test("wrong md_chk fails") {
        verifier.isValid(params("seq-1", "user-token", "deadbeef")) shouldBe false
    }

    test("md_chk computed with a different appKey fails") {
        val forged = md5Hex("other-key" + "user-token" + "seq-1")
        verifier.isValid(params("seq-1", "user-token", forged)) shouldBe false
    }
})
