-- Seed products whose picture file has an older name than the product, so V3 skipped them.
UPDATE product p SET image = v.image
FROM (VALUES
    ('Desktop Computer',                         '/images/Computer.png'),
    ('Ultrabook',                                '/images/Macbook Air.png'),
    ('Mechanical Keyboard',                      '/images/Keyboard.png'),
    ('Harry Potter and the Philosopher''s Stone', '/images/Harry Potter.png'),
    ('Star Wars Trilogy',                        '/images/Star Wars.png'),
    ('The Lord of the Rings Trilogy',            '/images/Lord of the Rings.png'),
    ('Call of Duty: Vanguard',                   '/images/Call of Duty.png')
) AS v (name, image)
WHERE p.name = v.name;
