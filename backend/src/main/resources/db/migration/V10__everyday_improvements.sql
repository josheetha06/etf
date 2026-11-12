-- Everyday improvements: undo a decision, ready-made reasons, job stars, details from the AI interview,
-- and closing a job cleanly.

-- ---------------------------------------------------------------------
-- 1. Undo a round decision within 5 minutes.
--    The candidate's email waits 5 minutes before it goes, so undoing cancels it.
-- ---------------------------------------------------------------------
ALTER TABLE stage_decisions
    ADD COLUMN message_id      uuid REFERENCES messages(id) ON DELETE SET NULL,
    ADD COLUMN stage_before    application_stage_t,
    ADD COLUMN entered_before  timestamptz;

ALTER TABLE messages
    ADD COLUMN cancelled_at timestamptz;

-- ---------------------------------------------------------------------
-- 2. Ready-made reasons for "Not progressing", edited by HR admins.
-- ---------------------------------------------------------------------
CREATE TABLE decision_reasons (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id       uuid NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    reason           text NOT NULL CHECK (length(reason) BETWEEN 10 AND 300),
    position         integer NOT NULL,
    created_by       uuid,
    created_at       timestamptz NOT NULL DEFAULT now(),
    last_modified_by uuid,
    last_modified_at timestamptz NOT NULL DEFAULT now(),
    owner_id         uuid,
    assigned_id      uuid
);
CREATE INDEX ix_decision_reasons_company ON decision_reasons (company_id, position);

-- ---------------------------------------------------------------------
-- 3. Personal job stars: each person stars the jobs that matter to them.
-- ---------------------------------------------------------------------
CREATE TABLE job_stars (
    hr_user_id uuid NOT NULL REFERENCES hr_users(id) ON DELETE CASCADE,
    job_id     uuid NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (hr_user_id, job_id)
);

-- ---------------------------------------------------------------------
-- 4. Notice period and expected CTC from the AI interview, when the CV left them out.
-- ---------------------------------------------------------------------
ALTER TABLE round1_reports
    ADD COLUMN candidate_notice_days      integer CHECK (candidate_notice_days BETWEEN 0 AND 365),
    ADD COLUMN candidate_expected_ctc_lpa numeric(6, 2) CHECK (candidate_expected_ctc_lpa >= 0);

ALTER TABLE application_key_facts
    ADD COLUMN notice_source text CHECK (notice_source IN ('cv', 'ai_interview', 'hr')),
    ADD COLUMN ctc_source    text CHECK (ctc_source IN ('cv', 'ai_interview', 'hr'));

-- ---------------------------------------------------------------------
-- 5. Closing a job: why an interview was cancelled, so history can say so.
-- ---------------------------------------------------------------------
ALTER TABLE interviews
    ADD COLUMN cancel_reason text;

DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['decision_reasons'] LOOP
        EXECUTE format(
            'CREATE TRIGGER trg_%s_last_modified BEFORE UPDATE ON %I
             FOR EACH ROW EXECUTE FUNCTION set_last_modified_at()', t, t);
        EXECUTE format(
            'CREATE TRIGGER trg_%s_audit AFTER INSERT OR UPDATE OR DELETE ON %I
             FOR EACH ROW EXECUTE FUNCTION fn_audit_trail()', t, t);
    END LOOP;
END $$;

-- Starting reasons for companies that already exist (new companies get the same from the app).
INSERT INTO decision_reasons (company_id, reason, position)
SELECT c.id, r.reason, r.position
  FROM companies c
 CROSS JOIN (VALUES
       ('The role needs deeper hands-on experience in the must-have skills than we saw in this round.', 1),
       ('Other candidates matched the role''s requirements more closely at this stage.', 2),
       ('We were looking for clearer problem solving and reasoning in the technical questions.', 3),
       ('Your notice period is longer than this role can wait for.', 4),
       ('Your expected salary is above the budget for this role.', 5),
       ('You weren''t able to attend the interview, and we couldn''t find another time that worked.', 6)
     ) AS r(reason, position)
 WHERE NOT EXISTS (SELECT 1 FROM decision_reasons d WHERE d.company_id = c.id);
