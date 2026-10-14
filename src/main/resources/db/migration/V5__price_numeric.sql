-- Money as exact decimals. DOUBLE PRECISION can't represent values like 0.10 exactly
ALTER TABLE product
    ALTER COLUMN price TYPE NUMERIC(10,2) USING round(price::numeric, 2);
