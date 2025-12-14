-- Step 1: Add the new columns in their correct positions (we'll reorder later)
ALTER TABLE oauth2_authorization ADD COLUMN authorized_scopes_new varchar(1000) DEFAULT NULL AFTER authorization_grant_type;

-- Step 2: Copy data from old position to new position
UPDATE oauth2_authorization SET authorized_scopes_new = authorized_scopes;

-- Step 3: Drop the old authorized_scopes column (at position 25)
ALTER TABLE oauth2_authorization DROP COLUMN authorized_scopes;

-- Step 4: Rename the new column to the correct name
ALTER TABLE oauth2_authorization CHANGE COLUMN authorized_scopes_new authorized_scopes varchar(1000) DEFAULT NULL;

-- Step 5: Drop the oidc_id_token_claims column (doesn't exist in Spring Security 7.0)
ALTER TABLE oauth2_authorization DROP COLUMN oidc_id_token_claims;

-- Step 6: Change data types for user_code and device_code columns

ALTER TABLE oauth2_authorization MODIFY COLUMN authorization_code_value blob DEFAULT NULL;
ALTER TABLE oauth2_authorization MODIFY COLUMN user_code_value blob DEFAULT NULL;
ALTER TABLE oauth2_authorization MODIFY COLUMN user_code_metadata blob DEFAULT NULL;
ALTER TABLE oauth2_authorization MODIFY COLUMN device_code_value blob DEFAULT NULL;
ALTER TABLE oauth2_authorization MODIFY COLUMN device_code_metadata blob DEFAULT NULL;
