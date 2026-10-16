-- Phase 5: admin refunds.

-- The PaymentIntent (pi_...) that charged this order, saved by the webhook when the order is paid.
-- Refunds are made against it, and it links to the payment in the Stripe Dashboard.
-- NULL for unpaid orders and for orders paid before V9 (a refund looks it up from the session).
ALTER TABLE orders ADD COLUMN stripe_payment_intent_id VARCHAR(255);

-- REFUNDED: an admin refunded a PAID order and its stock went back.
-- CANCELLED keeps meaning "never paid" (session expired, or stock ran out and it was auto-refunded).
ALTER TABLE orders DROP CONSTRAINT orders_status_check;
ALTER TABLE orders ADD CONSTRAINT orders_status_check
    CHECK (status IN ('PENDING', 'PAID', 'SHIPPED', 'CANCELLED', 'REFUNDED'));
