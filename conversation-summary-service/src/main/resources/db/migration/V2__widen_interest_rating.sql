-- Widen interest rating from a 1-5 integer to a 0-10 decimal so the LLM
-- can express finer-grained scores (e.g. 7.5). The summary-service prompt
-- and downstream consumers (dashboard + lead-trigger threshold) all expect
-- the new range from this migration onwards.
ALTER TABLE call_summaries
    ALTER COLUMN interest_rating TYPE NUMERIC(3,1)
    USING interest_rating::numeric(3,1);
