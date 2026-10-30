-- ==============================================================================
-- CricAuction Schema Migration V7: Add Unique Captain Partial Index (Module 5)
-- Prevents race conditions from ever inserting duplicate captains for a team.
-- ==============================================================================

CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_team_captain
ON squad_members(team_id)
WHERE is_captain = true;

