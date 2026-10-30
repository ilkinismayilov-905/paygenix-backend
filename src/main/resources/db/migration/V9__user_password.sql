-- Login uses a separate password (Gate 1 decision #11); the PIN only confirms transactions.
-- Existing users are test data and get no password (decision #11: no migration); Google users have none.
ALTER TABLE public.users ADD COLUMN password_hash character varying(255);
