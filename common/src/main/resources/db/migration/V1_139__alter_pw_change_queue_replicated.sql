ALTER TABLE password_change_queue ADD COLUMN azure_replicated BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE password_change_queue ADD COLUMN google_workspace_replicated BOOLEAN NOT NULL DEFAULT FALSE;