-- Phase 5: soft delete for products.
-- Products can't be deleted: order_items references them (ON DELETE RESTRICT), and old orders
-- must keep showing what was bought. Hiding a product (active = false) takes it out of the shop instead.
-- DEFAULT TRUE fills in the existing rows, so every current product stays visible.
ALTER TABLE product ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
