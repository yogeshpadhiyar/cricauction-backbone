package com.cricauction.modules.auction.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Registry and resolver for bidding strategies.
 * Supports adding further strategies seamlessly via Spring Dependency Injection.
 */
@Slf4j
@Component
public class BidStrategyResolver {

    private final Map<BiddingStrategyType, BidIncrementStrategy> strategyMap;

    public BidStrategyResolver(List<BidIncrementStrategy> strategies) {
        this.strategyMap = new EnumMap<>(BiddingStrategyType.class);
        for (BidIncrementStrategy strategy : strategies) {
            strategyMap.put(strategy.getStrategyType(), strategy);
            log.info("Registered Bidding Strategy: [{}] -> {}", strategy.getStrategyType(), strategy.getClass().getSimpleName());
        }
    }

    /**
     * Resolves the matching strategy bean for the requested type.
     * Falls back to ADDITIONAL_AMOUNT if type is unspecified or not registered.
     */
    public BidIncrementStrategy resolve(BiddingStrategyType type) {
        if (type == null) {
            type = BiddingStrategyType.ADDITIONAL_AMOUNT;
        }
        BidIncrementStrategy strategy = strategyMap.get(type);
        if (strategy == null) {
            log.warn("Strategy [{}] not found. Falling back to ADDITIONAL_AMOUNT", type);
            strategy = strategyMap.get(BiddingStrategyType.ADDITIONAL_AMOUNT);
        }
        return strategy;
    }
}
