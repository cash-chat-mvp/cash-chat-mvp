package com.wnl.cashchat.api.domain.ad.service

import com.wnl.cashchat.api.domain.ad.exception.GoogleAdSsvTransientException
import com.wnl.cashchat.api.domain.ad.properties.GoogleAdSsvProperties
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Base64

class GoogleAdPublicKeyClientTest : FunSpec({
    test("fetches and caches public keys by key id") {
        val fixture = fixture()
        val keyBase64 = base64PublicKey()
        fixture.server.expect(requestTo("https://keys.example.test"))
            .andRespond(withSuccess("""{"keys":[{"keyId":1916455855,"base64":"$keyBase64"}]}""", MediaType.APPLICATION_JSON))

        val first = fixture.client.getPublicKey(1916455855)
        val second = fixture.client.getPublicKey(1916455855)

        first shouldBe second
        fixture.server.verify()
    }

    test("refreshes keys after cache ttl expires") {
        val fixture = fixture()
        val firstKey = base64PublicKey()
        val secondKey = base64PublicKey()
        fixture.server.expect(requestTo("https://keys.example.test"))
            .andRespond(withSuccess("""{"keys":[{"keyId":1,"base64":"$firstKey"}]}""", MediaType.APPLICATION_JSON))
        fixture.server.expect(requestTo("https://keys.example.test"))
            .andRespond(withSuccess("""{"keys":[{"keyId":1,"base64":"$secondKey"}]}""", MediaType.APPLICATION_JSON))

        val first = fixture.client.getPublicKey(1)
        fixture.clock.advance(Duration.ofHours(25))
        val second = fixture.client.getPublicKey(1)

        (first == second) shouldBe false
        fixture.server.verify()
    }

    test("wraps key server errors as transient failures") {
        val fixture = fixture()
        fixture.server.expect(requestTo("https://keys.example.test"))
            .andRespond(withServerError())

        shouldThrow<GoogleAdSsvTransientException> {
            fixture.client.getPublicKey(1)
        }

        fixture.server.verify()
    }

    test("rejects missing key id after fetching keys") {
        val fixture = fixture()
        val firstKey = base64PublicKey()
        val secondKey = base64PublicKey()
        fixture.server.expect(requestTo("https://keys.example.test"))
            .andRespond(withSuccess("""{"keys":[{"keyId":2,"base64":"$firstKey"}]}""", MediaType.APPLICATION_JSON))
        fixture.server.expect(requestTo("https://keys.example.test"))
            .andRespond(withSuccess("""{"keys":[{"keyId":2,"base64":"$secondKey"}]}""", MediaType.APPLICATION_JSON))

        shouldThrow<GoogleAdSsvTransientException> {
            fixture.client.getPublicKey(1)
        }

        fixture.server.verify()
    }

    test("refreshes valid cache once when requested key id is missing") {
        val fixture = fixture()
        val firstKey = base64PublicKey()
        val secondKey = base64PublicKey()
        fixture.server.expect(requestTo("https://keys.example.test"))
            .andRespond(withSuccess("""{"keys":[{"keyId":1,"base64":"$firstKey"}]}""", MediaType.APPLICATION_JSON))
        fixture.server.expect(requestTo("https://keys.example.test"))
            .andRespond(withSuccess("""{"keys":[{"keyId":2,"base64":"$secondKey"}]}""", MediaType.APPLICATION_JSON))

        fixture.client.getPublicKey(1)
        val refreshedKey = fixture.client.getPublicKey(2)

        refreshedKey shouldBe fixture.client.getPublicKey(2)
        fixture.server.verify()
    }
})

private fun fixture(): KeyClientFixture {
    val builder = RestClient.builder()
    val server = MockRestServiceServer.bindTo(builder).build()
    val clock = MutableClock(Instant.parse("2026-05-17T00:00:00Z"))
    val client = GoogleAdPublicKeyClient(
        restClient = builder.build(),
        properties = GoogleAdSsvProperties(
            ssvPublicKeysUri = "https://keys.example.test",
            publicKeyCacheTtl = Duration.ofHours(24),
        ),
        clock = clock,
    )
    return KeyClientFixture(client, server, clock)
}

private data class KeyClientFixture(
    val client: GoogleAdPublicKeyClient,
    val server: MockRestServiceServer,
    val clock: MutableClock,
)

private class MutableClock(private var now: Instant) : Clock() {
    fun advance(duration: Duration) {
        now = now.plus(duration)
    }

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId?): Clock = this

    override fun instant(): Instant = now
}

private fun base64PublicKey(): String {
    val generator = KeyPairGenerator.getInstance("EC")
    generator.initialize(256)
    val publicKey: PublicKey = generator.generateKeyPair().public
    return Base64.getEncoder().encodeToString(publicKey.encoded)
}
