-- ==============================================================================
-- CricAuction Schema Migration V10: Live Auction Engine (Module 7)
-- Supports real-time bidding, hammer execution, audit ledger, and wishlists
-- ==============================================================================

-- 1. Live Auction Fast State Table
CREATE TABLE IF NOT EXISTS live_auction_state (
    tournament_id BIGINT PRIMARY KEY REFERENCES tournaments(id) ON DELETE CASCADE,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    current_player_id BIGINT REFERENCES tournament_players(id) ON DELETE SET NULL,
    current_bid BIGINT NOT NULL DEFAULT 0,
    leading_team_id BIGINT REFERENCES teams(id) ON DELETE SET NULL,
    timer_seconds INT NOT NULL DEFAULT 10,
    is_paused BOOLEAN NOT NULL DEFAULT FALSE,
    going_twice_warning INT NOT NULL DEFAULT 0,
    round INT NOT NULL DEFAULT 1,
    bid_strategy VARCHAR(50) NOT NULL DEFAULT 'ADDITIONAL_AMOUNT',
    increment_percentage NUMERIC(5, 2) DEFAULT 10.00,
    min_bid_increment BIGINT DEFAULT 1000,
    started_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2. Auction Bids Audit Ledger
CREATE TABLE IF NOT EXISTS auction_bids (
    id BIGSERIAL PRIMARY KEY,
    tournament_id BIGINT NOT NULL REFERENCES tournaments(id) ON DELETE CASCADE,
    tournament_player_id BIGINT NOT NULL REFERENCES tournament_players(id) ON DELETE CASCADE,
    team_id BIGINT NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    bid_amount BIGINT NOT NULL,
    bid_number INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_auction_bids_tourn_player ON auction_bids(tournament_id, tournament_player_id);
CREATE INDEX IF NOT EXISTS idx_auction_bids_timeline ON auction_bids(tournament_id, id DESC);

-- 3. Captain Wishlists Table
CREATE TABLE IF NOT EXISTS captain_wishlists (
    id BIGSERIAL PRIMARY KEY,
    tournament_id BIGINT NOT NULL REFERENCES tournaments(id) ON DELETE CASCADE,
    team_id BIGINT NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    tournament_player_id BIGINT NOT NULL REFERENCES tournament_players(id) ON DELETE CASCADE,
    max_budget BIGINT,
    priority VARCHAR(20) DEFAULT 'Normal',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_captain_wishlist UNIQUE (team_id, tournament_player_id)
);

CREATE INDEX IF NOT EXISTS idx_wishlist_team ON captain_wishlists(team_id);
CREATE INDEX IF NOT EXISTS idx_wishlist_tournament ON captain_wishlists(tournament_id);
