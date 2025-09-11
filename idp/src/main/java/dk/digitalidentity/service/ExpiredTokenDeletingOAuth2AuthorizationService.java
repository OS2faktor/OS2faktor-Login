package dk.digitalidentity.service;

import java.util.HashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import dk.digitalidentity.config.OS2faktorConfiguration;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ExpiredTokenDeletingOAuth2AuthorizationService {
	private static final String REMOVE_UNUSED_TOKENS =
			"DELETE FROM oauth2_authorization" +
			"  WHERE authorization_code_issued_at < (DATE_SUB(CURDATE(), INTERVAL 7 DAY))" +
			"    AND access_token_expires_at IS NULL" +
			"  LIMIT 25000";

	private static final String REMOVE_EXPIRED_TOKENS =
			"DELETE FROM oauth2_authorization" +
			"  WHERE access_token_expires_at IS NOT NULL" +
			"    AND access_token_expires_at < (DATE_SUB(CURDATE(), INTERVAL 7 DAY))" +
			"    AND refresh_token_issued_at IS NULL" +
			"  LIMIT 25000";

	private static final String REMOVE_REFRESH_TOKENS =
			"DELETE FROM oauth2_authorization" +
			"  WHERE refresh_token_issued_at IS NOT NULL" +
			"    AND refresh_token_issued_at < (DATE_SUB(CURDATE(), INTERVAL {DAYS} DAY))" +
			"  LIMIT 25000";

	@Autowired
	private NamedParameterJdbcTemplate jdbcTemplate;
	
	@Autowired
	private OS2faktorConfiguration configuration;

	@Transactional // this is OK, isolated cleanup transaction
	public void removeExpiredAuthorizations() {
		log.info("Executing: " + REMOVE_EXPIRED_TOKENS);
		jdbcTemplate.update(REMOVE_EXPIRED_TOKENS, new HashMap<>());
	}

	@Transactional // this is OK, isolated cleanup transaction
	public void removeUnusedAuthorizations() {
		log.info("Executing: " + REMOVE_UNUSED_TOKENS);
		jdbcTemplate.update(REMOVE_UNUSED_TOKENS, new HashMap<>());
	}

	@Transactional // this is OK, isolated cleanup transaction
	public void removeExpiredRefreshTokens() {
		String refreshTokenQuery = REMOVE_REFRESH_TOKENS.replace("{DAYS}", configuration.getOidc().getCleanupRefreshTokensAfterDays());
		log.info("Executing: " + refreshTokenQuery);
		jdbcTemplate.update(refreshTokenQuery, new HashMap<>());
	}
}