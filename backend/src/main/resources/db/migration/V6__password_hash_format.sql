-- T-E1-02, UC-04: passwords are stored as BCrypt hashes with the {bcrypt} prefix of Spring Security's
-- DelegatingPasswordEncoder. A bug that stores a raw password is rejected here. Another algorithm needs a new
-- migration that widens this check.
ALTER TABLE app_user ADD CONSTRAINT app_user_password_hash_format_ck
    CHECK (password_hash ~ '^\{bcrypt\}\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$');
