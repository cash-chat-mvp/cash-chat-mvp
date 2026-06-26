package com.wnl.cashchat.api.domain.offerwall.service

import com.wnl.cashchat.api.domain.offerwall.persistence.entity.OfferwallPlatform
import com.wnl.cashchat.api.domain.offerwall.properties.TnkOfferwallProperties
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.security.MessageDigest

class TnkMdChecksumVerifierTest : FunSpec({
    val androidKey = "android-secret"
    val iosKey = "ios-secret"
    val verifier = TnkMdChecksumVerifier(
        TnkOfferwallProperties(
            android = TnkOfferwallProperties.Platform(appKey = androidKey),
            ios = TnkOfferwallProperties.Platform(appKey = iosKey),
        ),
    )

    fun md5Hex(input: String): String =
        MessageDigest.getInstance("MD5").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }

    fun params(seqId: String, mdUserNm: String, mdChk: String) =
        TnkOfferwallCallbackParams(seqId = seqId, payPnt = 100, mdUserNm = mdUserNm, mdChk = mdChk, rawQuery = "raw")

    test("valid android md_chk passes with android key") {
        val expected = md5Hex(androidKey + "user-token" + "seq-1")
        verifier.isValid(OfferwallPlatform.ANDROID, params("seq-1", "user-token", expected)) shouldBe true
    }

    test("valid ios md_chk passes with ios key") {
        val expected = md5Hex(iosKey + "user-token" + "seq-1")
        verifier.isValid(OfferwallPlatform.IOS, params("seq-1", "user-token", expected)) shouldBe true
    }

    test("valid md_chk passes regardless of case") {
        val expected = md5Hex(androidKey + "user-token" + "seq-1").uppercase()
        verifier.isValid(OfferwallPlatform.ANDROID, params("seq-1", "user-token", expected)) shouldBe true
    }

    test("android md_chk fails when verified against ios platform (cross-platform)") {
        // 안드로이드 키로 서명한 값을 iOS 플랫폼(=iOS 키)으로 검증하면 실패해야 한다.
        val androidSigned = md5Hex(androidKey + "user-token" + "seq-1")
        verifier.isValid(OfferwallPlatform.IOS, params("seq-1", "user-token", androidSigned)) shouldBe false
    }

    test("wrong md_chk fails") {
        verifier.isValid(OfferwallPlatform.ANDROID, params("seq-1", "user-token", "deadbeef")) shouldBe false
    }

    test("blank appKey rejects that platform (fail-closed)") {
        val verifierWithBlankIos = TnkMdChecksumVerifier(
            TnkOfferwallProperties(
                android = TnkOfferwallProperties.Platform(appKey = androidKey),
                ios = TnkOfferwallProperties.Platform(appKey = ""),
            ),
        )
        val attackerHash = md5Hex("" + "user-token" + "seq-1")
        verifierWithBlankIos.isValid(OfferwallPlatform.IOS, params("seq-1", "user-token", attackerHash)) shouldBe false
    }
})
