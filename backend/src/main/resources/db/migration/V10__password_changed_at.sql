-- Logins (JWTs) issued before this time stop working: set on password change and admin reset.
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS password_changed_at TIMESTAMP;
