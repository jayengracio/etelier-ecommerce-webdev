-- Starter data for development and the demo.
-- Products follow the same rules as NewProductRequest and ProductEditForm: type <= 50 and
-- name <= 255 characters, details required and <= 1000, price NUMERIC(8,2) >= 0, stock >= 0.
-- A few products are at or below app.low-stock-threshold (5), one is out of stock and one is
-- hidden, so the dashboard's low-stock list and the hidden-product view have something to show.

INSERT INTO product (type, name, stock, details, price) VALUES
    -- food
    ('food', 'Apple', 120, 'A crisp red apple', 0.99),
    ('food', 'Banana', 150, 'A ripe banana', 0.49),
    ('food', 'Grapes', 40, 'A 500 g bunch of seedless green grapes', 3.49),
    ('food', 'Donut', 132, 'A glazed ring donut', 1.99),
    ('food', 'Pizza', 24, 'A 12-inch frozen margherita pizza', 6.50),
    ('food', 'Dark Chocolate Bar', 80, '100 g of 70% cocoa dark chocolate', 2.99),
    ('food', 'Ground Coffee', 3, '250 g of medium-roast ground coffee', 8.99),
    -- electronics
    ('electronics', 'Laptop', 10, '15-inch laptop with 16 GB of RAM and a 512 GB SSD', 899.00),
    ('electronics', 'Ultrabook', 7, '13-inch lightweight laptop with all-day battery life', 1099.00),
    ('electronics', 'Desktop Computer', 13, 'Tower PC with an 8-core CPU and 32 GB of RAM', 1299.99),
    ('electronics', 'Mechanical Keyboard', 15, 'Full-size mechanical keyboard with brown switches', 89.00),
    ('electronics', 'Wireless Mouse', 60, 'Ergonomic wireless mouse with a USB receiver', 24.99),
    ('electronics', '27-inch Monitor', 4, '27-inch 1440p IPS monitor at 144 Hz', 279.00),
    ('electronics', 'Noise-Cancelling Headphones', 25, 'Over-ear Bluetooth headphones with 30 hours of battery', 199.00),
    ('electronics', 'USB-C Charger', 90, '65 W USB-C wall charger', 39.99),
    -- books
    ('book', 'Harry Potter and the Philosopher''s Stone', 9, 'The first Harry Potter book, paperback', 12.99),
    ('book', 'The Fellowship of the Ring', 70, 'The first Lord of the Rings book, paperback', 14.99),
    ('book', 'Clean Code', 18, 'A handbook of agile software craftsmanship by Robert C. Martin', 39.99),
    ('book', 'Dune', 33, 'Frank Herbert''s science fiction classic, paperback', 11.99),
    ('book', 'The Pragmatic Programmer', 2, '20th anniversary edition, hardcover', 44.95),
    ('book', 'Atomic Habits', 45, 'James Clear''s guide to building good habits', 16.99),
    -- films
    ('film', 'Star Wars Trilogy', 30, 'The original Star Wars trilogy on Blu-ray', 49.99),
    ('film', 'The Godfather', 12, 'The 1972 classic on Blu-ray', 14.99),
    ('film', 'Spirited Away', 20, 'Studio Ghibli''s 2001 animated film on Blu-ray', 19.99),
    ('film', 'Inception', 15, 'Christopher Nolan''s 2010 thriller on 4K Blu-ray', 24.99),
    ('film', 'The Lord of the Rings Trilogy', 0, 'The extended editions on Blu-ray', 79.99),
    -- music
    ('music', 'After Hours', 45, 'The Weeknd''s 2020 album on vinyl', 34.99),
    ('music', 'Abbey Road', 22, 'The Beatles'' 1969 album on vinyl', 29.99),
    ('music', 'Random Access Memories', 14, 'Daft Punk''s 2013 album on double vinyl', 39.99),
    ('music', 'Kind of Blue', 30, 'Miles Davis''s 1959 jazz album on CD', 12.99),
    ('music', 'Rumours', 5, 'Fleetwood Mac''s 1977 album on vinyl', 27.99),
    -- video games
    ('video-games', 'Call of Duty: Vanguard', 99, 'The 2021 first-person shooter', 59.99),
    ('video-games', 'Red Dead Redemption 2', 20, 'Rockstar''s open-world western, Game of the Year 2018', 39.99),
    ('video-games', 'The Legend of Zelda: Breath of the Wild', 35, 'Open-world adventure for the Nintendo Switch', 59.99),
    ('video-games', 'Minecraft', 120, 'The block-building sandbox game', 29.99),
    ('video-games', 'Elden Ring', 28, 'FromSoftware''s 2022 action RPG', 49.99),
    ('video-games', 'Stardew Valley', 60, 'A relaxing farming and life sim', 14.99),
    -- home
    ('home', 'Ceramic Mug', 200, 'A 350 ml stoneware mug, dishwasher safe', 9.99),
    ('home', 'Desk Lamp', 40, 'LED desk lamp with adjustable brightness', 34.50),
    ('home', 'Throw Blanket', 35, 'A soft 130 x 170 cm fleece blanket', 24.99),
    ('home', 'Cast Iron Skillet', 18, 'A pre-seasoned 26 cm cast iron pan', 44.00),
    -- sports
    ('sports', 'Yoga Mat', 50, 'A 6 mm non-slip yoga mat with a carry strap', 29.99),
    ('sports', 'Football', 45, 'A size 5 match football', 24.99),
    ('sports', 'Adjustable Dumbbells', 6, 'A pair of dumbbells adjustable from 2 to 24 kg', 249.00),
    ('sports', 'Water Bottle', 110, 'A 750 ml insulated steel bottle', 19.99),
    ('sports', 'Tennis Racket', 12, 'A 300 g graphite racket for intermediate players', 89.99),
    -- clothing
    ('clothing', 'Hoodie', 40, 'A heavyweight cotton hoodie, unisex', 49.00),
    ('clothing', 'Running Shoes', 22, 'Lightweight running shoes with a cushioned sole', 119.95),
    ('clothing', 'Beanie', 65, 'A ribbed knit beanie, one size', 15.00);

-- Hidden (soft-deleted): admins still see it, customers don't.
INSERT INTO product (type, name, stock, details, price, active) VALUES
    ('electronics', 'Portable DVD Player', 8, 'A 10-inch portable DVD player (discontinued)', 69.99, FALSE);

-- Users follow the /register rules (username >= 3, password >= 8 characters).
-- Passwords are bcrypt hashes from BCryptPasswordEncoder (strength 10). Logins:
--   owner  / password   (ADMIN)
--   Phil   / 1234pass
--   Joseph / josephw0w
--   Maria  / maria2026
--   Kenji  / kenji2026
INSERT INTO users (username, password, role) VALUES
    ('owner',  '$2a$10$Pr9zPcYl4wekKRCQ34M.w.OtKReAv1buCO742MxIb9wYT6GTZnNmW', 'ADMIN'),
    ('Phil',   '$2a$10$nbDaaixAQIjNvVojBYWB1Ob.5WDjv1aviZmGewiRFqA96vx9gzPSq', 'USER'),
    ('Joseph', '$2a$10$G7OkiJEfRh1lQkBbi7YY7u9vKJkXKr9NVo/zNZDZmr32OS/vSgomC', 'USER'),
    ('Maria',  '$2a$10$0EbrNFePnKh.dThUYFOkwe/YpPCBEwB/zDmVXU1bGtWxu0kbUuwzW', 'USER'),
    ('Kenji',  '$2a$10$lRfb18z65pWk3fSroxswu.lMIDN8R0.9uS6ZSMPjVheJbuBm3QBme', 'USER');
