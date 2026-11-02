-- T-080: Victim@x and victim@x must be one account. The app now stores emails lower-case;
-- existing rows are lowered here, and the index keeps a write that skips normalization from creating a twin.
-- Fails (unique violation) if two accounts differ only by case: resolve those by hand before deploying. Find them with
--   SELECT lower(email), count(*) FROM users GROUP BY 1 HAVING count(*) > 1;
-- Google accounts keep username = email (EmailChangeService); lower those usernames too so they stay in step.
UPDATE users SET username = lower(username)
WHERE provider = 'GOOGLE' AND lower(username) = lower(email) AND username <> lower(username);
UPDATE users SET email = lower(email) WHERE email <> lower(email);
UPDATE edit_profile SET email = lower(email) WHERE email <> lower(email);
UPDATE onboarding_flows SET email = lower(email) WHERE email <> lower(email);
CREATE UNIQUE INDEX users_email_lower_key ON users (lower(email));
