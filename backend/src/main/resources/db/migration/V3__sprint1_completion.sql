-- =====================================================================
-- V3: Sprint 1 completion
--   client branding (logo), company settings, user alert preferences,
--   CV intake queue, candidate key facts and notes, Scoring 2.0 details
--   and red flags, report reviews, Round 2 and Round 3 additions,
--   notifications and the client skills dictionary.
--
-- Only adds types, tables, columns and indexes. V1 (the ETT HR schema)
-- is unchanged. New tables carry the same ownership columns as V1 and
-- use its last-modified and audit-trail triggers.
-- =====================================================================

-- Where a candidate came from: recruitment agencies and job boards.
ALTER TYPE application_source_t ADD VALUE IF NOT EXISTS 'agency';
ALTER TYPE application_source_t ADD VALUE IF NOT EXISTS 'job_board';

-- ---------------------------------------------------------------------
-- Client branding and settings
-- ---------------------------------------------------------------------

-- The client's own logo, shown in the portal header for everyone at that company.
-- PNG, JPEG or WebP only (no SVG, which can carry scripts); at most 1 MB.
CREATE TABLE company_logos (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id       uuid NOT NULL UNIQUE REFERENCES companies(id) ON DELETE CASCADE,
    content_type     text NOT NULL CHECK (content_type IN ('image/png', 'image/jpeg', 'image/webp')),
    file_name        text,
    image            bytea NOT NULL CHECK (octet_length(image) BETWEEN 1 AND 1048576),
    created_by       uuid,
    created_at       timestamptz NOT NULL DEFAULT now(),
    last_modified_by uuid,
    last_modified_at timestamptz NOT NULL DEFAULT now(),
    owner_id         uuid,
    assigned_id      uuid
);

-- Per-client screening settings.
CREATE TABLE company_settings (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id        uuid NOT NULL UNIQUE REFERENCES companies(id) ON DELETE CASCADE,
    shortlist_cutoff  integer NOT NULL DEFAULT 70 CHECK (shortlist_cutoff BETWEEN 1 AND 100),
    reserve_cutoff    integer NOT NULL DEFAULT 50 CHECK (reserve_cutoff BETWEEN 0 AND 99),
    gap_flag_months   integer NOT NULL DEFAULT 6 CHECK (gap_flag_months BETWEEN 1 AND 60),
    short_job_months  integer NOT NULL DEFAULT 12 CHECK (short_job_months BETWEEN 1 AND 60),
    created_by        uuid,
    created_at        timestamptz NOT NULL DEFAULT now(),
    last_modified_by  uuid,
    last_modified_at  timestamptz NOT NULL DEFAULT now(),
    owner_id          uuid,
    assigned_id       uuid,
    CHECK (reserve_cutoff < shortlist_cutoff)
);

-- How each HR user wants to hear about new notifications by email.
CREATE TABLE hr_user_preferences (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    hr_user_id       uuid NOT NULL UNIQUE REFERENCES hr_users(id) ON DELETE CASCADE,
    email_alerts     text NOT NULL DEFAULT 'instant' CHECK (email_alerts IN ('instant', 'daily', 'off')),
    last_digest_at   timestamptz,
    created_by       uuid,
    created_at       timestamptz NOT NULL DEFAULT now(),
    last_modified_by uuid,
    last_modified_at timestamptz NOT NULL DEFAULT now(),
    owner_id         uuid,
    assigned_id      uuid
);

-- Skills and their other names, added by a client on top of the built-in dictionary.
CREATE TABLE skill_synonyms (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id       uuid NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    skill            text NOT NULL CHECK (length(skill) BETWEEN 1 AND 80),
    synonyms         text[] NOT NULL,
    created_by       uuid,
    created_at       timestamptz NOT NULL DEFAULT now(),
    last_modified_by uuid,
    last_modified_at timestamptz NOT NULL DEFAULT now(),
    owner_id         uuid,
    assigned_id      uuid
);
CREATE UNIQUE INDEX ux_skill_synonyms_company_skill ON skill_synonyms (company_id, lower(skill));

-- ---------------------------------------------------------------------
-- Jobs: screening limits and criteria history
-- ---------------------------------------------------------------------

ALTER TABLE jobs
    ADD COLUMN criteria_version   integer NOT NULL DEFAULT 1,
    ADD COLUMN max_notice_days    integer CHECK (max_notice_days >= 0),
    ADD COLUMN max_ctc_lpa        numeric(6, 2) CHECK (max_ctc_lpa >= 0),
    ADD COLUMN accepted_locations text[];

-- Criteria are never deleted once used, so past scores keep their evidence.
ALTER TABLE job_criteria
    ADD COLUMN version    integer NOT NULL DEFAULT 1,
    ADD COLUMN retired_at timestamptz,
    ADD COLUMN min_years  integer CHECK (min_years BETWEEN 0 AND 40);
CREATE INDEX ix_job_criteria_job_active ON job_criteria (job_id) WHERE retired_at IS NULL;

-- ---------------------------------------------------------------------
-- Candidate intake
-- ---------------------------------------------------------------------

ALTER TABLE applications
    ADD COLUMN source_detail     text,
    ADD COLUMN status_reason     text,
    ADD COLUMN status_changed_at timestamptz,
    ADD COLUMN cv_text_hash      text;            -- fingerprint of the CV text, to spot copied CVs
CREATE INDEX ix_applications_cv_text_hash ON applications (cv_text_hash) WHERE cv_text_hash IS NOT NULL;

-- Every CV handed to the portal, from upload until it is added or refused.
CREATE TABLE cv_uploads (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id             uuid NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    batch_id           uuid NOT NULL,
    file_name          text NOT NULL,
    file_key           text,
    text_key           text,
    content_type       text,
    status             text NOT NULL DEFAULT 'queued'
                       CHECK (status IN ('queued', 'reading', 'needs_details', 'added', 'duplicate', 'failed')),
    error_code         text,
    error_message      text,
    detected_name      text,
    detected_email     text,
    detected_mobile    text,
    detected_city      text,
    detected_country   text,
    source             application_source_t,
    source_detail      text,
    application_id     uuid REFERENCES applications(id) ON DELETE SET NULL,
    uploaded_by        uuid REFERENCES hr_users(id),
    attempts           integer NOT NULL DEFAULT 0,
    queued_at          timestamptz NOT NULL DEFAULT now(),
    started_at         timestamptz,
    finished_at        timestamptz,
    overdue_alerted_at timestamptz,
    created_by         uuid,
    created_at         timestamptz NOT NULL DEFAULT now(),
    last_modified_by   uuid,
    last_modified_at   timestamptz NOT NULL DEFAULT now(),
    owner_id           uuid,
    assigned_id        uuid
);
CREATE INDEX ix_cv_uploads_status_queued ON cv_uploads (status, queued_at);
CREATE INDEX ix_cv_uploads_job_batch ON cv_uploads (job_id, batch_id);

-- Facts recruiters in India ask first. Kept per application, so each client sees only its own.
CREATE TABLE application_key_facts (
    id                        uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id            uuid NOT NULL UNIQUE REFERENCES applications(id) ON DELETE CASCADE,
    notice_period_days        integer CHECK (notice_period_days BETWEEN 0 AND 365),
    serving_notice            boolean,
    last_working_day          date,
    current_ctc_lpa           numeric(6, 2) CHECK (current_ctc_lpa >= 0),
    expected_ctc_lpa          numeric(6, 2) CHECK (expected_ctc_lpa >= 0),
    current_location          text,
    preferred_locations       text[],
    total_experience_years    numeric(4, 1) CHECK (total_experience_years >= 0),
    relevant_experience_years numeric(4, 1) CHECK (relevant_experience_years >= 0),
    willing_to_relocate       boolean,
    work_mode                 text CHECK (work_mode IN ('office', 'hybrid', 'remote', 'any')),
    created_by                uuid,
    created_at                timestamptz NOT NULL DEFAULT now(),
    last_modified_by          uuid,
    last_modified_at          timestamptz NOT NULL DEFAULT now(),
    owner_id                  uuid,
    assigned_id               uuid
);

-- Private HR notes about a candidate in one job.
CREATE TABLE application_notes (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id   uuid NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    author_id        uuid NOT NULL REFERENCES hr_users(id),
    body             text NOT NULL CHECK (length(body) BETWEEN 1 AND 4000),
    created_by       uuid,
    created_at       timestamptz NOT NULL DEFAULT now(),
    last_modified_by uuid,
    last_modified_at timestamptz NOT NULL DEFAULT now(),
    owner_id         uuid,
    assigned_id      uuid
);
CREATE INDEX ix_application_notes_app ON application_notes (application_id, created_at DESC);

-- ---------------------------------------------------------------------
-- Scoring 2.0
-- ---------------------------------------------------------------------

ALTER TABLE ai_screenings
    ADD COLUMN criteria_version integer,
    ADD COLUMN total_years      numeric(4, 1),
    ADD COLUMN relevant_years   numeric(4, 1),
    ADD COLUMN summary          text,
    ADD COLUMN reading_quality  text CHECK (reading_quality IN ('good', 'check')),
    ADD COLUMN shortlist_cutoff integer,
    ADD COLUMN reserve_cutoff   integer,
    ADD COLUMN reason           text;

ALTER TABLE match_evidence
    ADD COLUMN strength     text CHECK (strength IN ('recent_role', 'older_role', 'skills_only', 'mention_only',
                                                      'negated', 'not_found')),
    ADD COLUMN points       numeric(5, 2),
    ADD COLUMN cv_section   text,
    ADD COLUMN matched_term text;

-- Things HR should look at in a CV. Flags never change the score.
CREATE TABLE screening_flags (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    screening_id     uuid NOT NULL REFERENCES ai_screenings(id) ON DELETE CASCADE,
    application_id   uuid NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    severity         text NOT NULL CHECK (severity IN ('red', 'check')),
    kind             text NOT NULL,
    message          text NOT NULL,
    cv_quote         text,
    review_outcome   text CHECK (review_outcome IN ('fine', 'concern')),
    review_note      text,
    reviewed_by      uuid REFERENCES hr_users(id),
    reviewed_at      timestamptz,
    created_by       uuid,
    created_at       timestamptz NOT NULL DEFAULT now(),
    last_modified_by uuid,
    last_modified_at timestamptz NOT NULL DEFAULT now(),
    owner_id         uuid,
    assigned_id      uuid
);
CREATE INDEX ix_screening_flags_app ON screening_flags (application_id, screening_id);

-- Why HR went against the AI's suggestion.
ALTER TABLE shortlist_decisions ADD COLUMN reason text;

-- ---------------------------------------------------------------------
-- Interviews and reports
-- ---------------------------------------------------------------------

ALTER TABLE interviews
    ADD COLUMN assessor_id  uuid REFERENCES partner_assessors(id),
    ADD COLUMN completed_at timestamptz;
CREATE INDEX ix_interviews_application_round ON interviews (application_id, round);

ALTER TABLE reschedule_requests
    ADD COLUMN source      text NOT NULL DEFAULT 'hr_logged' CHECK (source IN ('hr_logged', 'candidate_portal')),
    ADD COLUMN new_slot_id uuid REFERENCES centre_slots(id);

ALTER TABLE round1_reports
    ADD COLUMN identity_check       text CHECK (identity_check IN ('verified', 'mismatch', 'not_done')),
    ADD COLUMN identity_note        text,
    ADD COLUMN surroundings_check   text CHECK (surroundings_check IN ('clear', 'concern', 'not_done')),
    ADD COLUMN surroundings_note    text,
    ADD COLUMN highlights           text[],
    ADD COLUMN is_sample            boolean NOT NULL DEFAULT false,
    ADD COLUMN recheck_requested_at timestamptz,
    ADD COLUMN recheck_note         text;

ALTER TABLE round2_assessments
    ADD COLUMN id_checked    boolean,
    ADD COLUMN id_check_note text,
    ADD COLUMN area_scores   jsonb,
    ADD COLUMN notes         text,
    ADD COLUMN submitted_at  timestamptz,
    ADD COLUMN is_sample     boolean NOT NULL DEFAULT false;
CREATE UNIQUE INDEX ux_round2_assessments_interview ON round2_assessments (interview_id);

ALTER TABLE round3_feedback
    ADD COLUMN strengths text,
    ADD COLUMN concerns  text,
    ADD COLUMN is_sample boolean NOT NULL DEFAULT false;
CREATE UNIQUE INDEX ux_round3_feedback_interviewer ON round3_feedback (interview_id, interviewer_id);

-- One-time links partner assessors use to send their Round 2 report (no login).
CREATE TABLE assessor_links (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    interview_id     uuid NOT NULL REFERENCES interviews(id) ON DELETE CASCADE,
    token_hash       text NOT NULL UNIQUE,
    sent_to          text,
    expires_at       timestamptz NOT NULL,
    used_at          timestamptz,
    created_by       uuid,
    created_at       timestamptz NOT NULL DEFAULT now(),
    last_modified_by uuid,
    last_modified_at timestamptz NOT NULL DEFAULT now(),
    owner_id         uuid,
    assigned_id      uuid
);

-- HR's review of a report: asking ETT to re-check, or a note alongside the decision.
CREATE TABLE report_reviews (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id   uuid NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    interview_id     uuid REFERENCES interviews(id) ON DELETE CASCADE,
    report_type      text NOT NULL CHECK (report_type IN ('shortlisting', 'round1', 'round2', 'round3')),
    outcome          text NOT NULL CHECK (outcome IN ('agree', 'override', 'recheck')),
    note             text,
    reviewed_by      uuid REFERENCES hr_users(id),
    reviewed_at      timestamptz NOT NULL DEFAULT now(),
    created_by       uuid,
    created_at       timestamptz NOT NULL DEFAULT now(),
    last_modified_by uuid,
    last_modified_at timestamptz NOT NULL DEFAULT now(),
    owner_id         uuid,
    assigned_id      uuid
);
CREATE INDEX ix_report_reviews_app ON report_reviews (application_id, report_type);

-- ---------------------------------------------------------------------
-- Notifications for HR users
-- ---------------------------------------------------------------------

CREATE TABLE notifications (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id     uuid NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    recipient_id   uuid NOT NULL REFERENCES hr_users(id) ON DELETE CASCADE,
    kind           text NOT NULL,
    title          text NOT NULL,
    body           text,
    link           text,
    job_id         uuid REFERENCES jobs(id) ON DELETE CASCADE,
    application_id uuid REFERENCES applications(id) ON DELETE CASCADE,
    interview_id   uuid REFERENCES interviews(id) ON DELETE CASCADE,
    read_at        timestamptz,
    emailed_at     timestamptz,
    created_at     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_notifications_recipient ON notifications (recipient_id, created_at DESC);
CREATE INDEX ix_notifications_unread ON notifications (recipient_id) WHERE read_at IS NULL;

-- ---------------------------------------------------------------------
-- Search
-- ---------------------------------------------------------------------

CREATE INDEX ix_candidates_name_lower ON candidates (lower(full_name));
CREATE INDEX ix_jobs_company_status ON jobs (company_id, status);

-- ---------------------------------------------------------------------
-- Triggers: same as V1 tables. company_logos skips the audit trail so
-- image bytes are not copied into it; uploads are logged in audit_log.
-- ---------------------------------------------------------------------

DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'company_logos',
        'company_settings',
        'hr_user_preferences',
        'skill_synonyms',
        'cv_uploads',
        'application_key_facts',
        'application_notes',
        'screening_flags',
        'assessor_links',
        'report_reviews'
    ] LOOP
        EXECUTE format(
            'CREATE TRIGGER trg_%s_last_modified BEFORE UPDATE ON %I
             FOR EACH ROW EXECUTE FUNCTION set_last_modified_at()', t, t);
    END LOOP;

    FOREACH t IN ARRAY ARRAY[
        'company_settings',
        'hr_user_preferences',
        'skill_synonyms',
        'cv_uploads',
        'application_key_facts',
        'application_notes',
        'screening_flags',
        'assessor_links',
        'report_reviews'
    ] LOOP
        EXECUTE format(
            'CREATE TRIGGER trg_%s_audit AFTER INSERT OR UPDATE OR DELETE ON %I
             FOR EACH ROW EXECUTE FUNCTION fn_audit_trail()', t, t);
    END LOOP;
END $$;
