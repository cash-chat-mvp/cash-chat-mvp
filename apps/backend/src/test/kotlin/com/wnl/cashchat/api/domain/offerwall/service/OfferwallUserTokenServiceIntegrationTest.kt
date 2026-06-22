package com.wnl.cashchat.api.domain.offerwall.service

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.offerwall.persistence.repository.OfferwallUserTokenRepository
import com.wnl.cashchat.api.domain.user.persistence.entity.Role
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import com.wnl.cashchat.api.domain.user.persistence.repository.UserRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MySQLContainer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
class OfferwallUserTokenServiceIntegrationTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var tokenRepository: OfferwallUserTokenRepository
    @Autowired lateinit var tokenService: OfferwallUserTokenService

    init {
        beforeTest {
            tokenRepository.deleteAll()
            userRepository.deleteAll()
        }

        test("tokenFor creates a token on first call") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "t1"))

            val token = tokenService.tokenFor(user.id)

            token.shouldNotBeNull()
            tokenRepository.findByToken(token)!!.userId shouldBe user.id
        }

        test("tokenFor returns the same token on repeated calls") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "t2"))

            val first = tokenService.tokenFor(user.id)
            val second = tokenService.tokenFor(user.id)

            second shouldBe first
            tokenRepository.count() shouldBe 1L
        }

        test("resolveUserId maps a known token back to its user") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "t3"))
            val token = tokenService.tokenFor(user.id)

            tokenService.resolveUserId(token) shouldBe user.id
        }

        test("resolveUserId returns null for an unknown token") {
            tokenService.resolveUserId("does-not-exist") shouldBe null
        }

        test("concurrent first calls create exactly one token") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "race"))
            val threads = 6
            val pool = Executors.newFixedThreadPool(threads)
            val ready = CountDownLatch(threads)
            val go = CountDownLatch(1)
            val tokens = ConcurrentLinkedQueue<String>()
            val failures = ConcurrentLinkedQueue<Throwable>()
            repeat(threads) {
                pool.submit {
                    ready.countDown(); go.await()
                    try { tokens.add(tokenService.tokenFor(user.id)) } catch (e: Throwable) { failures.add(e) }
                }
            }
            ready.await(); go.countDown(); pool.shutdown()
            pool.awaitTermination(30, TimeUnit.SECONDS) shouldBe true

            failures.map { "${it::class.simpleName}: ${it.message}" } shouldBe emptyList()
            tokenRepository.count() shouldBe 1L
            tokens.toSet().size shouldBe 1
        }
    }

    companion object {
        private val mysql = MySQLContainer("mysql:8.4.0")
            .withDatabaseName("cashchat").withUsername("cashchat").withPassword("cashchat")

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            if (!mysql.isRunning) mysql.start()
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
            registry.add("spring.datasource.driver-class-name", mysql::getDriverClassName)
            registry.add("app.offerwall.tnk.android.app-key") { "test-app-key" }
            registry.add("app.offerwall.tnk.ios.app-key") { "test-app-key" }
        }
    }
}
