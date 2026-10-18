-- Where a product's picture is: a path in this app ('/images/Apple.png') or a full URL.
-- NULL means it has none, and the pages show a placeholder instead. The check keeps out
-- values that would give a broken image on some pages: '' or 'images/Apple.png' (a relative
-- path resolves against the current page, so it breaks on /details/5). NULL passes a CHECK.
ALTER TABLE product
    ADD COLUMN image VARCHAR(1000)
    CONSTRAINT product_image_check CHECK (image ~ '^(/|https?://)');

-- The seed products that have a file in static/images.
UPDATE product SET image = '/images/' || name || '.png'
WHERE name IN ('Apple', 'Banana', 'Grapes', 'Donut', 'Pizza', 'Laptop', 'After Hours', 'Red Dead Redemption 2');
