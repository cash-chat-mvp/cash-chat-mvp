package com.wnl.cashchat.api.domain.roulette.exception

class FreeSpinAvailableException : RuntimeException("Free spin must be used before ad-gated spins")

class FreeSpinUsedException : RuntimeException("Free spin already used today")

class DailyLimitReachedException : RuntimeException("Daily roulette spin limit reached")

class AdNotVerifiedException : RuntimeException("Roulette ad nonce is not verified")

class NonceAlreadyUsedException : RuntimeException("Roulette ad nonce is already used")
