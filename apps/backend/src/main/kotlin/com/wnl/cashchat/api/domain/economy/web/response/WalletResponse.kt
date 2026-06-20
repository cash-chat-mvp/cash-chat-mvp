package com.wnl.cashchat.api.domain.economy.web.response

data class WalletResponse(
    val energyAvailable: Long,
    val energyReserved: Long,
    val maxEnergy: Long,
    val pendingCashablePt: Long,
    val confirmedCashablePt: Long,
    val evolutionExp: Long,
)
