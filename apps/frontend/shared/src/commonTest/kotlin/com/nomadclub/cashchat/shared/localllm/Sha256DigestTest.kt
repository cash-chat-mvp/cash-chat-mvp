package com.nomadclub.cashchat.shared.localllm

import kotlin.test.Test
import kotlin.test.assertEquals

class Sha256DigestTest {

    @Test
    fun emptyInput() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Sha256Digest.hex(ByteArray(0)),
        )
    }

    @Test
    fun abc() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256Digest.hex("abc".encodeToByteArray()),
        )
    }

    @Test
    fun fiftySixBytes_crossesPaddingBoundary() {
        // 56바이트는 단일 블록 내 패딩 경계(56 mod 64) — 길이 부착이 다음 블록으로 넘어간다.
        val input = "a".repeat(56).encodeToByteArray()
        assertEquals(
            "b35439a4ac6f0948b6d6f9e3c6af0f5f590ce20f1bde7090ef7970686ec6738a",
            Sha256Digest.hex(input),
        )
    }

    @Test
    fun multiBlock_oneMillionA() {
        // FIPS 180-2 표준 벡터: 'a' 1,000,000개.
        val digest = Sha256Digest()
        val chunk = ByteArray(1000) { 'a'.code.toByte() }
        repeat(1000) { digest.update(chunk) }
        assertEquals(
            "cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0",
            digest.digestHex(),
        )
    }

    @Test
    fun chunkedUpdate_matchesSingleShot() {
        val data = ByteArray(5000) { (it % 251).toByte() }
        val single = Sha256Digest.hex(data)

        val streamed = Sha256Digest()
        var offset = 0
        for (size in intArrayOf(1, 63, 64, 65, 127, 200, 1000)) {
            val end = minOf(offset + size, data.size)
            streamed.update(data.copyOfRange(offset, end), end - offset)
            offset = end
        }
        if (offset < data.size) streamed.update(data.copyOfRange(offset, data.size))
        assertEquals(single, streamed.digestHex())
    }
}
