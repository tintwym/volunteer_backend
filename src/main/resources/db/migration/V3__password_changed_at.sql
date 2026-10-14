-- Tokens issued before this instant are rejected (set on password change/reset).
ALTER TABLE users ADD COLUMN password_changed_at TIMESTAMPTZ;
