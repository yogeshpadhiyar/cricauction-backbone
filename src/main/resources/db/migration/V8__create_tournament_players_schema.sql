-- ==============================================================================
-- CricAuction Schema Migration V8: Player Registrations & Capacity Management (Module 6)
-- Supports player tournament enrollment, capacity tracking, approval workflows,
-- base price customization, and withdrawal handling.
-- ==============================================================================

-- 1. Create tournament_players table
CREATE TABLE IF NOT EXISTS tournament_players (
    id BIGSERIAL PRIMARY KEY,
    tournament_id BIGINT NOT NULL REFERENCES tournaments(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    player_name VARCHAR(100) NOT NULL,
    registered_role VARCHAR(50) NOT NULL,
    batting_style VARCHAR(50),
    bowling_style VARCHAR(50),
    base_price BIGINT DEFAULT 5000,
    city VARCHAR(100),
    mobile VARCHAR(20),
    jersey_size VARCHAR(10),
    cric_heroes_link VARCHAR(500),
    photo_url VARCHAR(500),
    status VARCHAR(30) NOT NULL DEFAULT 'Registered',
    review_reason VARCHAR(500),
    lot_number INT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tournament_player UNIQUE (tournament_id, user_id)
);

-- 2. Performance & Lookup Indexes
CREATE INDEX IF NOT EXISTS idx_tourn_players_tournament ON tournament_players(tournament_id);
CREATE INDEX IF NOT EXISTS idx_tourn_players_user ON tournament_players(user_id);
CREATE INDEX IF NOT EXISTS idx_tourn_players_status ON tournament_players(tournament_id, status);
CREATE INDEX IF NOT EXISTS idx_tourn_players_role ON tournament_players(tournament_id, registered_role);
CREATE INDEX IF NOT EXISTS idx_tourn_players_search ON tournament_players(tournament_id, player_name);

-- 3. Add registered_players_count to tournament_settings for atomic reservation & fast capacity stats
ALTER TABLE tournament_settings ADD COLUMN IF NOT EXISTS registered_players_count INT NOT NULL DEFAULT 0;

-- 4. Forward compatibility link for squad_members
ALTER TABLE squad_members ADD COLUMN IF NOT EXISTS tournament_player_id BIGINT REFERENCES tournament_players(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_squad_members_tourn_player ON squad_members(tournament_player_id);
