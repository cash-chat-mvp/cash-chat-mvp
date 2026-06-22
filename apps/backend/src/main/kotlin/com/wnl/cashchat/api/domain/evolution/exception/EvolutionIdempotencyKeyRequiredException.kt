package com.wnl.cashchat.api.domain.evolution.exception

class EvolutionIdempotencyKeyRequiredException :
    RuntimeException("Idempotency-Key header is required")
