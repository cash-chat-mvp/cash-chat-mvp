package com.wnl.cashchat.api.domain.ad.persistence.entity

import java.io.Serializable
import java.time.LocalDate

data class AdRewardDailyQuotaId(
    val userId: Long = 0,
    val kstDate: LocalDate = LocalDate.MIN,
) : Serializable
