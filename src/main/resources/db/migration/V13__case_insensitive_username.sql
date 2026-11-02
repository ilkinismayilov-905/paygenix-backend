-- T-101: "Alice" and "alice" must not be two accounts. Usernames keep the case the user typed (display),
-- but uniqueness and lookups go through lower(username).
-- Fails (unique violation) if two accounts differ only by case: resolve those by hand before deploying. Find them with
--   SELECT lower(username), count(*), string_agg(username, ', ') FROM users GROUP BY 1 HAVING count(*) > 1;
CREATE UNIQUE INDEX users_username_lower_key ON users (lower(username));
