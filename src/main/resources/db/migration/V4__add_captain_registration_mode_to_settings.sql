-- V4: Add Captain Registration & Approval Mode to Tournament Settings
-- Allows organizers to configure:
-- 1. Registration mode: INVITE_ONLY (default) or BOTH (supports both invites and open captain self-registration)
-- 2. Approval mode: ORGANIZER_APPROVAL (default) or AUTO (auto-approve upon registration)

ALTER TABLE tournament_settings 
ADD COLUMN IF NOT EXISTS captain_registration_mode VARCHAR(30) NOT NULL DEFAULT 'INVITE_ONLY';

ALTER TABLE tournament_settings 
ADD COLUMN IF NOT EXISTS captain_approval_mode VARCHAR(30) NOT NULL DEFAULT 'ORGANIZER_APPROVAL';

COMMENT ON COLUMN tournament_settings.captain_registration_mode IS 'Modes: INVITE_ONLY (default) or BOTH (supports both invites and open captain self-registration)';
COMMENT ON COLUMN tournament_settings.captain_approval_mode IS 'Approval Modes: ORGANIZER_APPROVAL (default) or AUTO (auto-approve upon registration)';
