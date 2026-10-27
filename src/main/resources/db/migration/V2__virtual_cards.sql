-- Virtual cards (Gate 1 decision #4): no CVV, no stored card number, every card has an owner.
-- The card number is looked up by HMAC-SHA256(number, server key); only the last four digits are kept.

-- Existing cards are test data with plaintext numbers and CVVs: remove them rather than migrate.
DELETE FROM public.cards;

ALTER TABLE public.cards
    DROP COLUMN card_number,
    DROP COLUMN cvv,
    ADD COLUMN owner_id bigint NOT NULL,
    ADD COLUMN last4 character varying(4) NOT NULL,
    ADD COLUMN pan_hmac character varying(64) NOT NULL;

ALTER TABLE ONLY public.cards
    ADD CONSTRAINT uk_cards_pan_hmac UNIQUE (pan_hmac);

ALTER TABLE ONLY public.cards
    ADD CONSTRAINT fk_cards_owner FOREIGN KEY (owner_id) REFERENCES public.users(id);

CREATE INDEX idx_cards_owner_id ON public.cards USING btree (owner_id);

-- History tables held full card numbers; keep only the last four digits.
UPDATE public.transactions SET from_card_id = right(from_card_id, 4), to_card_id = right(to_card_id, 4);
UPDATE public.payment_transactions SET from_card_id = right(from_card_id, 4);
UPDATE public.transfer SET from_card_number = right(from_card_number, 4), to_card_number = right(to_card_number, 4);
UPDATE public.payment SET from_card_number = right(from_card_number, 4);
UPDATE public.payment_onboarding SET from_card_number = right(from_card_number, 4);

-- Pending payments now point at the card by id. Old pending ones referenced cards deleted above.
DELETE FROM public.payment_onboarding WHERE payment_status = 'PENDING';
ALTER TABLE public.payment_onboarding ADD COLUMN from_card_id bigint;
