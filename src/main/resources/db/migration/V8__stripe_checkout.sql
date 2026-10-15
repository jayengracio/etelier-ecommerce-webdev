-- Phase 4: Stripe Checkout.

-- The Checkout Session that pays for this order. NULL for orders placed before
-- Stripe, and briefly for a new order (it needs its id before the session is created).
-- UNIQUE allows many NULLs in Postgres, and gives the webhook an index to look up by.
ALTER TABLE orders ADD COLUMN stripe_session_id VARCHAR(255);
ALTER TABLE orders ADD CONSTRAINT orders_stripe_session_id_unique UNIQUE (stripe_session_id);

-- CANCELLED: the session expired unpaid, or the payment was refunded because stock ran out.
ALTER TABLE orders DROP CONSTRAINT orders_status_check;
ALTER TABLE orders ADD CONSTRAINT orders_status_check
    CHECK (status IN ('PENDING', 'PAID', 'SHIPPED', 'CANCELLED'));
