-- T-144: a phone payment first moves the amount from the card to PAYMENT_HOLD and commits, then calls the operator
-- outside any transaction, then settles (hold -> payout) or releases (hold -> card, kind PAYMENT_RELEASE).
-- While the operator's answer is unknown the flow is PROCESSING; PaymentReconciliation asks the operator later.
ALTER TABLE public.payment_onboarding DROP CONSTRAINT payment_onboarding_payment_status_check;
ALTER TABLE public.payment_onboarding ADD CONSTRAINT payment_onboarding_payment_status_check
    CHECK (payment_status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'EXPIRED'));
-- the id sent to the operator: one per reservation, so a retry of the same reservation cannot deliver twice
ALTER TABLE public.payment_onboarding ADD COLUMN operator_reference varchar(64);
ALTER TABLE public.payment_onboarding ADD COLUMN reserved_at timestamp with time zone;
ALTER TABLE public.payment_onboarding ADD CONSTRAINT uk_payment_onboarding_operator_reference UNIQUE (operator_reference);
CREATE INDEX idx_payment_onboarding_processing ON public.payment_onboarding (reserved_at)
    WHERE payment_status = 'PROCESSING';

ALTER TABLE public.ledger_accounts DROP CONSTRAINT ledger_accounts_code_check;
ALTER TABLE public.ledger_accounts ADD CONSTRAINT ledger_accounts_code_check
    CHECK (code IN ('FEE_REVENUE', 'CASHBACK_EXPENSE', 'STRIPE_CLEARING', 'PAYMENT_PAYOUT', 'OPENING_BALANCE', 'PAYMENT_HOLD'));
INSERT INTO public.ledger_accounts (code, currency)
SELECT 'PAYMENT_HOLD', currency FROM unnest(ARRAY['AZN', 'USD', 'EUR']) AS currency;

ALTER TABLE public.ledger_entries DROP CONSTRAINT ledger_entries_kind_check;
ALTER TABLE public.ledger_entries ADD CONSTRAINT ledger_entries_kind_check
    CHECK (kind IN ('TRANSFER', 'PAYMENT', 'CASHBACK', 'TOP_UP', 'OPENING_BALANCE', 'PAYMENT_RELEASE'));
