package com.wnl.cashchat.api.domain.energy.web.response

import com.wnl.cashchat.api.domain.energy.service.EnergyView

data class EnergyResponse(
    val energy: Int,
    val maxEnergy: Int,
) {
    companion object {
        fun from(view: EnergyView) = EnergyResponse(energy = view.energy, maxEnergy = view.maxEnergy)
    }
}
