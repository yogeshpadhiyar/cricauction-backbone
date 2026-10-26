-- ==============================================================================
-- CricAuction Schema Migration V3: Teams and Squads (Module 5)
-- Supports team creation, captain invites, squad rosters, and purse tracking
-- ==============================================================================

-- 1. Teams Table
CREATE TABLE IF NOT EXISTS teams (
    id BIGSERIAL PRIMARY KEY,
    tournament_id BIGINT NOT NULL REFERENCES tournaments(id) ON DELETE CASCADE,
    captain_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    name VARCHAR(100) NOT NULL,
    short_code VARCHAR(10),
    logo_url VARCHAR(500),
    captain_name VARCHAR(100),
    captain_mobile VARCHAR(20),
    initial_budget BIGINT NOT NULL DEFAULT 1000000,
    remaining_budget BIGINT NOT NULL DEFAULT 1000000,
    invite_code VARCHAR(100) UNIQUE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'Pending',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_teams_tournament ON teams(tournament_id);
CREATE INDEX IF NOT EXISTS idx_teams_captain ON teams(captain_id);
CREATE INDEX IF NOT EXISTS idx_teams_invite_code ON teams(invite_code);
CREATE INDEX IF NOT EXISTS idx_teams_tournament_status ON teams(tournament_id, status);

-- 2. Squad Members Table
CREATE TABLE IF NOT EXISTS squad_members (
    id BIGSERIAL PRIMARY KEY,
    team_id BIGINT NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    player_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    player_name VARCHAR(100) NOT NULL,
    playing_role VARCHAR(50) DEFAULT 'All-Rounder',
    purchase_price BIGINT NOT NULL DEFAULT 0,
    is_captain BOOLEAN NOT NULL DEFAULT FALSE,
    photo_url VARCHAR(500),
    bought_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_squad_members_team ON squad_members(team_id);
CREATE INDEX IF NOT EXISTS idx_squad_members_user ON squad_members(player_user_id);
