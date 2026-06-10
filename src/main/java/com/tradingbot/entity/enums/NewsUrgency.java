package com.tradingbot.entity.enums;

public enum NewsUrgency {
    IMMEDIATE,   // act within seconds — breaking news, hacks, bans, approvals
    WITHIN_1H,   // significant but not instant — listings, whale moves
    WITHIN_4H    // general sentiment — analyst opinions, slow-burn articles
}
