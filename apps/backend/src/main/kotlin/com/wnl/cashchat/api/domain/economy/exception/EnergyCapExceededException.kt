package com.wnl.cashchat.api.domain.economy.exception

class EnergyCapExceededException(message: String = "Energy 보유 상한을 초과했습니다.") : RuntimeException(message)
