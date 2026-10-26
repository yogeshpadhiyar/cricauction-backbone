-- ==============================================================================
-- CricAuction Schema Migration V2: Tournaments & Settings (Module 4)
-- Supports complete tournament lifecycle, rules, budgets, and public slug lookup
-- ==============================================================================

-- 1. Tournaments Table
CREATE TABLE IF NOT EXISTS tournaments (
    id BIGSERIAL PRIMARY KEY,
    public_id VARCHAR(50) UNIQUE NOT NULL,
    organizer_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(150) NOT NULL,
    slug VARCHAR(180) UNIQUE NOT NULL,
    location VARCHAR(150) NOT NULL,
    ground VARCHAR(150) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    auction_date DATE,
    auction_time VARCHAR(20),
    registration_start_date DATE,
    registration_end_date DATE,
    ball_type VARCHAR(50) NOT NULL,
    format VARCHAR(50) NOT NULL,
    entry_fee VARCHAR(50) DEFAULT 'Free',
    description TEXT,
    banner_url VARCHAR(500),
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    step INT NOT NULL DEFAULT 1,
    registration_link VARCHAR(500),
    captain_invite_link VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tournaments_organizer ON tournaments(organizer_id);
CREATE INDEX IF NOT EXISTS idx_tournaments_slug ON tournaments(slug);
CREATE INDEX IF NOT EXISTS idx_tournaments_public_id ON tournaments(public_id);
CREATE INDEX IF NOT EXISTS idx_tournaments_status ON tournaments(status);
CREATE INDEX IF NOT EXISTS idx_tournaments_organizer_status ON tournaments(organizer_id, status);

-- 2. Tournament Settings Table (1-to-1 with Tournaments)
CREATE TABLE IF NOT EXISTS tournament_settings (
    tournament_id BIGINT PRIMARY KEY REFERENCES tournaments(id) ON DELETE CASCADE,
    total_teams INT NOT NULL DEFAULT 10,
    squad_minimum INT NOT NULL DEFAULT 12,
    squad_maximum INT NOT NULL DEFAULT 15,
    team_budget BIGINT NOT NULL DEFAULT 1000000,
    default_base_price BIGINT NOT NULL DEFAULT 20000,
    bid_increment BIGINT NOT NULL DEFAULT 10000,
    bid_timer_seconds INT NOT NULL DEFAULT 10,
    auto_start_next BOOLEAN NOT NULL DEFAULT FALSE,
    matches INT NOT NULL DEFAULT 20,
    match_duration_hours INT NOT NULL DEFAULT 3,
    allow_players_to_withdraw BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tournament_settings_teams ON tournament_settings(total_teams);
