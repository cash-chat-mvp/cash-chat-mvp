package com.nomadclub.cashchat.shared.localllm

/**
 * 순수 Kotlin 증분(streaming) SHA-256.
 *
 * iOS Kotlin/Native에는 CommonCrypto cinterop가 기본 포함되지 않아, 대용량(2.5GB) 모델 파일을
 * 메모리에 한 번에 올리지 않고 청크 단위로 [update] 하여 해싱하기 위한 플랫폼 무관 구현이다.
 * Android는 `java.security.MessageDigest`를 쓰므로 이 클래스를 사용하지 않는다.
 */
internal class Sha256Digest {
    private val state = intArrayOf(
        0x6a09e667, 0xbb67ae85.toInt(), 0x3c6ef372, 0xa54ff53a.toInt(),
        0x510e527f, 0x9b05688c.toInt(), 0x1f83d9ab, 0x5be0cd19,
    )
    private val block = ByteArray(BLOCK_SIZE)
    private val w = IntArray(64)
    private var blockLen = 0
    private var totalBytes = 0L

    /** [data]의 앞 [length] 바이트를 다이제스트에 반영한다. */
    fun update(data: ByteArray, length: Int = data.size) {
        var offset = 0
        var remaining = length
        totalBytes += length.toLong()

        if (blockLen > 0) {
            val take = minOf(BLOCK_SIZE - blockLen, remaining)
            data.copyInto(block, blockLen, offset, offset + take)
            blockLen += take
            offset += take
            remaining -= take
            if (blockLen == BLOCK_SIZE) {
                processBlock(block, 0)
                blockLen = 0
            }
        }

        while (remaining >= BLOCK_SIZE) {
            processBlock(data, offset)
            offset += BLOCK_SIZE
            remaining -= BLOCK_SIZE
        }

        if (remaining > 0) {
            data.copyInto(block, 0, offset, offset + remaining)
            blockLen = remaining
        }
    }

    /** 패딩·길이 부착 후 최종 해시를 소문자 16진수 문자열로 반환한다. */
    fun digestHex(): String {
        val bitLen = totalBytes * 8L

        // 0x80 + 0패딩으로 길이를 56 mod 64 로 맞춘 뒤 8바이트 빅엔디언 비트길이를 덧붙인다.
        val padLen = if (blockLen < 56) 56 - blockLen else 120 - blockLen
        val pad = ByteArray(padLen)
        pad[0] = 0x80.toByte()
        update(pad, padLen)

        val lenBytes = ByteArray(8)
        for (i in 0 until 8) {
            lenBytes[i] = (bitLen ushr (56 - i * 8)).toByte()
        }
        update(lenBytes, 8)

        val out = StringBuilder(64)
        for (s in state) {
            for (shift in intArrayOf(24, 16, 8, 0)) {
                out.append(HEX[(s ushr (shift + 4)) and 0xf])
                out.append(HEX[(s ushr shift) and 0xf])
            }
        }
        return out.toString()
    }

    private fun processBlock(src: ByteArray, off: Int) {
        for (i in 0 until 16) {
            val j = off + i * 4
            w[i] = ((src[j].toInt() and 0xff) shl 24) or
                ((src[j + 1].toInt() and 0xff) shl 16) or
                ((src[j + 2].toInt() and 0xff) shl 8) or
                (src[j + 3].toInt() and 0xff)
        }
        for (i in 16 until 64) {
            val x = w[i - 15]
            val y = w[i - 2]
            val s0 = (x rotr 7) xor (x rotr 18) xor (x ushr 3)
            val s1 = (y rotr 17) xor (y rotr 19) xor (y ushr 10)
            w[i] = w[i - 16] + s0 + w[i - 7] + s1
        }

        var a = state[0]; var b = state[1]; var c = state[2]; var d = state[3]
        var e = state[4]; var f = state[5]; var g = state[6]; var h = state[7]

        for (i in 0 until 64) {
            val bigS1 = (e rotr 6) xor (e rotr 11) xor (e rotr 25)
            val ch = (e and f) xor (e.inv() and g)
            val t1 = h + bigS1 + ch + K[i] + w[i]
            val bigS0 = (a rotr 2) xor (a rotr 13) xor (a rotr 22)
            val maj = (a and b) xor (a and c) xor (b and c)
            val t2 = bigS0 + maj
            h = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2
        }

        state[0] += a; state[1] += b; state[2] += c; state[3] += d
        state[4] += e; state[5] += f; state[6] += g; state[7] += h
    }

    private infix fun Int.rotr(bits: Int): Int = (this ushr bits) or (this shl (32 - bits))

    companion object {
        private const val BLOCK_SIZE = 64
        private val HEX = "0123456789abcdef".toCharArray()
        private val K = intArrayOf(
            0x428a2f98, 0x71374491, 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(),
            0x3956c25b, 0x59f111f1, 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
            0xd807aa98.toInt(), 0x12835b01, 0x243185be, 0x550c7dc3,
            0x72be5d74, 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
            0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6, 0x240ca1cc,
            0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
            0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(),
            0xc6e00bf3.toInt(), 0xd5a79147.toInt(), 0x06ca6351, 0x14292967,
            0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
            0x650a7354, 0x766a0abb, 0x81c2c92e.toInt(), 0x92722c85.toInt(),
            0xa2bfe8a1.toInt(), 0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(),
            0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070,
            0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
            0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
            0x748f82ee, 0x78a5636f, 0x84c87814.toInt(), 0x8cc70208.toInt(),
            0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt(),
        )

        /** 단일 바이트 배열 해시(주로 테스트용). */
        fun hex(data: ByteArray): String = Sha256Digest().apply { update(data) }.digestHex()
    }
}
