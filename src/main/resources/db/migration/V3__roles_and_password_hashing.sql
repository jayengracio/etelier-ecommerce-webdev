-- replace the owner flag with a role
ALTER TABLE users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';
UPDATE users SET role = 'ADMIN' WHERE owner = true;
ALTER TABLE users DROP COLUMN owner;
ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('USER', 'ADMIN'));

-- hash the existing plain-text password with bcrypt
CREATE EXTENSION IF NOT EXISTS pgcrypto;
UPDATE users SET password = crypt(password, gen_salt('bf', 10)) WHERE password <> '';
