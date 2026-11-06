-- The report form has an optional reference (card ending, amount, date); it was accepted by the API but never stored.
ALTER TABLE public.problem_reports ADD COLUMN reference_id character varying(100);
