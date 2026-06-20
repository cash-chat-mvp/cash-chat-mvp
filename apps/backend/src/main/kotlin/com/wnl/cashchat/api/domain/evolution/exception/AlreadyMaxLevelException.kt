package com.wnl.cashchat.api.domain.evolution.exception

class AlreadyMaxLevelException(
    message: String = "Already at max evolution level",
) : RuntimeException(message)