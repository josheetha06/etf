-- Release 2: each job sets its own rounds, AI shortlisting can be turned off, interview panels review through a
-- generic login with employee ID + interview code, HR can hand their jobs to a colleague while on leave, candidates
-- ask for a new time from their own page, and every change is shown with who made it.

-- ---------------------------------------------------------------------
-- 1. Rounds per job: any order, up to 8, each with a type, a name, a length and a target time.
-- ---------------------------------------------------------------------
CREATE TYPE round_kind_t AS ENUM ('ai_interview', 'partner', 'panel');

ALTER TABLE job_rounds
    ADD COLUMN name        text,
    ADD COLUMN kind        round_kind_t,
    ADD COLUMN target_days integer CHECK (target_days BETWEEN 1 AND 90);

-- Jobs made before this release ran ETT's fixed three rounds.
UPDATE job_rounds
   SET kind = (CASE round WHEN 1 THEN 'ai_interview' WHEN 2 THEN 'partner' ELSE 'panel' END)::round_kind_t,
       name = CASE round WHEN 1 THEN 'AI interview' WHEN 2 THEN 'Partner assessment' ELSE 'Hiring panel' END
 WHERE kind IS NULL;

ALTER TABLE job_rounds
    ALTER COLUMN kind SET NOT NULL,
    ALTER COLUMN name SET NOT NULL,
    ADD CONSTRAINT job_rounds_round_range CHECK (round BETWEEN 1 AND 8),
    ADD CONSTRAINT job_rounds_name_length CHECK (length(name) BETWEEN 1 AND 60);
CREATE UNIQUE INDEX IF NOT EXISTS ux_job_rounds_job_round ON job_rounds (job_id, round);

-- ---------------------------------------------------------------------
-- 2. Jobs: AI shortlisting switch, job description, hiring manager, hold/close reason.
-- ---------------------------------------------------------------------
ALTER TABLE jobs
    ADD COLUMN ai_shortlisting       boolean NOT NULL DEFAULT true,
    ADD COLUMN description           text CHECK (length(description) <= 20000),
    ADD COLUMN description_file_key  text,
    ADD COLUMN description_file_name text,
    ADD COLUMN hiring_manager_id     uuid REFERENCES hr_users(id),
    ADD COLUMN status_reason         text CHECK (length(status_reason) <= 500),
    ADD COLUMN status_changed_at     timestamptz,
    ADD COLUMN status_changed_by     uuid REFERENCES hr_users(id);

-- A job belongs to the person who created it (HR admins can reassign it).
UPDATE jobs SET owner_id = created_by WHERE owner_id IS NULL;
CREATE INDEX IF NOT EXISTS ix_jobs_owner ON jobs (company_id, owner_id);
CREATE INDEX IF NOT EXISTS ix_jobs_hiring_manager ON jobs (hiring_manager_id) WHERE hiring_manager_id IS NOT NULL;

-- The old Interviewer role is replaced by the panel login.
UPDATE hr_users SET role = 'hiring_manager' WHERE role = 'interviewer';

-- ---------------------------------------------------------------------
-- 3. Applications: when the candidate reached their current step, for each round's target time.
-- ---------------------------------------------------------------------
ALTER TABLE applications
    ADD COLUMN stage_entered_at     timestamptz,
    ADD COLUMN due_reminder_sent_at timestamptz;
UPDATE applications SET stage_entered_at = coalesce(last_modified_at, created_at) WHERE stage_entered_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_applications_stage ON applications (job_id, stage) WHERE status = 'active';

-- ---------------------------------------------------------------------
-- 4. Interviews: in-person venue, and the interview code for panel rounds.
-- ---------------------------------------------------------------------
ALTER TABLE interviews
    ADD COLUMN venue           text CHECK (length(venue) <= 300),
    ADD COLUMN panel_code_hash text UNIQUE,
    ADD COLUMN panel_code_enc  text;

-- People on an interview's panel: typed in by HR for each interview (no saved list).
CREATE TABLE panel_members (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    interview_id     uuid NOT NULL REFERENCES interviews(id) ON DELETE CASCADE,
    employee_id      text NOT NULL CHECK (length(employee_id) BETWEEN 1 AND 40),
    full_name        text NOT NULL CHECK (length(full_name) BETWEEN 1 AND 120),
    email            text NOT NULL CHECK (email ~* '^[^@\s]+@[^@\s]+\.[^@\s]+$'),
    notified_at      timestamptz,
    created_by       uuid,
    created_at       timestamptz NOT NULL DEFAULT now(),
    last_modified_by uuid,
    last_modified_at timestamptz NOT NULL DEFAULT now(),
    owner_id         uuid,
    assigned_id      uuid
);
CREATE UNIQUE INDEX ux_panel_members_employee ON panel_members (interview_id, upper(employee_id));

-- One detailed review per panel member per interview. Drafts are saved as they type.
CREATE TABLE panel_reviews (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    interview_id     uuid NOT NULL REFERENCES interviews(id) ON DELETE CASCADE,
    panel_member_id  uuid NOT NULL UNIQUE REFERENCES panel_members(id) ON DELETE CASCADE,
    skill_scores     jsonb NOT NULL DEFAULT '[]'::jsonb,   -- [{skill, kind, score 1-5, comment}]
    measure_scores   jsonb NOT NULL DEFAULT '[]'::jsonb,   -- [{measure, score 1-5, comment}]
    strengths        text CHECK (length(strengths) <= 4000),
    concerns         text CHECK (length(concerns) <= 4000),
    questions        text CHECK (length(questions) <= 4000),
    summary          text CHECK (length(summary) <= 8000),
    next_round_notes text CHECK (length(next_round_notes) <= 4000),
    recommendation   text CHECK (recommendation IN ('strong_hire', 'hire', 'no_hire', 'strong_no_hire')),
    overall_score    numeric(3, 1) CHECK (overall_score BETWEEN 1 AND 5),
    submitted_at     timestamptz,
    device           text,
    ip_address       text,
    is_sample        boolean NOT NULL DEFAULT false,
    created_by       uuid,
    created_at       timestamptz NOT NULL DEFAULT now(),
    last_modified_by uuid,
    last_modified_at timestamptz NOT NULL DEFAULT now(),
    owner_id         uuid,
    assigned_id      uuid
);
CREATE INDEX ix_panel_reviews_interview ON panel_reviews (interview_id);

-- Every try at the panel sign-in page; 5 wrong tries lock the panel login for 15 minutes.
CREATE TABLE panel_access_attempts (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    hr_user_id  uuid NOT NULL REFERENCES hr_users(id) ON DELETE CASCADE,
    employee_id text,
    succeeded   boolean NOT NULL,
    reason      text,
    ip_address  text,
    at          timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_panel_access_attempts_user ON panel_access_attempts (hr_user_id, at DESC);

-- ---------------------------------------------------------------------
-- 5. Settings HR admins can change.
-- ---------------------------------------------------------------------
ALTER TABLE companies
    ADD COLUMN address   text CHECK (length(address) <= 500),
    ADD COLUMN time_zone text NOT NULL DEFAULT 'Asia/Kolkata';

-- The round plan that pre-fills every new job.
CREATE TABLE round_templates (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id       uuid NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    round            integer NOT NULL CHECK (round BETWEEN 1 AND 8),
    name             text NOT NULL CHECK (length(name) BETWEEN 1 AND 60),
    kind             round_kind_t NOT NULL,
    mode             interview_mode_t NOT NULL DEFAULT 'online',
    duration_min     integer NOT NULL CHECK (duration_min BETWEEN 15 AND 480),
    target_days      integer CHECK (target_days BETWEEN 1 AND 90),
    created_by       uuid,
    created_at       timestamptz NOT NULL DEFAULT now(),
    last_modified_by uuid,
    last_modified_at timestamptz NOT NULL DEFAULT now(),
    owner_id         uuid,
    assigned_id      uuid,
    UNIQUE (company_id, round)
);
INSERT INTO round_templates (company_id, round, name, kind, mode, duration_min, target_days)
SELECT c.id, t.round, t.name, t.kind::round_kind_t, t.mode::interview_mode_t, t.minutes, t.days
  FROM companies c
 CROSS JOIN (VALUES (1, 'Technical Level 1', 'panel', 'online', 60, 3),
                    (2, 'Technical Level 2', 'panel', 'online', 60, 5),
                    (3, 'HR round', 'panel', 'online', 30, 3)) AS t(round, name, kind, mode, minutes, days);

-- The general measures on the panel review form (skills come from each job).
CREATE TABLE review_measures (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id       uuid NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    name             text NOT NULL CHECK (length(name) BETWEEN 1 AND 60),
    position         integer NOT NULL,
    created_by       uuid,
    created_at       timestamptz NOT NULL DEFAULT now(),
    last_modified_by uuid,
    last_modified_at timestamptz NOT NULL DEFAULT now(),
    owner_id         uuid,
    assigned_id      uuid
);
CREATE UNIQUE INDEX ux_review_measures_name ON review_measures (company_id, lower(name));
INSERT INTO review_measures (company_id, name, position)
SELECT c.id, m.name, m.position
  FROM companies c
 CROSS JOIN (VALUES ('Communication', 1), ('Problem solving', 2), ('Technical depth', 3),
                    ('Attitude and motivation', 4), ('Teamwork', 5), ('Ownership', 6), ('Learning ability', 7),
                    ('Professionalism', 8)) AS m(name, position);

-- Email wording a company has changed (the portal's own wording is used otherwise).
CREATE TABLE email_templates (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id       uuid NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    template_key     text NOT NULL,
    subject          text NOT NULL CHECK (length(subject) BETWEEN 1 AND 200),
    body             text NOT NULL CHECK (length(body) BETWEEN 1 AND 8000),
    created_by       uuid,
    created_at       timestamptz NOT NULL DEFAULT now(),
    last_modified_by uuid,
    last_modified_at timestamptz NOT NULL DEFAULT now(),
    owner_id         uuid,
    assigned_id      uuid,
    UNIQUE (company_id, template_key)
);

-- Partner centres: shared ETT network (no company) or a company's own, which its HR admins manage.
ALTER TABLE partner_centres
    ADD COLUMN company_id uuid REFERENCES companies(id) ON DELETE CASCADE,
    ADD COLUMN active     boolean NOT NULL DEFAULT true;

-- ---------------------------------------------------------------------
-- 6. Leave cover: an HR person's jobs are handed to a colleague for set dates.
-- ---------------------------------------------------------------------
CREATE TABLE leave_cover (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id        uuid NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    user_id           uuid NOT NULL REFERENCES hr_users(id) ON DELETE CASCADE,
    cover_user_id     uuid NOT NULL REFERENCES hr_users(id) ON DELETE CASCADE,
    starts_on         date NOT NULL,
    ends_on           date NOT NULL,
    note              text CHECK (length(note) <= 500),
    ended_early_at    timestamptz,
    start_notified_at timestamptz,
    created_by        uuid,
    created_at        timestamptz NOT NULL DEFAULT now(),
    last_modified_by  uuid,
    last_modified_at  timestamptz NOT NULL DEFAULT now(),
    owner_id          uuid,
    assigned_id       uuid,
    CHECK (ends_on >= starts_on),
    CHECK (user_id <> cover_user_id)
);
CREATE INDEX ix_leave_cover_user ON leave_cover (user_id, starts_on, ends_on);
CREATE INDEX ix_leave_cover_cover ON leave_cover (cover_user_id, starts_on, ends_on);

-- ---------------------------------------------------------------------
-- 7. Candidate page: secure links in invitation emails, and the dates a candidate says they are free.
-- ---------------------------------------------------------------------
CREATE TABLE candidate_links (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id uuid NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    interview_id   uuid REFERENCES interviews(id) ON DELETE CASCADE,
    token_hash     text NOT NULL UNIQUE,
    expires_at     timestamptz NOT NULL,
    last_used_at   timestamptz,
    created_at     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_candidate_links_application ON candidate_links (application_id);

ALTER TABLE reschedule_requests
    ADD COLUMN availability jsonb,              -- [{date, window: morning|afternoon|any|range, from, to}]
    ADD COLUMN requested_by_candidate boolean NOT NULL DEFAULT false;

-- ---------------------------------------------------------------------
-- 8. Email delivery: failed emails are retried, and HR is told if they still fail.
-- ---------------------------------------------------------------------
ALTER TABLE messages
    ADD COLUMN send_attempts integer NOT NULL DEFAULT 0,
    ADD COLUMN next_attempt_at timestamptz,
    ADD COLUMN failed_at timestamptz,
    ADD COLUMN failure_reason text;
CREATE INDEX IF NOT EXISTS ix_messages_retry ON messages (next_attempt_at)
    WHERE sent_at IS NULL AND failed_at IS NULL AND direction = 'outbound';

-- Emails to people who aren't candidates (panel members, partner assessors, colleagues covering leave).
CREATE TABLE email_outbox (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id      uuid REFERENCES companies(id) ON DELETE CASCADE,
    interview_id    uuid REFERENCES interviews(id) ON DELETE SET NULL,
    job_id          uuid REFERENCES jobs(id) ON DELETE SET NULL,
    purpose         text NOT NULL,
    to_address      text NOT NULL,
    to_name         text,
    subject         text NOT NULL,
    body            text NOT NULL,
    send_attempts   integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    sent_at         timestamptz,
    failed_at       timestamptz,
    failure_reason  text,
    created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_email_outbox_due ON email_outbox (next_attempt_at) WHERE sent_at IS NULL AND failed_at IS NULL;
CREATE INDEX ix_email_outbox_interview ON email_outbox (interview_id);

-- ---------------------------------------------------------------------
-- 9. Audit trail: who acted when it wasn't an HR login (panel member, partner assessor, candidate), and whose
--    jobs were being covered.
-- ---------------------------------------------------------------------
ALTER TABLE audit_trail
    ADD COLUMN actor_label  text,
    ADD COLUMN on_behalf_of uuid;
CREATE INDEX IF NOT EXISTS ix_audit_trail_record ON audit_trail (table_name, record_id, id DESC);
CREATE INDEX IF NOT EXISTS ix_audit_trail_changed_at ON audit_trail (changed_at DESC);

CREATE OR REPLACE FUNCTION fn_audit_trail() RETURNS trigger AS $$
DECLARE
    v_old     jsonb;
    v_new     jsonb;
    v_changed text[];
    v_id      uuid;
    v_uid     uuid         := nullif(current_setting('app.current_user_id', true), '')::uuid;
    v_kind    actor_kind_t := nullif(current_setting('app.current_actor_kind', true), '')::actor_kind_t;
    v_label   text         := nullif(current_setting('app.actor_label', true), '');
    v_behalf  uuid         := nullif(current_setting('app.on_behalf_of', true), '')::uuid;
    v_secret  text[]       := ARRAY['password_hash', 'totp_secret', 'panel_code_hash', 'panel_code_enc', 'token_hash'];
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
         WHERE n.key NOT IN ('last_modified_at', 'last_modified_by')
           AND n.value IS DISTINCT FROM (v_old -> n.key);
        IF v_changed IS NULL THEN
            RETURN NULL;                         -- nothing really changed
        END IF;
    ELSE
        v_old := to_jsonb(OLD);
        v_id  := OLD.id;
    END IF;

    INSERT INTO audit_trail (table_name, record_id, operation, changed_fields,
                             old_data, new_data, changed_by, actor_kind, actor_label, on_behalf_of)
    VALUES (TG_TABLE_NAME, v_id, TG_OP, v_changed,
            v_old - v_secret, v_new - v_secret, v_uid, v_kind, v_label, v_behalf);
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- Who last changed a row, even when it was changed with plain SQL rather than through an entity.
CREATE OR REPLACE FUNCTION set_last_modified_at() RETURNS trigger AS $$
DECLARE
    v_uid uuid := nullif(current_setting('app.current_user_id', true), '')::uuid;
BEGIN
    NEW.last_modified_at := now();
    IF v_uid IS NOT NULL THEN
        NEW.last_modified_by := v_uid;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'panel_members',
        'panel_reviews',
        'round_templates',
        'review_measures',
        'email_templates',
        'leave_cover'
    ] LOOP
        EXECUTE format(
            'CREATE TRIGGER trg_%s_last_modified BEFORE UPDATE ON %I
             FOR EACH ROW EXECUTE FUNCTION set_last_modified_at()', t, t);
        EXECUTE format(
            'CREATE TRIGGER trg_%s_audit AFTER INSERT OR UPDATE OR DELETE ON %I
             FOR EACH ROW EXECUTE FUNCTION fn_audit_trail()', t, t);
    END LOOP;
END $$;
