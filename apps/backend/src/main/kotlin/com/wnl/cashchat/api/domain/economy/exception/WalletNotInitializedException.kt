package com.wnl.cashchat.api.domain.economy.exception

class WalletNotInitializedException(userId: Long) :
    RuntimeException("Wallet not initialized for userId=$userId")
