-- T-132: a started payment that is not confirmed within 15 minutes expires (PaymentHousekeeping marks it).
ALTER TABLE public.payment_onboarding DROP CONSTRAINT payment_onboarding_payment_status_check;
ALTER TABLE public.payment_onboarding ADD CONSTRAINT payment_onboarding_payment_status_check
    CHECK (payment_status IN ('PENDING', 'COMPLETED', 'EXPIRED'));
CREATE INDEX idx_payment_onboarding_pending ON public.payment_onboarding (creation_date) WHERE payment_status = 'PENDING';
-- the daily idempotency purge deletes by age
CREATE INDEX idx_idempotency_keys_created_at ON public.idempotency_keys (created_at);
