package com.cricauction.modules.auction.strategy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Supported bidding strategies for live auction increments.
 * Extensible for future strategies (e.g. TIERED_SLAB_INCREMENT, PROXY_BIDDING).
 */
public enum BiddingStrategyType {
    /**
     * Strategy 1: Next bid auto-increments based on a percentage provided by the organizer.
     */
    PERCENTAGE_INCREMENT("percentage_increment"),

    /**
     * Strategy 2: Current price + additional amount by captain (subject to minimum increment set by organizer).
     */
    ADDITIONAL_AMOUNT("additional_amount");

    private final String value;

    BiddingStrategyType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static BiddingStrategyType fromString(String text) {
        if (text == null || text.isBlank()) {
            return ADDITIONAL_AMOUNT;
        }
        for (BiddingStrategyType b : BiddingStrategyType.values()) {
            if (b.name().equalsIgnoreCase(text.trim()) || b.value.equalsIgnoreCase(text.trim())) {
                return b;
            }
        }
        throw new IllegalArgumentException("Unknown bidding strategy type: " + text);
    }
}
