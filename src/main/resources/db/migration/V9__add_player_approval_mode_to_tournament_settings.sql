-- ==============================================================================
-- CricAuction Schema Migration V9: Add player_approval_mode to tournament_settings
-- Allows organizers to choose between AUTO_APPROVAL or ORGANIZER_APPROVAL for players
-- ==============================================================================

ALTER TABLE tournament_settings 
ADD COLUMN IF NOT EXISTS player_approval_mode VARCHAR(255) NOT NULL DEFAULT 'ORGANIZER_APPROVAL';
