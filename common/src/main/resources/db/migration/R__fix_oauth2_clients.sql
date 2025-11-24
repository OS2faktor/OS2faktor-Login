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
