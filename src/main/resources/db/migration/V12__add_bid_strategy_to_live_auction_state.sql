-- ==============================================================================
-- CricAuction Schema Migration V12: Bidding Strategies for Live Auction Engine
-- Supports 2 bidding strategies:
-- 1. PERCENTAGE_INCREMENT: auto-increment based on organizer percentage
-- 2. ADDITIONAL_AMOUNT: current price + additional amount with minimum increment
-- ==============================================================================

ALTER TABLE live_auction_state 
    ADD COLUMN IF NOT EXISTS bid_strategy VARCHAR(50) NOT NULL DEFAULT 'ADDITIONAL_AMOUNT',
    ADD COLUMN IF NOT EXISTS increment_percentage NUMERIC(5, 2) DEFAULT 10.00,
    ADD COLUMN IF NOT EXISTS min_bid_increment BIGINT DEFAULT 1000;
