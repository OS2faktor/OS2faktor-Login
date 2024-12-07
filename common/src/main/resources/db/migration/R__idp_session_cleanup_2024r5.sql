
-- clear sessions, as format has changed in new Spring version
delete from SESSION_IDP;
delete from SESSION_IDP_ATTRIBUTES;

-- rename class path, as the class has been moved
UPDATE oauth2_registered_client SET token_settings = REPLACE(token_settings, 'org.springframework.security.oauth2.core.OAuth2TokenFormat', 'org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat');
