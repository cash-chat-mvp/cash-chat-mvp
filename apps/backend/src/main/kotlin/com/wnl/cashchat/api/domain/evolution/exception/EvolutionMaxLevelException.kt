package com.wnl.cashchat.api.domain.evolution.exception

class EvolutionMaxLevelException(val level: Int) :
    RuntimeException("Already at max level $level")
