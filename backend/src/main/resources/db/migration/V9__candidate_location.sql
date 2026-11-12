-- Where each candidate is, so partner assessment centres can be suggested from the nearest city.
-- The location comes from the CV, from the AI interviewer (asked when the CV has none), or is set by HR.

ALTER TABLE candidates
    ADD COLUMN location_text       text,
    ADD COLUMN location_pin        text,
    ADD COLUMN location_place      text,
    ADD COLUMN location_district   text,
    ADD COLUMN location_region     text,
    ADD COLUMN location_country    text,
    ADD COLUMN location_lat        double precision,
    ADD COLUMN location_lng        double precision,
    ADD COLUMN location_source     text CHECK (location_source IN ('cv', 'ai_interview', 'hr')),
    ADD COLUMN location_updated_at timestamptz;

-- The AI interviewer confirms the candidate's current city and PIN code when the CV doesn't give one.
ALTER TABLE round1_reports
    ADD COLUMN candidate_location text,
    ADD COLUMN candidate_pin      text;

-- Why HR booked a partner assessment centre outside the candidate's nearest centre city.
ALTER TABLE interviews
    ADD COLUMN centre_note text;
