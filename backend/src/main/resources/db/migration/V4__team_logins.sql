-- People added by an HR admin get a one-time password and choose their own on first sign-in.
ALTER TABLE hr_users
    ADD COLUMN must_change_password boolean NOT NULL DEFAULT false,
    ADD COLUMN password_changed_at  timestamptz;

CREATE UNIQUE INDEX IF NOT EXISTS ux_hr_users_email_lower ON hr_users (lower(work_email));
