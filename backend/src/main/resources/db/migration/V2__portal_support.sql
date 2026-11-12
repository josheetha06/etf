-- Additions the HR portal needs on top of V1. Nothing in V1 is changed or removed.

-- Interview times are saved as drafts first; candidates hear nothing until HR presses Send.
ALTER TYPE interview_status_t ADD VALUE IF NOT EXISTS 'draft' BEFORE 'invited';

-- Human-readable references, e.g. PR-2026-0007 for jobs and ETT-000123 for candidates.
CREATE SEQUENCE IF NOT EXISTS job_ref_seq;
CREATE SEQUENCE IF NOT EXISTS candidate_ref_seq;

-- Lookups used by the portal's lists and dashboards.
CREATE UNIQUE INDEX IF NOT EXISTS ux_hr_users_work_email_lower ON hr_users (lower(work_email));
CREATE INDEX IF NOT EXISTS ix_applications_job_stage ON applications (job_id, stage, status);
CREATE INDEX IF NOT EXISTS ix_interviews_scheduled_by_start ON interviews (scheduled_by, start_at);
CREATE INDEX IF NOT EXISTS ix_interviews_status_reply_by ON interviews (status, reply_by);
CREATE INDEX IF NOT EXISTS ix_reschedule_requests_status ON reschedule_requests (status);
CREATE INDEX IF NOT EXISTS ix_round1_reports_status ON round1_reports (status);
CREATE INDEX IF NOT EXISTS ix_shortlist_decisions_app_time ON shortlist_decisions (application_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_ai_screenings_app_time ON ai_screenings (application_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_audit_log_record ON audit_log (record_type, record_id, at DESC);
CREATE INDEX IF NOT EXISTS ix_login_log_user_time ON login_log (user_id, occurred_at DESC);
