-- Partner centres on the map, so Round 2 can suggest the centres nearest the candidate, and in their own time zone
-- (UK centres keep UK opening hours).
ALTER TABLE partner_centres
    ADD COLUMN country   text,
    ADD COLUMN postcode  text,
    ADD COLUMN latitude  double precision CHECK (latitude BETWEEN -90 AND 90),
    ADD COLUMN longitude double precision CHECK (longitude BETWEEN -180 AND 180),
    ADD COLUMN time_zone text NOT NULL DEFAULT 'Asia/Kolkata';

CREATE INDEX IF NOT EXISTS ix_centre_slots_centre_start ON centre_slots (centre_id, start_at);
CREATE INDEX IF NOT EXISTS ix_interviews_assessor_start ON interviews (assessor_id, start_at) WHERE assessor_id IS NOT NULL;
