package com.wnl.cashchat.api.domain.chat.persistence

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.chat.persistence.entity.ChatMessage
import com.wnl.cashchat.api.domain.chat.persistence.entity.Conversation
import com.wnl.cashchat.api.domain.chat.persistence.entity.MessageRole
import com.wnl.cashchat.api.domain.chat.persistence.entity.MessageStatus
import com.wnl.cashchat.api.domain.chat.persistence.repository.ChatMessageRepository
import com.wnl.cashchat.api.domain.chat.persistence.repository.ConversationRepository
import com.wnl.cashchat.api.domain.user.persistence.entity.Role
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import com.wnl.cashchat.api.domain.user.persistence.repository.UserRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MySQLContainer
import javax.sql.DataSource

@SpringBootTest(properties = ["spring.jpa.hibernate.ddl-auto=create-drop"])
class ChatPersistenceIntegrationTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var conversationRepository: ConversationRepository

    @Autowired
    lateinit var chatMessageRepository: ChatMessageRepository

    @Autowired
    lateinit var dataSource: DataSource

    init {
        beforeTest {
            chatMessageRepository.deleteAll()
            conversationRepository.deleteAll()
            userRepository.deleteAll()
        }

        test("conversation owner and ordered messages are persisted in mysql with the history index") {
            val user = userRepository.save(
                User(
                    role = Role.MEMBER,
                    provider = AuthProviderType.NONE,
                    name = "tester"
                )
            )

            val conversation = conversationRepository.save(
                Conversation(user = user, title = "first chat")
            )

            chatMessageRepository.save(
                ChatMessage(
                    conversation = conversation,
                    role = MessageRole.USER,
                    content = "hello",
                    status = MessageStatus.COMPLETED
                )
            )
            chatMessageRepository.save(
                ChatMessage(
                    conversation = conversation,
                    role = MessageRole.ASSISTANT,
                    content = "hi there",
                    status = MessageStatus.COMPLETED,
                    model = "gemini-2.0-flash"
                )
            )

            val messages = chatMessageRepository.findAllByConversationIdOrderByCreatedAtAsc(conversation.id)

            messages shouldHaveSize 2
            messages[0].role shouldBe MessageRole.USER
            messages[1].role shouldBe MessageRole.ASSISTANT
            messages[1].model shouldBe "gemini-2.0-flash"
            conversation.user.id shouldBe user.id

            val indexes = JdbcTemplate(dataSource).queryForList("SHOW INDEX FROM chat_messages")
            val indexedColumns = indexes
                .filter { it["Key_name"] == "idx_chat_messages_conversation_created_at" }
                .map { it["Column_name"] as String }

            indexedColumns shouldContain "conversation_id"
            indexedColumns shouldContain "created_at"
        }
    }

    companion object {
        private val mysql = MySQLContainer("mysql:8.4.0")
            .withDatabaseName("cashchat")
            .withUsername("cashchat")
            .withPassword("cashchat")

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            if (!mysql.isRunning) {
                mysql.start()
            }
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
            registry.add("spring.datasource.driver-class-name", mysql::getDriverClassName)
        }
    }
}
