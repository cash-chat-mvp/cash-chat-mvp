package com.wnl.cashchat.api.domain.evolution.service

/** 길게누르기 타이밍 등급. bonusRate 는 성공 확률 가산치(%p). FE TimingGrade 와 동일 의미. */
enum class TimingGrade(val bonusRate: Double) {
    NORMAL(0.0),
    GREAT(0.05),
    PERFECT(0.10),
}
