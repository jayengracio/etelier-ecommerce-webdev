DELETE FROM users_cart   WHERE user_id = (SELECT id FROM users WHERE username = 'Guest');
DELETE FROM users_orders WHERE user_id = (SELECT id FROM users WHERE username = 'Guest');
DELETE FROM users WHERE username = 'Guest';
