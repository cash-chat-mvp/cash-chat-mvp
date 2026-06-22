package com.wnl.cashchat.api.domain.evolution.exception

class EvolutionInsufficientExpException(val required: Long, val current: Long) :
    RuntimeException("Need $required exp but have $current")
