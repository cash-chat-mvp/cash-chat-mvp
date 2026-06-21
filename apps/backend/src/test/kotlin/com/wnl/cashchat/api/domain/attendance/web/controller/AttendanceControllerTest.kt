package com.wnl.cashchat.api.domain.attendance.web.controller

import com.wnl.cashchat.api.common.security.jwt.JwtTokenHandler
import com.wnl.cashchat.api.domain.attendance.exception.AlreadyCheckedInException
import com.wnl.cashchat.api.domain.attendance.service.AttendanceService
import com.wnl.cashchat.api.domain.attendance.service.BonusItem
import com.wnl.cashchat.api.domain.attendance.service.CheckInResult
import com.wnl.cashchat.api.domain.attendance.service.MonthlyAttendance
import com.wnl.cashchat.api.domain.attendance.service.RewardView
import com.wnl.cashchat.api.domain.attendance.web.exception.AttendanceExceptionHandler
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 의도적으로 웹 슬라이스(@WebMvcTest) 테스트다 — 라우팅·파라미터 검증·예외 매핑(상태코드/에러코드)만 검증한다.
 * 코드베이스 관례(ChatControllerTest 와 동일)를 따르며, DB 백엔드 통합(실제 시드·적립·트랜잭션)은
 * Testcontainers MySQL 기반의 AttendanceIntegrationTest 에서 별도로 다룬다.
 */
@WebMvcTest(AttendanceController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AttendanceExceptionHandler::class)
class AttendanceControllerTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var mockMvc: MockMvc

    @MockBean lateinit var attendanceService: AttendanceService
    @MockBean lateinit var jwtTokenHandler: JwtTokenHandler
    @MockBean(name = "jpaMappingContext") lateinit var jpaMappingContext: JpaMetamodelMappingContext

    private val principal = UsernamePasswordAuthenticationToken(1L, null)

    init {
        test("check-in returns awarded coin and streak") {
            whenever(attendanceService.checkIn(eq(1L), any())).thenReturn(
                CheckInResult(
                    awardedEnergy = 4L,
                    streakDayCount = 7,
                    bonusItems = listOf(BonusItem("EVO_STONE", 1)),
                    nextReward = RewardView(dayCount = 8, coin = 20L, bonusItems = emptyList()),
                )
            )

            mockMvc.perform(post("/api/attendance/check-in").principal(principal))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.awardedEnergy").value(4))
                .andExpect(jsonPath("$.streakDayCount").value(7))
                .andExpect(jsonPath("$.bonusItems[0].itemCode").value("EVO_STONE"))
                .andExpect(jsonPath("$.bonusItems[0].quantity").value(1))
                .andExpect(jsonPath("$.nextRewardPreview.dayCount").value(8))
                .andExpect(jsonPath("$.nextRewardPreview.coin").value(20))
        }

        test("duplicate check-in returns 409 ALREADY_CHECKED_IN") {
            whenever(attendanceService.checkIn(eq(1L), any())).thenThrow(AlreadyCheckedInException())

            mockMvc.perform(post("/api/attendance/check-in").principal(principal))
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.code").value("ALREADY_CHECKED_IN"))
        }

        test("GET /me without params returns monthly calendar") {
            whenever(attendanceService.getMonthly(eq(1L), any(), any(), any())).thenReturn(
                MonthlyAttendance(
                    year = 2026, month = 5, checkedDays = listOf(1, 2, 3),
                    currentStreak = 3, todayChecked = true,
                    nextReward = RewardView(dayCount = 4, coin = 20L, bonusItems = emptyList()),
                )
            )

            mockMvc.perform(get("/api/attendance/me").principal(principal))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.checkedDays.length()").value(3))
                .andExpect(jsonPath("$.currentStreak").value(3))
                .andExpect(jsonPath("$.todayChecked").value(true))
        }

        test("GET /me with only year returns 400") {
            mockMvc.perform(get("/api/attendance/me").param("year", "2026").principal(principal))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("INVALID_ATTENDANCE_QUERY"))
        }

        test("GET /me with month out of range returns 400") {
            mockMvc.perform(
                get("/api/attendance/me").param("year", "2026").param("month", "13").principal(principal)
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("INVALID_ATTENDANCE_QUERY"))
        }

        test("GET /me with non-numeric month normalizes the type-mismatch to 400 INVALID_ATTENDANCE_QUERY") {
            mockMvc.perform(
                get("/api/attendance/me").param("year", "2026").param("month", "foo").principal(principal)
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("INVALID_ATTENDANCE_QUERY"))
        }
    }
}
