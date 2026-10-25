-- CricAuction Schema Migration V3: Support Email and Mobile OTP Login

ALTER TABLE otp_verifications DROP CONSTRAINT IF EXISTS otp_verifications_pkey;
ALTER TABLE otp_verifications ALTER COLUMN mobile DROP NOT NULL;
ALTER TABLE otp_verifications ADD COLUMN IF NOT EXISTS identifier VARCHAR(150);

-- Backfill identifier from mobile if existing
UPDATE otp_verifications SET identifier = mobile WHERE identifier IS NULL AND mobile IS NOT NULL;
UPDATE otp_verifications SET identifier = 'default' WHERE identifier IS NULL;
ALTER TABLE otp_verifications ALTER COLUMN identifier SET NOT NULL;

-- Re-establish primary key on (identifier, purpose)
ALTER TABLE otp_verifications ADD PRIMARY KEY (identifier, purpose);
CREATE INDEX IF NOT EXISTS idx_otp_identifier_expiry ON otp_verifications(identifier, expires_at);
