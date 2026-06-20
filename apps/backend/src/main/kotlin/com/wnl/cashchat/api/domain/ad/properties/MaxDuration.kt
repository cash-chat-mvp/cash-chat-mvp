package com.wnl.cashchat.api.domain.ad.properties

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import java.time.Duration
import kotlin.reflect.KClass

@MustBeDocumented
@Constraint(validatedBy = [PositiveDurationValidator::class])
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class PositiveDuration(
    val message: String = "must be a positive duration",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class PositiveDurationValidator : ConstraintValidator<PositiveDuration, Duration> {
    override fun isValid(value: Duration?, context: ConstraintValidatorContext): Boolean =
        value == null || !value.isZero && !value.isNegative
}

@MustBeDocumented
@Constraint(validatedBy = [MaxDurationValidator::class])
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class MaxDuration(
    val hours: Long,
    val message: String = "must be less than or equal to {hours} hours",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class MaxDurationValidator : ConstraintValidator<MaxDuration, Duration> {
    private lateinit var max: Duration

    override fun initialize(constraintAnnotation: MaxDuration) {
        max = Duration.ofHours(constraintAnnotation.hours)
    }

    override fun isValid(value: Duration?, context: ConstraintValidatorContext): Boolean =
        value == null || value <= max
}
