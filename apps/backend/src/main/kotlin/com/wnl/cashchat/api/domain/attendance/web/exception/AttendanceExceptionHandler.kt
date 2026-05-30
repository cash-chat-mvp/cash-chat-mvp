package com.wnl.cashchat.api.domain.attendance.web.exception

import com.wnl.cashchat.api.common.web.response.ErrorResponse
import com.wnl.cashchat.api.domain.attendance.exception.AlreadyCheckedInException
import com.wnl.cashchat.api.domain.attendance.exception.InvalidAttendanceQueryException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice(basePackages = ["com.wnl.cashchat.api.domain.attendance"])
class AttendanceExceptionHandler {

    @ExceptionHandler(AlreadyCheckedInException::class)
    fun handleAlreadyCheckedIn(e: AlreadyCheckedInException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse("ALREADY_CHECKED_IN", e.message ?: "Already checked in today"))

    @ExceptionHandler(InvalidAttendanceQueryException::class)
    fun handleInvalidQuery(e: InvalidAttendanceQueryException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse("INVALID_ATTENDANCE_QUERY", e.message ?: "Invalid attendance query"))

    /**
     * year/month 같은 숫자 쿼리 파라미터가 잘못된 타입(예: month=foo)일 때 Spring 이 던지는
     * 변환 실패를 InvalidAttendanceQueryException 과 동일한 400 응답으로 정규화한다.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(e: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse("INVALID_ATTENDANCE_QUERY", "Invalid attendance query parameter: ${e.name}"))
}
