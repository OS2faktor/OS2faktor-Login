ALTER TABLE password_settings DROP COLUMN change_password_on_users_enabled;
ALTER TABLE password_settings DROP FOREIGN KEY fk_password_settings_groups;
ALTER TABLE password_settings DROP COLUMN change_password_on_users_group_id;