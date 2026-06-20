package com.wnl.cashchat.api.domain.economy.persistence

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.economy.persistence.repository.UserWalletRepository
import com.wnl.cashchat.api.domain.economy.service.WalletService
import com.wnl.cashchat.api.domain.user.persistence.entity.Role
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import com.wnl.cashchat.api.domain.user.persistence.repository.UserRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import com.wnl.cashchat.api.domain.economy.exception.WalletNotInitializedException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MySQLContainer

@SpringBootTest
class WalletPersistenceIntegrationTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var userWalletRepository: UserWalletRepository
    @Autowired lateinit var walletService: WalletService

    init {
        beforeTest { userWalletRepository.deleteAll(); userRepository.deleteAll() }

        test("ensureInitialized creates exactly one wallet and is idempotent") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "w"))
            val first = walletService.ensureInitialized(user)
            val second = walletService.ensureInitialized(user)
            second.id shouldBe first.id
            userWalletRepository.count() shouldBe 1L
            userWalletRepository.findByUserId(user.id)!!.energyAvailable shouldBe 0L
        }

        test("snapshot throws when the wallet does not exist") {
            shouldThrow<WalletNotInitializedException> { walletService.snapshot(999_999L) }
        }

        test("getForUpdate requires an active transaction") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "tx"))
            walletService.ensureInitialized(user)
            shouldThrow<org.springframework.transaction.IllegalTransactionStateException> {
                walletService.getForUpdate(user.id)
            }
        }
    }

    companion object {
        private val mysql = MySQLContainer("mysql:8.4.0")
            .withDatabaseName("cashchat").withUsername("cashchat").withPassword("cashchat")
        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            if (!mysql.isRunning) mysql.start()
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
            registry.add("spring.datasource.driver-class-name", mysql::getDriverClassName)
        }
    }
}
