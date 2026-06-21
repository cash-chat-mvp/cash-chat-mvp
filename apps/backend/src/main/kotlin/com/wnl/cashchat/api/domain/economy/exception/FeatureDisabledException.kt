package com.wnl.cashchat.api.domain.economy.exception

class FeatureDisabledException(val feature: String) : RuntimeException("Feature disabled: $feature")
