package com.wnl.cashchat.api.domain.shop.persistence

import com.wnl.cashchat.api.domain.shop.persistence.entity.ShopItemCategory
import com.wnl.cashchat.api.domain.shop.persistence.repository.ShopItemGrantRepository
import com.wnl.cashchat.api.domain.shop.persistence.repository.ShopItemRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MySQLContainer

@SpringBootTest
class ShopMigrationIntegrationTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var shopItemRepository: ShopItemRepository
    @Autowired lateinit var shopItemGrantRepository: ShopItemGrantRepository

    init {
        test("V6 seeds 5 active ENHANCE items ordered by displayOrder") {
            val items = shopItemRepository
                .findByCategoryAndIsActiveTrueOrderByDisplayOrderAsc(ShopItemCategory.ENHANCE)
            items.map { it.itemCode } shouldBe listOf(
                "ENHANCE_PACK", "EVO_STONE", "EVO_STONE_BUNDLE", "LUCK_CHARM", "PROTECT_TICKET"
            )
        }

        test("V6 seeds 6 grant rows including the ENHANCE_PACK bundle") {
            shopItemGrantRepository.count() shouldBe 6L
            shopItemGrantRepository.findByItemCodeOrderByGrantItemCodeAsc("ENHANCE_PACK")
                .map { it.grantItemCode to it.grantQty } shouldBe listOf("EVO_STONE" to 5, "LUCK_CHARM" to 1)
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
            if (!mysql.isRunning) mysql.start()
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
            registry.add("spring.datasource.driver-class-name", mysql::getDriverClassName)
        }
    }
}
