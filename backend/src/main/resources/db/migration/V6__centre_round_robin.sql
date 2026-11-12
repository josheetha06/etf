-- Round 2: nearby partner centres take turns (round robin), so the nearest one doesn't get every candidate.
-- Each booking stamps its centre with the next turn number; the centre that has gone longest without a candidate
-- (lowest or no turn number) is suggested next.
CREATE SEQUENCE IF NOT EXISTS centre_turn_seq;

ALTER TABLE partner_centres
    ADD COLUMN last_turn        bigint,
    ADD COLUMN last_assigned_at timestamptz;

-- Start the queue from the bookings already made.
UPDATE partner_centres c
   SET last_assigned_at = b.last_at
  FROM (SELECT s.centre_id, max(i.created_at) AS last_at
          FROM interviews i JOIN centre_slots s ON s.id = i.slot_id
         GROUP BY s.centre_id) b
 WHERE b.centre_id = c.id;

UPDATE partner_centres c
   SET last_turn = t.n
  FROM (SELECT id, row_number() OVER (ORDER BY last_assigned_at, id) AS n
          FROM partner_centres WHERE last_assigned_at IS NOT NULL) t
 WHERE t.id = c.id;

SELECT setval('centre_turn_seq', coalesce((SELECT max(last_turn) FROM partner_centres), 0) + 1, false);
