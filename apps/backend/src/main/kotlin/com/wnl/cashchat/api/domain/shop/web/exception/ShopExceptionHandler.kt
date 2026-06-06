package com.wnl.cashchat.api.domain.shop.web.exception

import com.wnl.cashchat.api.common.web.response.ErrorResponse
import com.wnl.cashchat.api.domain.shop.exception.IdempotencyKeyConflictException
import com.wnl.cashchat.api.domain.shop.exception.InsufficientCoinException
import com.wnl.cashchat.api.domain.shop.exception.ItemInactiveException
import com.wnl.cashchat.api.domain.shop.exception.ItemNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice(basePackages = ["com.wnl.cashchat.api.domain.shop"])
class ShopExceptionHandler {

    @ExceptionHandler(ItemNotFoundException::class)
    fun handleItemNotFound(e: ItemNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse("ITEM_NOT_FOUND", e.message ?: "Shop item not found"))

    @ExceptionHandler(ItemInactiveException::class)
    fun handleItemInactive(e: ItemInactiveException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse("ITEM_INACTIVE", e.message ?: "Shop item is inactive"))

    @ExceptionHandler(InsufficientCoinException::class)
    fun handleInsufficientCoin(e: InsufficientCoinException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse("INSUFFICIENT_COIN", e.message ?: "Insufficient coin balance"))

    @ExceptionHandler(IdempotencyKeyConflictException::class)
    fun handleIdempotencyConflict(e: IdempotencyKeyConflictException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse("IDEMPOTENCY_KEY_CONFLICT", e.message ?: "Idempotency key conflict"))

    // @Valid 실패(qty<1, idempotencyKey 형식 위반 등) → 400 VALIDATION
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(
                ErrorResponse(
                    "VALIDATION",
                    e.bindingResult.fieldErrors.firstOrNull()?.let { "${it.field}: ${it.defaultMessage}" }
                        ?: "Invalid request",
                ),
            )

    // 잘못된 JSON 본문 등 → 400 VALIDATION
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadable(e: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse("VALIDATION", "Malformed request body"))

    // category enum 범위 밖(예: ?category=FOO) → 400 INVALID_CATEGORY
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(e: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse("INVALID_CATEGORY", "Invalid category: ${e.value}"))
}
