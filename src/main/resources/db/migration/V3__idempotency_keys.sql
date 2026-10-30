-- Idempotency keys for money-moving endpoints (MASTER_PROMPT section 9).
-- A key is unique per user; the stored response is replayed when the same request is retried.
CREATE TABLE public.idempotency_keys (
    id bigserial PRIMARY KEY,
    user_id bigint NOT NULL REFERENCES public.users(id),
    idem_key character varying(255) NOT NULL,
    request_hash character varying(64) NOT NULL,
    response_body text NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT uk_idempotency_keys_user_key UNIQUE (user_id, idem_key)
);
