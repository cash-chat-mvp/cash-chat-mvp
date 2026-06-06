package com.wnl.cashchat.api.domain.ledger.service

import com.wnl.cashchat.api.domain.energy.service.EnergyService
import com.wnl.cashchat.api.domain.ledger.persistence.entity.LedgerEntry
import com.wnl.cashchat.api.domain.ledger.persistence.entity.RevenueSource
import com.wnl.cashchat.api.domain.ledger.persistence.repository.LedgerEntryRepository
import com.wnl.cashchat.api.domain.ledger.properties.LedgerProperties
import com.wnl.cashchat.api.domain.point.persistence.entity.PointTransactionReason
import com.wnl.cashchat.api.domain.point.service.UserPointService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class LedgerServiceTest : FunSpec({
    val userId = 1L
    val properties = LedgerProperties(
        riskRate = 0.15,
        serviceRate = 0.1,
        minProfitRate = 0.2,
        minProfitFloor = 2L,
        rewards = listOf(
            LedgerProperties.SourceReward(source = RevenueSource.AD, cashablePt = 4L, energy = 3)
        ),
    )

    lateinit var ledgerEntryRepository: LedgerEntryRepository
    lateinit var userPointService: UserPointService
    lateinit var energyService: EnergyService
    lateinit var service: LedgerService

    beforeTest {
        ledgerEntryRepository = mock()
        userPointService = mock()
        energyService = mock()
        service = LedgerService(
            ledgerEntryRepository = ledgerEntryRepository,
            userPointService = userPointService,
            energyService = energyService,
            properties = properties,
        )
        whenever(ledgerEntryRepository.findByUserIdAndIdempotencyKey(any(), any())).thenReturn(null)
        whenever(ledgerEntryRepository.save(any<LedgerEntry>())).thenAnswer { it.arguments[0] }
    }

    // Case 1: R=12 happy path
    // risk = floor(12 * 0.15) = floor(1.8) = 1
    // net = 12 - 1 = 11
    // service = floor(11 * 0.1) = floor(1.1) = 1
    // companyProfit = 11 - 1 - 4 - 3 = 3
    // minProfit = max(2, floor(11 * 0.2)) = max(2, 2) = 2
    // 3 >= 2 → OK
    test("R=12 AD distributes correctly: risk=1, net=11, service=1, profit=3, awards cashablePt 4 and energy 3") {
        val grossRevenue = 12L
        val key = "test-key-1"

        val result = service.recordRevenue(userId, RevenueSource.AD, grossRevenue, key)

        result.grossRevenue shouldBe 12L
        result.riskReserve shouldBe 1L
        result.serviceReserve shouldBe 1L
        result.companyProfit shouldBe 3L
        result.cashablePt shouldBe 4L
        result.energy shouldBe 3

        verify(userPointService).recordTransaction(
            eq(userId),
            eq(4L),
            eq(PointTransactionReason.LEDGER_REWARD),
            eq("ledger:AD:$userId:$key"),
        )
        verify(energyService).charge(eq(userId), eq(3))
        verify(ledgerEntryRepository).save(argThat<LedgerEntry> {
            this.userId == userId &&
                source == RevenueSource.AD &&
                grossRevenue == 12L &&
                riskReserve == 1L &&
                serviceReserve == 1L &&
                companyProfit == 3L &&
                cashablePtAwarded == 4L &&
                energyAwarded == 3 &&
                idempotencyKey == key
        })
    }

    // Case 2: idempotent no-op — second call with same key returns existing distribution
    test("duplicate idempotency key returns existing distribution without re-crediting") {
        val grossRevenue = 12L
        val key = "test-key-idem"
        val existingEntry = LedgerEntry(
            userId = userId,
            source = RevenueSource.AD,
            grossRevenue = grossRevenue,
            riskReserve = 1L,
            serviceReserve = 1L,
            companyProfit = 3L,
            cashablePtAwarded = 4L,
            energyAwarded = 3,
            idempotencyKey = key,
        )
        whenever(ledgerEntryRepository.findByUserIdAndIdempotencyKey(userId, key)).thenReturn(existingEntry)

        val result = service.recordRevenue(userId, RevenueSource.AD, grossRevenue, key)

        result.grossRevenue shouldBe 12L
        result.riskReserve shouldBe 1L
        result.serviceReserve shouldBe 1L
        result.companyProfit shouldBe 3L
        result.cashablePt shouldBe 4L
        result.energy shouldBe 3

        verify(userPointService, never()).recordTransaction(any(), any(), any(), any())
        verify(energyService, never()).charge(any(), any())
        verify(ledgerEntryRepository, never()).save(any())
    }

    // Case 3: profit guard — R too small so profit < minProfit → require fails (IllegalArgumentException)
    // R=6: risk=floor(0.9)=0, net=6, service=floor(0.6)=0, profit=6-0-4-3= -1 < 2 → fail
    test("profit guard rejects grossRevenue too small to cover minimum profit") {
        val key = "test-key-guard"

        shouldThrow<IllegalArgumentException> {
            service.recordRevenue(userId, RevenueSource.AD, 6L, key)
        }

        verify(userPointService, never()).recordTransaction(any(), any(), any(), any())
        verify(energyService, never()).charge(any(), any())
        verify(ledgerEntryRepository, never()).save(any())
    }

    // Case 4: missing config for source — IllegalStateException
    test("missing reward config for source throws IllegalStateException") {
        val propertiesNoAd = LedgerProperties(
            riskRate = 0.15,
            serviceRate = 0.1,
            minProfitRate = 0.2,
            minProfitFloor = 2L,
            rewards = emptyList(),
        )
        val serviceNoConfig = LedgerService(
            ledgerEntryRepository = ledgerEntryRepository,
            userPointService = userPointService,
            energyService = energyService,
            properties = propertiesNoAd,
        )

        shouldThrow<IllegalStateException> {
            serviceNoConfig.recordRevenue(userId, RevenueSource.AD, 12L, "test-key-noconfig")
        }

        verify(userPointService, never()).recordTransaction(any(), any(), any(), any())
        verify(energyService, never()).charge(any(), any())
        verify(ledgerEntryRepository, never()).save(any())
    }
})
