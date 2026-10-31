-- ==============================================================================
-- CricAuction Schema Migration V11: Matches, Fixtures & Scoring (Module 8)
-- Supports match scheduling, live ball-by-ball scoring, scorecards,
-- commentary audit ledger, and automated tournament standings.
-- ==============================================================================

-- 1. Matches Table
CREATE TABLE IF NOT EXISTS matches (
    id BIGSERIAL PRIMARY KEY,
    public_id VARCHAR(50) NOT NULL UNIQUE,
    tournament_id BIGINT NOT NULL REFERENCES tournaments(id) ON DELETE CASCADE,
    team1_id BIGINT NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    team2_id BIGINT NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    scheduled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ground VARCHAR(150),
    overs INT NOT NULL DEFAULT 20,
    status VARCHAR(30) NOT NULL DEFAULT 'UPCOMING',
    winner_team_id BIGINT REFERENCES teams(id) ON DELETE SET NULL,
    result_description VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_matches_tourn_status ON matches(tournament_id, status);
CREATE INDEX IF NOT EXISTS idx_matches_team1 ON matches(team1_id);
CREATE INDEX IF NOT EXISTS idx_matches_team2 ON matches(team2_id);
CREATE INDEX IF NOT EXISTS idx_matches_scheduled ON matches(tournament_id, scheduled_at);

-- 2. Match Scorecards Fast State Table
CREATE TABLE IF NOT EXISTS match_scorecards (
    match_id BIGINT PRIMARY KEY REFERENCES matches(id) ON DELETE CASCADE,
    team1_runs INT NOT NULL DEFAULT 0,
    team1_wickets INT NOT NULL DEFAULT 0,
    team1_balls INT NOT NULL DEFAULT 0,
    team2_runs INT NOT NULL DEFAULT 0,
    team2_wickets INT NOT NULL DEFAULT 0,
    team2_balls INT NOT NULL DEFAULT 0,
    current_innings INT NOT NULL DEFAULT 1,
    current_batsman_id BIGINT,
    current_batsman_name VARCHAR(100),
    current_batsman_runs INT NOT NULL DEFAULT 0,
    current_batsman_balls INT NOT NULL DEFAULT 0,
    current_bowler_id BIGINT,
    current_bowler_name VARCHAR(100),
    current_bowler_balls INT NOT NULL DEFAULT 0,
    current_bowler_wickets INT NOT NULL DEFAULT 0,
    current_bowler_runs INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. Match Balls Audit & Commentary Ledger
CREATE TABLE IF NOT EXISTS match_balls (
    id BIGSERIAL PRIMARY KEY,
    match_id BIGINT NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
    innings INT NOT NULL DEFAULT 1,
    over_number INT NOT NULL,
    ball_number INT NOT NULL,
    runs INT NOT NULL DEFAULT 0,
    is_wicket BOOLEAN NOT NULL DEFAULT FALSE,
    wicket_type VARCHAR(30),
    extra_runs INT NOT NULL DEFAULT 0,
    extra_type VARCHAR(20),
    batsman_id BIGINT,
    batsman_name VARCHAR(100),
    bowler_id BIGINT,
    bowler_name VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_match_innings_over_ball UNIQUE (match_id, innings, over_number, ball_number)
);

CREATE INDEX IF NOT EXISTS idx_match_balls_timeline ON match_balls(match_id, innings, id DESC);

