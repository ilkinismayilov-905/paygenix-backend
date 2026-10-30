-- Google accounts (decision #12): matched by Google's stable subject id, not by email (SEC-10).
-- They are created without a PIN and set one before their first payment.
ALTER TABLE public.users ADD COLUMN provider_id character varying(255);
ALTER TABLE public.users ADD CONSTRAINT uk_users_provider_provider_id UNIQUE (provider, provider_id);
ALTER TABLE public.users ALTER COLUMN pin_hash DROP NOT NULL;
