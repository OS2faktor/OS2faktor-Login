CREATE OR REPLACE VIEW view_mfa_login_history AS
  SELECT id, device_id, status, created_tts, push_tts, fetch_tts, response_tts, client_type, system_name, username
  FROM mfa_login_history;