package dk.digitalidentity.service;

import java.security.Key;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.ModelAndView;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.config.oidc.OidcJWKSource;
import dk.digitalidentity.controller.dto.LoginRequest;
import dk.digitalidentity.service.serviceprovider.ServiceProvider;
import dk.digitalidentity.util.ResponderException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class EntraMfaService {
	private Key key = null;
	
	@Autowired
	private SessionHelper sessionHelper;
	
	@Autowired
	private OS2faktorConfiguration os2faktorConfiguration;
	
	@Autowired
	private AuditLogger auditLogger;
	
	@Autowired
	private OidcJWKSource oidcJWKSource;

	@SuppressWarnings("deprecation")
	public ModelAndView createAndSendIdToken(HttpServletResponse httpServletResponse, Person person, ServiceProvider serviceProvider, LoginRequest loginRequest) throws ResponderException {
		if (log.isDebugEnabled()) {
			log.debug("Creating OIDC ID Token");
		}

		// perform cleanup
		sessionHelper.setInEntraMfaFlow(null, null);

		if (loginRequest == null) {
			throw new ResponderException("Ugyldig tilstand - ingen loginRequest på sessionen");
		}
		else if (loginRequest.getEntraPayload() == null) {
			throw new ResponderException("Ugyldig tilstand - ingen Entra Payload på sessionen");
		}
		
		Map<String, Object> attributes = serviceProvider.getAttributes(loginRequest, person, false);
		attributes.put("nonce", loginRequest.getEntraPayload().getNonce());
		attributes.put("amr", Collections.singletonList("otp"));
		
		if (StringUtils.hasText(loginRequest.getEntraPayload().getAcr())) {
			attributes.put("acr", loginRequest.getEntraPayload().getAcr());
		}
		else {
			attributes.put("acr", "possessionorinherence");
		}

		Key key = getKey();
		
		Date now = new Date();
		String jwt = Jwts.builder()
			.subject(loginRequest.getEntraPayload().getSubject())
			.issuedAt(now)
			.issuer(os2faktorConfiguration.getEntityId())
			.audience()
				.add(loginRequest.getEntraPayload().getAudience())
				.and()
			.expiration(new Date(now.getTime() + 10 * 60 * 1000))
			.claims(attributes)
			// cannot find a non-deprecated version that works *sigh*
			.signWith(SignatureAlgorithm.RS256, key)
			.header()
				.keyId(OidcJWKSource.KEYID)
				.type("JWT")
				.and()
			.compact();

		Map<String, String> model = new HashMap<>();
		model.put("code", jwt);
		model.put("state", loginRequest.getEntraPayload().getState());
		model.put("url", loginRequest.getEntraPayload().getRedirectUrl());

		auditLogger.sentJWTIdToken(jwt, person, "EntraID");

		return new ModelAndView("entraMfa/success", model);
	}
	
	private Key getKey() throws ResponderException {
		if (key != null) {
			return key;
		}

		try {
			key = oidcJWKSource.getKeyPair().getPrivate();
		}
		catch (Exception ex) {
			log.error("Could not extract key for signing", ex);
			throw new ResponderException("Signature key unavailable");
		}

		return key;
	}
}
