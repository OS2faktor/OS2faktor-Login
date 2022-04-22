ALTER TABLE password_settings ADD COLUMN old_password_number BIGINT NOT NULL DEFAULT 10 AFTER disallow_old_passwords;

ALTER TABLE password_settings ADD COLUMN tries_before_lock_number BIGINT NOT NULL DEFAULT 5;
ALTER TABLE password_settings ADD COLUMN locked_minutes BIGINT NOT NULL DEFAULT 5;

ALTER TABLE password_settings ADD COLUMN max_password_changes_pr_day_enabled BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE password_settings ADD COLUMN max_password_changes_pr_day BIGINT NOT NULL DEFAULT 1;

ALTER TABLE password_settings ADD COLUMN can_not_change_password_enabled BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE password_settings ADD COLUMN can_not_change_password_group_id BIGINT NULL;
ALTER TABLE password_settings ADD CONSTRAINT fk_password_settings_groups_can_not_change_password_group FOREIGN KEY (can_not_change_password_group_id) REFERENCES groups(id) ON DELETE SET NULL;

ALTER TABLE persons ADD COLUMN daily_password_change_counter BIGINT NOT NULL DEFAULT 0;
ALTER TABLE persons_aud ADD COLUMN daily_password_change_counter BIGINT NULL;