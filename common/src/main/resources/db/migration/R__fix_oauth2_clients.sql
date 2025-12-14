-- created with 2025r5 - once everyone is running at least that, it can be removed

-- fix oauth2 clients - Duration is not in a different format

UPDATE oauth2_registered_client
SET client_settings = REGEXP_REPLACE(
    client_settings,
    '\\["java\\.time\\.Duration",([0-9]+)\\.0+\\]',
    '"PT\\1S"'
)
WHERE client_settings LIKE '%"java.time.Duration"%';

UPDATE oauth2_registered_client
SET token_settings = REGEXP_REPLACE(
    token_settings,
    '\\["java\\.time\\.Duration",([0-9]+)\\.0+\\]',
    '"PT\\1S"'
)
WHERE token_settings LIKE '%"java.time.Duration"%';

-- and we also need to do this afterwards - to swith these to long-bases Duration (god I hate it when they change the format from version to version)
UPDATE oauth2_registered_client 
SET token_settings = REGEXP_REPLACE(
    REGEXP_REPLACE(
        token_settings,
        '"settings\\.token\\.access-token-time-to-live":"(PT[^"]+)"',
        '"settings.token.access-token-time-to-live":["java.time.Duration","\\1"]'
    ),
    '"settings\\.token\\.refresh-token-time-to-live":"(PT[^"]+)"',
    '"settings.token.refresh-token-time-to-live":["java.time.Duration","\\1"]'
);
