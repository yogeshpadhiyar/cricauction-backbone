-- CricAuction Schema Migration V4: Add bio to users and create player_profiles table

ALTER TABLE users ADD COLUMN IF NOT EXISTS bio VARCHAR(500);

CREATE TABLE IF NOT EXISTS player_profiles (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    playing_role VARCHAR(50) NOT NULL,
    batting_style VARCHAR(50),
    bowling_style VARCHAR(50),
    jersey_number INT,
    cricheroes_link VARCHAR(300),
    past_tournaments INT DEFAULT 0,
    highest_score INT DEFAULT 0,
    best_bowling VARCHAR(20),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_player_profiles_role ON player_profiles(playing_role);
