-- The seed accounts. The products come from DummyJSON in V3__dummyjson_products.sql.
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
