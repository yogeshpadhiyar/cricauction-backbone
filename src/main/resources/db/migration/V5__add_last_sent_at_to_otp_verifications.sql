-- V5: Add last_sent_at timestamp to otp_verifications for accurate cooldown throttling
ALTER TABLE otp_verifications ADD COLUMN IF NOT EXISTS last_sent_at TIMESTAMP WITH TIME ZONE;

