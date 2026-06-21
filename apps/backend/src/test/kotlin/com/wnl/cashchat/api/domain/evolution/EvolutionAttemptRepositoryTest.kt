package com.wnl.cashchat.api.domain.evolution

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.evolution.persistence.entity.EvolutionAttempt
import com.wnl.cashchat.api.domain.evolution.persistence.entity.EvolutionResult
import com.wnl.cashchat.api.domain.evolution.persistence.repository.EvolutionAttemptRepository
import com.wnl.cashchat.api.domain.user.persistence.entity.Role
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import com.wnl.cashchat.api.domain.user.persistence.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MySQLContainer

@SpringBootTest
class EvolutionAttemptRepositoryTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var repository: EvolutionAttemptRepository
    @Autowired lateinit var userRepository: UserRepository

    init {
        beforeTest {
            repository.deleteAll()
            userRepository.deleteAll()
        }

        test("save and find by userId + attemptKey") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "x"))
            repository.save(attempt(user.id, "key-1"))
            val found = repository.findByUserIdAndAttemptKey(user.id, "key-1")
            found.shouldNotBeNull()
            found.result shouldBe EvolutionResult.FAIL
        }

        test("unique(user_id, attempt_key) rejects duplicate") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "dup"))
            repository.saveAndFlush(attempt(user.id, "dup"))
            shouldThrow<DataIntegrityViolationException> {
                repository.saveAndFlush(attempt(user.id, "dup"))
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

private fun attempt(userId: Long, key: String) = EvolutionAttempt(
    userId = userId, attemptKey = key, levelBefore = 1, levelAfter = 1,
    requiredExp = 30, baseSuccessRate = 0.80, failStackBefore = 0,
    finalSuccessRate = 0.80, rollValue = 0.99, result = EvolutionResult.FAIL,
    expAfter = 0, failStackAfter = 1, policyVersion = 1,
)
