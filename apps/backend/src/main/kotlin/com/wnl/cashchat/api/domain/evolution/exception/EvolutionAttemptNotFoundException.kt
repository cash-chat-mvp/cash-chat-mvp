package com.wnl.cashchat.api.domain.evolution.exception

class EvolutionAttemptNotFoundException(val attemptId: Long) :
    RuntimeException("Evolution attempt $attemptId not found")
