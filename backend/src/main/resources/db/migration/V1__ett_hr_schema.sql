-- Provided ETT HR schema (ett_hr_schema.sql), unchanged except that the
-- BEGIN/COMMIT wrapper is removed: Flyway runs each migration in its own transaction.

-- =====================================================================
-- ETT HR portal - PostgreSQL schema
-- Generated from ETT_HR_Data_Dictionary_By_Table.xlsx (25 tables)
-- plus audit_trail and 6 log tables (section 8) = 32 tables
-- Requires PostgreSQL 13+ (gen_random_uuid() is built in).
--
-- NOTE: The data dictionary lists "enum" columns but NOT their values.
-- The values below are INFERRED - review/edit section 1 before running.
-- Every table also carries: created_by, created_at, last_modified_by,
-- last_modified_at, owner_id, assigned_id (see section 7 for the trigger).
-- Sprint 1 tables: 1-19.  "Next sprint" tables: 20-25 (section 3).
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. ENUM TYPES (values are assumptions - please review)
-- ---------------------------------------------------------------------
CREATE TYPE data_region_t          AS ENUM ('eu', 'uk', 'us', 'apac', 'other');
CREATE TYPE company_status_t       AS ENUM ('active', 'suspended', 'inactive');
CREATE TYPE hr_user_role_t         AS ENUM ('admin', 'recruiter', 'hiring_manager', 'interviewer');
CREATE TYPE hr_user_status_t       AS ENUM ('invited', 'active', 'disabled');
CREATE TYPE calendar_provider_t    AS ENUM ('google', 'microsoft');
CREATE TYPE calendar_status_t      AS ENUM ('connected', 'expired', 'revoked');
CREATE TYPE contract_type_t        AS ENUM ('permanent', 'fixed_term', 'contract', 'part_time', 'internship');
CREATE TYPE job_status_t           AS ENUM ('draft', 'open', 'shortlist_confirmed', 'closed', 'archived');
CREATE TYPE interview_mode_t       AS ENUM ('online', 'phone', 'in_person');
CREATE TYPE criterion_kind_t       AS ENUM ('must_have', 'nice_to_have', 'certification');
CREATE TYPE application_source_t   AS ENUM ('hr_upload', 'email', 'portal', 'referral');
CREATE TYPE cv_status_t            AS ENUM ('uploaded', 'parsed', 'failed', 'deleted');
CREATE TYPE application_stage_t    AS ENUM ('screening', 'shortlist', 'round1', 'round2', 'round3', 'offer', 'closed');
CREATE TYPE application_status_t   AS ENUM ('active', 'rejected', 'withdrawn', 'hired');
CREATE TYPE ai_suggestion_t        AS ENUM ('shortlist', 'review', 'reject');
CREATE TYPE shortlist_decision_t   AS ENUM ('shortlisted', 'rejected', 'hold');
CREATE TYPE time_source_t          AS ENUM ('hr_set', 'candidate_picked', 'centre_slot');
CREATE TYPE interview_status_t     AS ENUM ('invited', 'confirmed', 'reschedule_requested', 'completed', 'cancelled', 'no_show');
CREATE TYPE reschedule_reason_t    AS ENUM ('clash', 'illness', 'travel', 'other');
CREATE TYPE time_of_day_t          AS ENUM ('morning', 'afternoon', 'evening', 'any');
CREATE TYPE reschedule_status_t    AS ENUM ('pending', 'approved', 'declined');
CREATE TYPE message_channel_t      AS ENUM ('email');                       -- Sprint 1: email only
CREATE TYPE sender_kind_t          AS ENUM ('hr_user', 'candidate', 'ett_staff', 'system');
CREATE TYPE message_direction_t    AS ENUM ('inbound', 'outbound');
CREATE TYPE recommendation_t       AS ENUM ('advance', 'hold', 'reject');
CREATE TYPE integrity_status_t     AS ENUM ('clear', 'flagged', 'under_review');
CREATE TYPE report_status_t        AS ENUM ('draft', 'in_review', 'released');
CREATE TYPE stage_decision_t       AS ENUM ('advance', 'hold', 'reject');
CREATE TYPE ett_staff_role_t       AS ENUM ('admin', 'reviewer');
CREATE TYPE ett_staff_status_t     AS ENUM ('active', 'inactive');
CREATE TYPE actor_kind_t           AS ENUM ('hr_user', 'ett_staff', 'candidate', 'system');
CREATE TYPE assessor_status_t      AS ENUM ('active', 'inactive');
CREATE TYPE panel_role_t           AS ENUM ('lead', 'panelist', 'observer');
CREATE TYPE panel_response_t       AS ENUM ('pending', 'accepted', 'declined');

-- ---------------------------------------------------------------------
-- 2. SPRINT 1 TABLES
-- ---------------------------------------------------------------------

-- 1. companies - Client company
CREATE TABLE companies (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name         text NOT NULL,
    country      text,
    data_region  data_region_t,
    status       company_status_t NOT NULL DEFAULT 'active',
    created_at   timestamptz NOT NULL DEFAULT now(),
    -- audit / ownership columns
    created_by       uuid,
    last_modified_by uuid,
    last_modified_at timestamptz NOT NULL DEFAULT now(),
    owner_id         uuid,
    assigned_id      uuid
);

-- 2. hr_users - HR user
CREATE TABLE hr_users (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id       uuid NOT NULL REFERENCES companies(id),
    full_name        text NOT NULL,
    work_email       text NOT NULL UNIQUE,
    password_hash    text,
    totp_secret      text,
    role             hr_user_role_t,
    status           hr_user_status_t NOT NULL DEFAULT 'invited',
    last_sign_in_at  timestamptz,
    -- audit / ownership columns
    created_by       uuid,
    created_at       timestamptz NOT NULL DEFAULT now(),
    last_modified_by uuid,
    last_modified_at timestamptz NOT NULL DEFAULT now(),
    owner_id         uuid,
    assigned_id      uuid
);

-- 3. calendar_connections - Calendar connection
CREATE TABLE calendar_connections (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    hr_user_id    uuid NOT NULL REFERENCES hr_users(id),
    provider      calendar_provider_t NOT NULL,
    status        calendar_status_t NOT NULL DEFAULT 'connected',
    token_ref     text,
    connected_at  timestamptz,
    -- audit / ownership columns
    created_by       uuid,
    created_at       timestamptz NOT NULL DEFAULT now(),
    last_modified_by uuid,
    last_modified_at timestamptz NOT NULL DEFAULT now(),
    owner_id         uuid,
    assigned_id      uuid
);

-- 4. jobs - Job
CREATE TABLE jobs (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    job_ref                 text NOT NULL UNIQUE,
    company_id              uuid NOT NULL REFERENCES companies(id),
    created_by              uuid REFERENCES hr_users(id),
    title                   text NOT NULL,
    location                text,
    contract_type           contract_type_t,
    openings                integer CHECK (openings > 0),
    min_experience_yrs      integer CHECK (min_experience_yrs >= 0),
    status                  job_status_t NOT NULL DEFAULT 'draft',
    published_at            timestamptz,
    shortlist_confirmed_at  timestamptz,
    -- audit / ownership columns
    created_at       timestamptz NOT NULL DEFAULT now(),
    last_modified_by uuid,
    last_modified_at timestamptz NOT NULL DEFAULT now(),
    owner_id         uuid,
    assigned_id      uuid
);

-- 5. job_rounds - Job interview plan
CREATE TABLE job_rounds (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id        uuid NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    round         integer NOT NULL CHECK (round > 0),
    mode          interview_mode_t,
    duration_min  integer CHECK (duration_min > 0),
    required      boolean NOT NULL DEFAULT true,
    -- audit / ownership columns
    created_by       uuid,
    created_at       timestamptz NOT NULL DEFAULT now(),
    last_modified_by uuid,
    last_modified_at timestamptz NOT NULL DEFAULT now(),
    owner_id         uuid,
    assigned_id      uuid,
    UNIQUE (job_id, round)
);

-- 6. job_criteria - Shortlisting criterion
CREATE TABLE job_criteria (
    id      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id  uuid NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    kind    criterion_kind_t NOT NULL,
    value   text NOT NULL,
    -- audit / ownership columns
    created_by       uuid,
    created_at       timestamptz NOT NULL DEFAULT now(),
    last_modified_by uuid,
    last_modified_at timestamptz NOT NULL DEFAULT now(),
    owner_id         uuid,
    assigned_id      uuid
);

-- 7. candidates - Candidate (from the CV)
CREATE TABLE candidates (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    candidate_ref  text NOT NULL UNIQUE,
    full_name      text NOT NULL,
    email          text NOT NULL UNIQUE,
    mobile         text,
    city           text,
    country        text,
    created_at     timestamptz NOT NULL DEFAULT now(),
    -- audit / ownership columns
    created_by       uuid,
    last_modified_by uuid,
    last_modified_at timestamptz NOT NULL DEFAULT now(),
    owner_id         uuid,
    assigned_id      uuid
);

-- 8. applications - Application (candidate in a job)
CREATE TABLE applications (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id          uuid NOT NULL REFERENCES jobs(id),
    candidate_id    uuid NOT NULL REFERENCES candidates(id),
    source          application_source_t,
    cv_file_key     text,
    cv_text_key     text,
    cv_status       cv_status_t,
    cv_delete_after date,
    cv_uploaded_by  uuid REFERENCES hr_users(id),
    stage           application_stage_t NOT NULL DEFAULT 'screening',
    status          application_status_t NOT NULL DEFAULT 'active',
    created_at      timestamptz NOT NULL DEFAULT now(),
    -- audit / ownership columns
    created_by       uuid,
    last_modified_by uuid,
    last_modified_at timestamptz NOT NULL DEFAULT now(),
    owner_id         uuid,
    assigned_id      uuid,
    UNIQUE (job_id, candidate_id)   -- assumption: one application per candidate per job
);

-- 18. ett_staff - ETT staff (created early: referenced by round1_reports)
CREATE TABLE ett_staff (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name  text NOT NULL,
    email      text NOT NULL UNIQUE,
    role       ett_staff_role_t,
    status     ett_staff_status_t NOT NULL DEFAULT 'active',
    -- audit / ownership columns
    created_by       uuid,
    created_at       timestamptz NOT NULL DEFAULT now(),
    last_modified_by uuid,
    last_modified_at timestamptz NOT NULL DEFAULT now(),
    owner_id         uuid,
    assigned_id      uuid
);

-- 9. ai_screenings - AI screening result
CREATE TABLE ai_screenings (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id       uuid NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    model_version        text,
    match_score          integer CHECK (match_score BETWEEN 0 AND 100),
    must_have_pts        integer,
    nice_to_have_pts     integer,
    experience_pts       integer,
    certification_pts    integer,
    experience_years     integer,
    certifications_found text,
    suggestion           ai_suggestion_t,
    created_at           timestamptz NOT NULL DEFAULT now(),
    -- audit / ownership columns
    created_by       uuid,
    last_modified_by uuid,
    last_modified_at timestamptz NOT NULL DEFAULT now(),
    owner_id         uuid,
    assigned_id      uuid
);

-- 10. match_evidence - Match evidence
CREATE TABLE match_evidence (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    screening_id  uuid NOT NULL REFERENCES ai_screenings(id) ON DELETE CASCADE,
    criterion_id  uuid NOT NULL REFERENCES job_criteria(id),
    matched       boolean NOT NULL,
    cv_quote      text,
    flagged_by    uuid REFERENCES hr_users(id),
    flag_note     text,
    -- audit / ownership columns
    created_by       uuid,
    created_at       timestamptz NOT NULL DEFAULT now(),
    last_modified_by uuid,
    last_modified_at timestamptz NOT NULL DEFAULT now(),
    owner_id         uuid,
    assigned_id      uuid
);

-- 11. shortlist_decisions - Shortlist decision
CREATE TABLE shortlist_decisions (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id  uuid NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    ai_suggestion   ai_suggestion_t,
    decision        shortlist_decision_t NOT NULL,
    decided_by      uuid REFERENCES hr_users(id),
    decided_at      timestamptz,
    -- audit / ownership columns
    created_by       uuid,
    created_at       timestamptz NOT NULL DEFAULT now(),
    last_modified_by uuid,
    last_modified_at timestamptz NOT NULL DEFAULT now(),
    owner_id         uuid,
    assigned_id      uuid
);

-- ---------------------------------------------------------------------
-- 3. NEXT-SPRINT TABLES that interviews depends on
-- ---------------------------------------------------------------------

-- 20. partner_centres - Partner centre
CREATE TABLE partner_centres (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name              text NOT NULL,
    address           text,
    city              text,
    step_free_access  boolean,
    partner_org       text,
    -- audit / ownership columns
    created_by       uuid,
    created_at       timestamptz NOT NULL DEFAULT now(),
    last_modified_by uuid,
    last_modified_at timestamptz NOT NULL DEFAULT now(),
    owner_id         uuid,
    assigned_id      uuid
);

-- 21. centre_slots - Centre slot (Round 2)
CREATE TABLE centre_slots (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    centre_id  uuid NOT NULL REFERENCES partner_centres(id),
    start_at   timestamptz NOT NULL,
    end_at     timestamptz NOT NULL,
    seats      integer NOT NULL CHECK (seats >= 0),
    booked     integer NOT NULL DEFAULT 0 CHECK (booked >= 0),
    -- audit / ownership columns
    created_by       uuid,
    created_at       timestamptz NOT NULL DEFAULT now(),
    last_modified_by uuid,
    last_modified_at timestamptz NOT NULL DEFAULT now(),
    owner_id         uuid,
    assigned_id      uuid,
    CHECK (end_at > start_at),
    CHECK (booked <= seats)
);

-- 22. partner_assessors - Partner assessor
CREATE TABLE partner_assessors (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    centre_id  uuid NOT NULL REFERENCES partner_centres(id),
    full_name  text NOT NULL,
    email      text,
    status     assessor_status_t NOT NULL DEFAULT 'active',
    -- audit / ownership columns
    created_by       uuid,
    created_at       timestamptz NOT NULL DEFAULT now(),
    last_modified_by uuid,
    last_modified_at timestamptz NOT NULL DEFAULT now(),
    owner_id         uuid,
    assigned_id      uuid
);

-- ---------------------------------------------------------------------
-- 4. REMAINING SPRINT 1 TABLES
-- ---------------------------------------------------------------------

-- 12. interviews - Interview
CREATE TABLE interviews (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id    uuid NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    round             integer NOT NULL CHECK (round > 0),
    mode              interview_mode_t,
    start_at          timestamptz,
    candidate_tz      text,                                   -- IANA tz name, e.g. 'Asia/Kolkata'
    duration_min      integer CHECK (duration_min > 0),
    time_source       time_source_t,
    slot_id           uuid REFERENCES centre_slots(id),       -- optional; next sprint
    arrive_by         timestamptz,                            -- next sprint
    meeting_link      text,                                   -- next sprint
    reply_by          timestamptz,
    invited_at        timestamptz,
    status            interview_status_t NOT NULL DEFAULT 'invited',
    scheduled_by      uuid REFERENCES hr_users(id),
    reminder_sent_at  timestamptz,
    -- audit / ownership columns
    created_by       uuid,
    created_at       timestamptz NOT NULL DEFAULT now(),
    last_modified_by uuid,
    last_modified_at timestamptz NOT NULL DEFAULT now(),
    owner_id         uuid,
    assigned_id      uuid
);

-- 13. reschedule_requests - Reschedule request
CREATE TABLE reschedule_requests (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    interview_id    uuid NOT NULL REFERENCES interviews(id) ON DELETE CASCADE,
    reason          reschedule_reason_t,
    note            text,
    available_days  date[],
    time_of_day     time_of_day_t,
    status          reschedule_status_t NOT NULL DEFAULT 'pending',
    new_start_at    timestamptz,
    resolved_by     uuid REFERENCES hr_users(id),
    created_at      timestamptz NOT NULL DEFAULT now(),
    -- audit / ownership columns
    created_by       uuid,
    last_modified_by uuid,
    last_modified_at timestamptz NOT NULL DEFAULT now(),
    owner_id         uuid,
    assigned_id      uuid
);

-- 14. messages - Message (in-app email)
CREATE TABLE messages (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id  uuid NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    interview_id    uuid REFERENCES interviews(id),           -- optional
    channel         message_channel_t NOT NULL DEFAULT 'email',
    sender_kind     sender_kind_t NOT NULL,
    sender_id       uuid,                                     -- polymorphic (see sender_kind), no FK
    to_hr_user_id   uuid REFERENCES hr_users(id),
    direction       message_direction_t NOT NULL,
    subject         text,
    body            text,
    sent_at         timestamptz,
    read_at         timestamptz,
    -- audit / ownership columns
    created_by       uuid,
    created_at       timestamptz NOT NULL DEFAULT now(),
    last_modified_by uuid,
    last_modified_at timestamptz NOT NULL DEFAULT now(),
    owner_id         uuid,
    assigned_id      uuid
);

-- 15. round1_reports - Round 1 report
CREATE TABLE round1_reports (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    interview_id     uuid NOT NULL UNIQUE REFERENCES interviews(id) ON DELETE CASCADE,  -- 0 or 1 per interview
    reviewer_id      uuid REFERENCES ett_staff(id),
    ai_overall       integer CHECK (ai_overall BETWEEN 0 AND 100),
    final_overall    integer CHECK (final_overall BETWEEN 0 AND 100),
    recommendation   recommendation_t,
    task_summary     text,
    integrity_status integrity_status_t,
    reviewer_notes   text,
    status           report_status_t NOT NULL DEFAULT 'draft',
    released_at      timestamptz,
    -- audit / ownership columns
    created_by       uuid,
    created_at       timestamptz NOT NULL DEFAULT now(),
    last_modified_by uuid,
    last_modified_at timestamptz NOT NULL DEFAULT now(),
    owner_id         uuid,
    assigned_id      uuid
);

-- 16. competency_scores - Competency score
CREATE TABLE competency_scores (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id         uuid NOT NULL REFERENCES round1_reports(id) ON DELETE CASCADE,
    competency        text NOT NULL,
    ai_draft_score    integer,
    final_score       integer,
    reviewer_comment  text,
    -- audit / ownership columns
    created_by       uuid,
    created_at       timestamptz NOT NULL DEFAULT now(),
    last_modified_by uuid,
    last_modified_at timestamptz NOT NULL DEFAULT now(),
    owner_id         uuid,
    assigned_id      uuid,
    UNIQUE (report_id, competency)
);

-- 17. stage_decisions - Stage decision
CREATE TABLE stage_decisions (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id  uuid NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    stage           application_stage_t NOT NULL,
    decision        stage_decision_t NOT NULL,
    reason          text,
    decided_by      uuid REFERENCES hr_users(id),
    decided_at      timestamptz,
    challenged_at   timestamptz,
    -- audit / ownership columns
    created_by       uuid,
    created_at       timestamptz NOT NULL DEFAULT now(),
    last_modified_by uuid,
    last_modified_at timestamptz NOT NULL DEFAULT now(),
    owner_id         uuid,
    assigned_id      uuid
);

-- 19. audit_log - Audit log
CREATE TABLE audit_log (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_kind   actor_kind_t NOT NULL,
    actor_id     uuid,                                        -- polymorphic (see actor_kind), no FK
    action       text NOT NULL,
    record_type  text,
    record_id    uuid,
    at           timestamptz NOT NULL DEFAULT now(),
    -- audit / ownership columns
    created_by       uuid,
    created_at       timestamptz NOT NULL DEFAULT now(),
    last_modified_by uuid,
    last_modified_at timestamptz NOT NULL DEFAULT now(),
    owner_id         uuid,
    assigned_id      uuid
);

-- ---------------------------------------------------------------------
-- 5. REMAINING NEXT-SPRINT TABLES
-- ---------------------------------------------------------------------

-- 23. round2_assessments - Round 2 assessment
CREATE TABLE round2_assessments (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    interview_id    uuid NOT NULL REFERENCES interviews(id) ON DELETE CASCADE,
    assessor_id     uuid REFERENCES partner_assessors(id),
    score           integer,
    recommendation  recommendation_t,
    report_key      text,
    -- audit / ownership columns
    created_by       uuid,
    created_at       timestamptz NOT NULL DEFAULT now(),
    last_modified_by uuid,
    last_modified_at timestamptz NOT NULL DEFAULT now(),
    owner_id         uuid,
    assigned_id      uuid
);

-- 24. interview_panel - Round 3 panel
CREATE TABLE interview_panel (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    interview_id  uuid NOT NULL REFERENCES interviews(id) ON DELETE CASCADE,
    hr_user_id    uuid NOT NULL REFERENCES hr_users(id),
    role          panel_role_t,
    response      panel_response_t NOT NULL DEFAULT 'pending',
    -- audit / ownership columns
    created_by       uuid,
    created_at       timestamptz NOT NULL DEFAULT now(),
    last_modified_by uuid,
    last_modified_at timestamptz NOT NULL DEFAULT now(),
    owner_id         uuid,
    assigned_id      uuid,
    UNIQUE (interview_id, hr_user_id)
);

-- 25. round3_feedback - Round 3 feedback
CREATE TABLE round3_feedback (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    interview_id    uuid NOT NULL REFERENCES interviews(id) ON DELETE CASCADE,
    interviewer_id  uuid NOT NULL REFERENCES hr_users(id),
    rating          integer,
    recommendation  recommendation_t,
    notes           text,
    submitted_at    timestamptz,
    -- audit / ownership columns
    created_by       uuid,
    created_at       timestamptz NOT NULL DEFAULT now(),
    last_modified_by uuid,
    last_modified_at timestamptz NOT NULL DEFAULT now(),
    owner_id         uuid,
    assigned_id      uuid
);

-- ---------------------------------------------------------------------
-- 6. INDEXES on foreign keys / common lookups
-- ---------------------------------------------------------------------
CREATE INDEX ON hr_users (company_id);
CREATE INDEX ON calendar_connections (hr_user_id);
CREATE INDEX ON jobs (company_id);
CREATE INDEX ON jobs (created_by);
CREATE INDEX ON job_criteria (job_id);
CREATE INDEX ON applications (job_id);
CREATE INDEX ON applications (candidate_id);
CREATE INDEX ON applications (cv_uploaded_by);
CREATE INDEX ON ai_screenings (application_id);
CREATE INDEX ON match_evidence (screening_id);
CREATE INDEX ON match_evidence (criterion_id);
CREATE INDEX ON shortlist_decisions (application_id);
CREATE INDEX ON centre_slots (centre_id);
CREATE INDEX ON partner_assessors (centre_id);
CREATE INDEX ON interviews (application_id);
CREATE INDEX ON interviews (slot_id);
CREATE INDEX ON interviews (start_at);
CREATE INDEX ON reschedule_requests (interview_id);
CREATE INDEX ON messages (application_id);
CREATE INDEX ON messages (interview_id);
CREATE INDEX ON messages (to_hr_user_id);
CREATE INDEX ON round1_reports (reviewer_id);
CREATE INDEX ON competency_scores (report_id);
CREATE INDEX ON stage_decisions (application_id);
CREATE INDEX ON audit_log (actor_kind, actor_id);
CREATE INDEX ON audit_log (record_type, record_id);
CREATE INDEX ON audit_log (at);
CREATE INDEX ON round2_assessments (interview_id);
CREATE INDEX ON round2_assessments (assessor_id);
CREATE INDEX ON round3_feedback (interview_id);

-- ---------------------------------------------------------------------
-- 7. AUTO-MAINTAIN last_modified_at
--    last_modified_by is set by the application on each UPDATE.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION set_last_modified_at() RETURNS trigger AS $$
BEGIN
    NEW.last_modified_at := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'companies',
        'hr_users',
        'calendar_connections',
        'jobs',
        'job_rounds',
        'job_criteria',
        'candidates',
        'applications',
        'ett_staff',
        'ai_screenings',
        'match_evidence',
        'shortlist_decisions',
        'partner_centres',
        'centre_slots',
        'partner_assessors',
        'interviews',
        'reschedule_requests',
        'messages',
        'round1_reports',
        'competency_scores',
        'stage_decisions',
        'audit_log',
        'round2_assessments',
        'interview_panel',
        'round3_feedback'
    ] LOOP
        EXECUTE format(
            'CREATE TRIGGER trg_%s_last_modified BEFORE UPDATE ON %I
             FOR EACH ROW EXECUTE FUNCTION set_last_modified_at()', t, t);
    END LOOP;
END $$;

-- ---------------------------------------------------------------------
-- 8. AUDIT TRAIL & LOG TABLES (append-only event records)
--    audit_trail  : automatic row-level change history (who/what/when,
--                   old + new values) fed by a trigger on every core table
--    login_log, email_log, ai_processing_log, calendar_sync_log,
--    document_access_log, error_log : operational / compliance logs
--    These use bigint identity keys (high volume, ordered by insert) and
--    only an event timestamp - the six ownership columns don't apply to
--    immutable event rows.
--    Existing audit_log (business-action log) is kept unchanged.
-- ---------------------------------------------------------------------

CREATE TYPE login_event_t      AS ENUM ('sign_in', 'sign_out', 'failed_password', 'failed_totp', 'locked', 'password_reset');
CREATE TYPE email_event_t      AS ENUM ('queued', 'sent', 'delivered', 'opened', 'bounced', 'failed');
CREATE TYPE ai_task_t          AS ENUM ('cv_parse', 'cv_screening', 'round1_scoring', 'report_draft');
CREATE TYPE process_status_t   AS ENUM ('started', 'succeeded', 'failed');
CREATE TYPE calendar_action_t  AS ENUM ('create_event', 'update_event', 'delete_event', 'refresh_token', 'free_busy');
CREATE TYPE doc_action_t       AS ENUM ('view', 'download', 'delete', 'purge');
CREATE TYPE log_level_t        AS ENUM ('warning', 'error', 'critical');

-- 26. audit_trail - field-level change history (filled by trigger)
CREATE TABLE audit_trail (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    table_name      text NOT NULL,
    record_id       uuid,
    operation       text NOT NULL CHECK (operation IN ('INSERT', 'UPDATE', 'DELETE')),
    changed_fields  text[],                      -- UPDATE only
    old_data        jsonb,                       -- UPDATE / DELETE
    new_data        jsonb,                       -- INSERT / UPDATE
    changed_by      uuid,                        -- from SET app.current_user_id
    actor_kind      actor_kind_t,                -- from SET app.current_actor_kind
    transaction_id  bigint NOT NULL DEFAULT txid_current(),
    changed_at      timestamptz NOT NULL DEFAULT now()
);

-- 27. login_log - sign-in attempts and session events
CREATE TABLE login_log (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_kind        actor_kind_t NOT NULL,      -- hr_user or ett_staff
    user_id          uuid,                       -- NULL if the email was unknown
    email_attempted  text,
    event            login_event_t NOT NULL,
    success          boolean NOT NULL,
    failure_reason   text,
    ip_address       inet,
    user_agent       text,
    occurred_at      timestamptz NOT NULL DEFAULT now()
);

-- 28. email_log - delivery events for in-app emails
CREATE TABLE email_log (
    id                   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    message_id           uuid REFERENCES messages(id) ON DELETE SET NULL,
    to_address           text,
    provider             text,
    provider_message_id  text,
    event                email_event_t NOT NULL,
    detail               text,
    occurred_at          timestamptz NOT NULL DEFAULT now()
);

-- 29. ai_processing_log - every AI run (CV parsing, screening, scoring)
CREATE TABLE ai_processing_log (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task            ai_task_t NOT NULL,
    application_id  uuid REFERENCES applications(id) ON DELETE SET NULL,
    screening_id    uuid REFERENCES ai_screenings(id) ON DELETE SET NULL,
    interview_id    uuid REFERENCES interviews(id) ON DELETE SET NULL,
    model_version   text,
    status          process_status_t NOT NULL,
    input_tokens    integer,
    output_tokens   integer,
    duration_ms     integer,
    error_message   text,
    started_at      timestamptz,
    finished_at     timestamptz,
    occurred_at     timestamptz NOT NULL DEFAULT now()
);

-- 30. calendar_sync_log - calendar API calls per connection / interview
CREATE TABLE calendar_sync_log (
    id                      bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    calendar_connection_id  uuid REFERENCES calendar_connections(id) ON DELETE SET NULL,
    interview_id            uuid REFERENCES interviews(id) ON DELETE SET NULL,
    action                  calendar_action_t NOT NULL,
    status                  process_status_t NOT NULL,
    external_event_id       text,
    error_message           text,
    occurred_at             timestamptz NOT NULL DEFAULT now()
);

-- 31. document_access_log - who viewed / downloaded / deleted a CV
CREATE TABLE document_access_log (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_id  uuid REFERENCES applications(id) ON DELETE SET NULL,
    file_key        text,
    action          doc_action_t NOT NULL,
    accessed_kind   actor_kind_t,
    accessed_by     uuid,
    ip_address      inet,
    occurred_at     timestamptz NOT NULL DEFAULT now()
);

-- 32. error_log - application / integration errors
CREATE TABLE error_log (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    severity     log_level_t NOT NULL,
    source       text,                           -- service / module name
    message      text NOT NULL,
    stack_trace  text,
    context      jsonb,
    request_id   text,
    user_id      uuid,
    occurred_at  timestamptz NOT NULL DEFAULT now()
);

-- Indexes
CREATE INDEX ON audit_trail (table_name, record_id);
CREATE INDEX ON audit_trail (changed_by);
CREATE INDEX ON audit_trail (changed_at);
CREATE INDEX ON audit_trail (transaction_id);
CREATE INDEX ON login_log (user_kind, user_id);
CREATE INDEX ON login_log (ip_address);
CREATE INDEX ON login_log (occurred_at);
CREATE INDEX ON email_log (message_id);
CREATE INDEX ON email_log (occurred_at);
CREATE INDEX ON ai_processing_log (application_id);
CREATE INDEX ON ai_processing_log (screening_id);
CREATE INDEX ON ai_processing_log (interview_id);
CREATE INDEX ON ai_processing_log (occurred_at);
CREATE INDEX ON calendar_sync_log (calendar_connection_id);
CREATE INDEX ON calendar_sync_log (interview_id);
CREATE INDEX ON document_access_log (application_id);
CREATE INDEX ON document_access_log (accessed_kind, accessed_by);
CREATE INDEX ON document_access_log (occurred_at);
CREATE INDEX ON error_log (severity, occurred_at);
CREATE INDEX ON error_log (request_id);

-- Automatic audit trigger.
-- The app should run, once per transaction/connection:
--   SET LOCAL app.current_user_id    = '<uuid>';
--   SET LOCAL app.current_actor_kind = 'hr_user';   -- hr_user | ett_staff | candidate | system
-- password_hash and totp_secret values are never copied into audit data.
CREATE OR REPLACE FUNCTION fn_audit_trail() RETURNS trigger AS $$
DECLARE
    v_old     jsonb;
    v_new     jsonb;
    v_changed text[];
    v_id      uuid;
    v_uid     uuid         := nullif(current_setting('app.current_user_id', true), '')::uuid;
    v_kind    actor_kind_t := nullif(current_setting('app.current_actor_kind', true), '')::actor_kind_t;
    v_secret  text[]       := ARRAY['password_hash', 'totp_secret'];
BEGIN
    IF TG_OP = 'INSERT' THEN
        v_new := to_jsonb(NEW);
        v_id  := NEW.id;
    ELSIF TG_OP = 'UPDATE' THEN
        v_old := to_jsonb(OLD);
        v_new := to_jsonb(NEW);
        v_id  := NEW.id;
        SELECT array_agg(n.key ORDER BY n.key) INTO v_changed
          FROM jsonb_each(v_new) AS n
         WHERE n.key <> 'last_modified_at'
           AND n.value IS DISTINCT FROM (v_old -> n.key);
        IF v_changed IS NULL THEN
            RETURN NULL;                         -- nothing really changed
        END IF;
    ELSE
        v_old := to_jsonb(OLD);
        v_id  := OLD.id;
    END IF;

    INSERT INTO audit_trail (table_name, record_id, operation, changed_fields,
                             old_data, new_data, changed_by, actor_kind)
    VALUES (TG_TABLE_NAME, v_id, TG_OP, v_changed,
            v_old - v_secret, v_new - v_secret, v_uid, v_kind);
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'companies',
        'hr_users',
        'calendar_connections',
        'jobs',
        'job_rounds',
        'job_criteria',
        'candidates',
        'applications',
        'ett_staff',
        'ai_screenings',
        'match_evidence',
        'shortlist_decisions',
        'partner_centres',
        'centre_slots',
        'partner_assessors',
        'interviews',
        'reschedule_requests',
        'messages',
        'round1_reports',
        'competency_scores',
        'stage_decisions',
        'round2_assessments',
        'interview_panel',
        'round3_feedback'
    ] LOOP
        EXECUTE format(
            'CREATE TRIGGER trg_%s_audit AFTER INSERT OR UPDATE OR DELETE ON %I
             FOR EACH ROW EXECUTE FUNCTION fn_audit_trail()', t, t);
    END LOOP;
END $$;

-- audit_trail is append-only: block UPDATE and DELETE.
CREATE OR REPLACE FUNCTION fn_audit_trail_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_trail is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_trail_immutable
    BEFORE UPDATE OR DELETE ON audit_trail
    FOR EACH ROW EXECUTE FUNCTION fn_audit_trail_immutable();

