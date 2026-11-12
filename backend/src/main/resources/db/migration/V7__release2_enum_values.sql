-- Release 2 needs new enum values. PostgreSQL only lets a new value be used once it is committed, so they are added
-- here and used from V8 on.

-- Up to 8 interview rounds per job.
ALTER TYPE application_stage_t ADD VALUE IF NOT EXISTS 'round4' AFTER 'round3';
ALTER TYPE application_stage_t ADD VALUE IF NOT EXISTS 'round5' AFTER 'round4';
ALTER TYPE application_stage_t ADD VALUE IF NOT EXISTS 'round6' AFTER 'round5';
ALTER TYPE application_stage_t ADD VALUE IF NOT EXISTS 'round7' AFTER 'round6';
ALTER TYPE application_stage_t ADD VALUE IF NOT EXISTS 'round8' AFTER 'round7';

-- A job can be put on hold.
ALTER TYPE job_status_t ADD VALUE IF NOT EXISTS 'on_hold' AFTER 'shortlist_confirmed';

-- The generic panel login.
ALTER TYPE hr_user_role_t ADD VALUE IF NOT EXISTS 'panel';

-- Who did something, for the audit trail: panel members and partner assessors act without an HR login.
ALTER TYPE actor_kind_t ADD VALUE IF NOT EXISTS 'panel_member';
ALTER TYPE actor_kind_t ADD VALUE IF NOT EXISTS 'partner_assessor';

-- Candidates message HR from their own page.
ALTER TYPE message_channel_t ADD VALUE IF NOT EXISTS 'portal';
