-- Double-entry ledger (Gate 1 decision #8). Every money movement is a group of entries (same tx_id,
-- one currency) that sums to zero. cards.amount stays as a cached balance and must equal the sum of
-- its account's entries.

CREATE TABLE public.ledger_accounts (
    id bigserial PRIMARY KEY,
    -- system accounts have a code; card accounts have a card (set to NULL when a zero-balance card is closed)
    code character varying(32),
    card_id bigint REFERENCES public.cards(id) ON DELETE SET NULL,
    currency character varying(255) NOT NULL,
    CONSTRAINT ledger_accounts_currency_check CHECK (currency IN ('AZN', 'USD', 'EUR')),
    CONSTRAINT ledger_accounts_code_check
        CHECK (code IN ('FEE_REVENUE', 'CASHBACK_EXPENSE', 'STRIPE_CLEARING', 'PAYMENT_PAYOUT', 'OPENING_BALANCE')),
    CONSTRAINT ledger_accounts_code_or_card CHECK (code IS NULL OR card_id IS NULL),
    CONSTRAINT uk_ledger_accounts_card UNIQUE (card_id),
    CONSTRAINT uk_ledger_accounts_code_currency UNIQUE (code, currency)
);

CREATE TABLE public.ledger_entries (
    id bigserial PRIMARY KEY,
    tx_id uuid NOT NULL,
    account_id bigint NOT NULL REFERENCES public.ledger_accounts(id),
    -- signed: positive raises the account's balance, negative lowers it
    amount numeric(19,2) NOT NULL CHECK (amount <> 0),
    currency character varying(255) NOT NULL,
    kind character varying(32) NOT NULL,
    reference character varying(64) NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT ledger_entries_currency_check CHECK (currency IN ('AZN', 'USD', 'EUR'))
);
CREATE INDEX idx_ledger_entries_account_id ON public.ledger_entries USING btree (account_id);
CREATE INDEX idx_ledger_entries_tx_id ON public.ledger_entries USING btree (tx_id);

-- Entries are immutable: a mistake is corrected by a new, reversing entry.
CREATE FUNCTION public.ledger_entries_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'ledger_entries is append-only';
END
$$;
CREATE TRIGGER ledger_entries_append_only BEFORE UPDATE OR DELETE ON public.ledger_entries
    FOR EACH ROW EXECUTE FUNCTION public.ledger_entries_append_only();

INSERT INTO public.ledger_accounts (code, currency)
SELECT code, currency
FROM unnest(ARRAY['FEE_REVENUE', 'CASHBACK_EXPENSE', 'STRIPE_CLEARING', 'PAYMENT_PAYOUT', 'OPENING_BALANCE']) AS code
CROSS JOIN unnest(ARRAY['AZN', 'USD', 'EUR']) AS currency;

INSERT INTO public.ledger_accounts (card_id, currency) SELECT id, currency FROM public.cards;

-- Existing balances were never backed by entries; book them against OPENING_BALANCE.
WITH opening AS MATERIALIZED (
    SELECT c.id AS card_id, c.amount, c.currency, gen_random_uuid() AS tx_id
    FROM public.cards c WHERE c.amount <> 0
)
INSERT INTO public.ledger_entries (tx_id, account_id, amount, currency, kind, reference)
SELECT o.tx_id, a.id, o.amount, o.currency, 'OPENING_BALANCE', 'V7'
FROM opening o JOIN public.ledger_accounts a ON a.card_id = o.card_id
UNION ALL
SELECT o.tx_id, s.id, -o.amount, o.currency, 'OPENING_BALANCE', 'V7'
FROM opening o JOIN public.ledger_accounts s ON s.code = 'OPENING_BALANCE' AND s.currency = o.currency;
