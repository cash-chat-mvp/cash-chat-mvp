package com.wnl.cashchat.api.domain.energy.exception

class InsufficientEnergyException(
    message: String = "Not enough energy",
) : RuntimeException(message)
