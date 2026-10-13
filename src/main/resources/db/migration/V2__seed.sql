-- Seed data, moved from the old data.sql.

INSERT INTO product (type, name, stock, details, price) VALUES
    ('food',        'Apple',                 10,  'A red apple',                      10.00),
    ('electronics', 'Laptop',                10,  'A white laptop',                   500.00),
    ('food',        'Banana',                 10,  'A banana',                         3.00),
    ('food',        'Grapes',                20,  'Some grapes',                      3.00),
    ('electronics', 'Computer',              13,  'A computer',                       899.99),
    ('book',        'Harry Potter',          9,   'A Harry Potter Book',              40.00),
    ('food',        'Donut',                 132, 'A donut',                          9.50),
    ('food',        'Pizza',                 24,  'A scuffed pizza',                  14.00),
    ('electronics', 'Keyboard',              15,  'A broken keyboard',                55.00),
    ('film',        'Star Wars',             30,  'The Star Wars trilogy collection', 50.00),
    ('book',        'Lord of the Rings',     70,  'The first Lord of the Rings book', 35.99),
    ('music',       'After Hours',           45,  'His latest album from 2020',       45.50),
    ('video-games', 'Call of Duty',          99,  'The 2021 Call of Duty game',       59.99),
    ('electronics', 'Macbook Air',           70,  'An actual scam',                   1100.00),
    ('video-games', 'Red Dead Redemption 2', 20,  'Game of the Year 2018',            59.99);

-- Guest must be id 1: MainController uses findById(1) as the logged-out user.
-- TODO phase 2: plaintext passwords are replaced with hashes.
INSERT INTO users (id, username, password, owner) VALUES
    (1, 'Guest',  '',         FALSE),
    (2, 'owner',  'password', TRUE),
    (3, 'Phil',   '1234pass', FALSE),
    (4, 'Joseph', 'w0w',      FALSE);

-- Explicit ids above don't advance the identity sequence; move it past them.
SELECT setval(pg_get_serial_sequence('users', 'id'), (SELECT MAX(id) FROM users));
