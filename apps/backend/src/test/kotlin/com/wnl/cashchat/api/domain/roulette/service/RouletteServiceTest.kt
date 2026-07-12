package com.wnl.cashchat.api.domain.roulette.service

import com.wnl.cashchat.api.domain.energy.service.EnergyService
import com.wnl.cashchat.api.domain.energy.service.EnergyView
import com.wnl.cashchat.api.domain.roulette.exception.FreeSpinAvailableException
import com.wnl.cashchat.api.domain.roulette.persistence.entity.RouletteAdNonce
import com.wnl.cashchat.api.domain.roulette.persistence.entity.RouletteDailyState
import com.wnl.cashchat.api.domain.roulette.persistence.entity.RoulettePrize
import com.wnl.cashchat.api.domain.roulette.persistence.entity.RouletteSpin
import com.wnl.cashchat.api.domain.roulette.persistence.entity.RouletteSpinType
import com.wnl.cashchat.api.domain.roulette.persistence.repository.RouletteAdNonceRepository
import com.wnl.cashchat.api.domain.roulette.persistence.repository.RouletteDailyStateRepository
import com.wnl.cashchat.api.domain.roulette.persistence.repository.RouletteSpinRepository
import com.wnl.cashchat.api.domain.roulette.properties.RouletteProperties
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class RouletteServiceTest : FunSpec({
    lateinit var dailyStateRepository: RouletteDailyStateRepository
    lateinit var adNonceRepository: RouletteAdNonceRepository
    lateinit var spinRepository: RouletteSpinRepository
    lateinit var energyService: EnergyService
    lateinit var prizeDrawService: RoulettePrizeDrawService
    lateinit var service: RouletteService

    val properties = RouletteProperties(nonceTtl = Duration.ofMinutes(10))
    val now = Instant.parse("2026-06-21T03:00:00Z")
    val kstDate = LocalDate.of(2026, 6, 21)

    beforeTest {
        dailyStateRepository = mock()
        adNonceRepository = mock()
        spinRepository = mock()
        energyService = mock()
        prizeDrawService = mock()
        service = RouletteService(
            dailyStateRepository = dailyStateRepository,
            adNonceRepository = adNonceRepository,
            spinRepository = spinRepository,
            energyService = energyService,
            prizeDrawService = prizeDrawService,
            properties = properties,
        )
    }

    test("free spin records nominal prize energy and reports actual capped award") {
        val state = RouletteDailyState(userId = 1L, kstDate = kstDate)
        whenever(dailyStateRepository.findForUpdate(1L, kstDate)).thenReturn(state)
        whenever(prizeDrawService.draw()).thenReturn(RoulettePrizeDraw(RoulettePrize.E10, prizeEnergy = 10))
        whenever(energyService.getEnergy(1L))
            .thenReturn(EnergyView(energy = 48, maxEnergy = 50))
            .thenReturn(EnergyView(energy = 50, maxEnergy = 50))
        whenever(spinRepository.saveAndFlush(any<RouletteSpin>())).thenAnswer { it.arguments[0] }

        val result = service.spinFree(1L, now)

        result.prize shouldBe RoulettePrize.E10
        result.prizeEnergy shouldBe 10
        result.awardedEnergy shouldBe 2
        result.energyAfter shouldBe 50
        result.status.spinsUsedToday shouldBe 1
        result.status.freeSpinAvailable shouldBe false
        verify(energyService).charge(1L, 10)
    }

    test("issue nonce is rejected while the free spin is still available") {
        val state = RouletteDailyState(userId = 1L, kstDate = kstDate)
        whenever(dailyStateRepository.findForUpdate(1L, kstDate)).thenReturn(state)

        shouldThrow<FreeSpinAvailableException> {
            service.issueNonce(1L, now)
        }
    }

    test("ad spin replays when the nonce is already used by a concurrent request") {
        val state = RouletteDailyState(userId = 1L, kstDate = kstDate, spinsUsed = 1, freeSpinsUsed = 1)
        val nonce = RouletteAdNonce(
            nonce = "nonce-1",
            userId = 1L,
            expiresAt = now.plus(Duration.ofMinutes(10)),
            verified = true,
            used = true,
        )
        val existingSpin = RouletteSpin(
            userId = 1L,
            kstDate = kstDate,
            spinType = RouletteSpinType.AD,
            prize = RoulettePrize.E3,
            prizeEnergy = 3,
            awardedEnergy = 3,
            energyAfter = 43,
            segmentIndex = 4,
            nonce = "nonce-1",
        )
        whenever(spinRepository.findByNonce("nonce-1")).thenReturn(null, existingSpin)
        whenever(dailyStateRepository.findForUpdate(1L, kstDate)).thenReturn(state)
        whenever(adNonceRepository.findForUpdate("nonce-1")).thenReturn(nonce)
        whenever(dailyStateRepository.findByUserIdAndKstDate(1L, kstDate)).thenReturn(state)

        val result = service.spinWithAd(1L, "nonce-1", now)

        result.prize shouldBe RoulettePrize.E3
        result.segmentIndex shouldBe 4
        result.awardedEnergy shouldBe 3
        result.status.spinsUsedToday shouldBe 1
    }
})
