-- Posting kinds are a fixed set (LedgerKind).
ALTER TABLE public.ledger_entries ADD CONSTRAINT ledger_entries_kind_check
    CHECK (kind IN ('TRANSFER', 'PAYMENT', 'CASHBACK', 'TOP_UP', 'OPENING_BALANCE'));

-- Cards are closed, not deleted: the row keeps the ledger history tied to its card and owner, and a
-- closed card's number (pan_hmac stays unique) is never issued again.
ALTER TABLE public.cards ADD COLUMN closed_at timestamp with time zone;
