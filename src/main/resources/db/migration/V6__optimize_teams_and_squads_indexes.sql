-- ==============================================================================
-- CricAuction Schema Migration V6: Optimize Team & Squad Queries (Module 5)
-- Adds composite indexes for faster team listing and pending requests filtering
-- ==============================================================================

CREATE INDEX IF NOT EXISTS idx_teams_tournament_status_id ON teams(tournament_id, status, id);
CREATE INDEX IF NOT EXISTS idx_teams_tournament_id ON teams(tournament_id, id);
CREATE INDEX IF NOT EXISTS idx_teams_tournament_status_created ON teams(tournament_id, status, created_at);
