DELETE FROM ad_password_cache;
ALTER TABLE ad_password_cache ADD CONSTRAINT uc_password_cache UNIQUE (sam_account_name, domain_id);
