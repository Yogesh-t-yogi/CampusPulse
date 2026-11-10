-- V5 (Phase 6.5): bulk student onboarding.
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS roll_number VARCHAR(50);
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS batch_year INT;
-- Accounts created by an admin/import get a one-time password and must change it at first sign-in.
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

-- A roll number is unique within a college.
CREATE UNIQUE INDEX IF NOT EXISTS uq_app_user_roll
    ON app_user(institution_id, roll_number)
    WHERE roll_number IS NOT NULL;
