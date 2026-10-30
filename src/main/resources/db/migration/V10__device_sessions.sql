-- One row per signed-in device (decision #13). Only the SHA-256 of the current refresh token is stored (SEC-18);
-- a validly signed refresh token for this session that does not match it is a reused, already rotated one.
DROP TABLE public.refresh_token;

CREATE TABLE public.user_sessions (
    id uuid PRIMARY KEY,
    user_id bigint NOT NULL REFERENCES public.users(id),
    device_name character varying(255),
    refresh_token_hash character varying(64) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    last_used_at timestamp with time zone NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone
);

CREATE INDEX ix_user_sessions_user_id ON public.user_sessions (user_id);
