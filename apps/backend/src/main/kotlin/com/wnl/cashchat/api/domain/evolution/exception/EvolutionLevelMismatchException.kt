package com.wnl.cashchat.api.domain.evolution.exception

class EvolutionLevelMismatchException(val expected: Int, val actual: Int) :
    RuntimeException("Expected level $expected but current is $actual")
