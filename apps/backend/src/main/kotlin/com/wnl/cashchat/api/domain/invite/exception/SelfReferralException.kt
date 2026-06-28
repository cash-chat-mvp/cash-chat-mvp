package com.wnl.cashchat.api.domain.invite.exception

class SelfReferralException : RuntimeException("Cannot redeem your own invite code")
