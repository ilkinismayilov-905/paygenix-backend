-- numeric(5,2) caps commission and cashback at 999.99: a 50,000 transfer (2% = 1000.00) or a
-- 200,000 payment (0.5% = 1000.00) failed with "numeric field overflow". Widen to the card balance size.
ALTER TABLE public.transactions ALTER COLUMN commission_value TYPE numeric(19,2);
ALTER TABLE public.payment_transactions ALTER COLUMN cashback_value TYPE numeric(19,2);
