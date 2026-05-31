package com.wnl.cashchat.api.domain.ad.persistence.repository

import com.wnl.cashchat.api.domain.ad.persistence.entity.AdRewardNonce
import org.springframework.data.jpa.repository.JpaRepository

interface AdRewardNonceRepository : JpaRepository<AdRewardNonce, String>
