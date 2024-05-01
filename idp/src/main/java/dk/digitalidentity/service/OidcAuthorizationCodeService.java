package dk.digitalidentity.service;

import java.io.IOException;
import java.security.Principal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletResponse;

import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.AttributeStatement;
import org.opensaml.saml.saml2.core.Subject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.keygen.Base64StringKeyGenerator;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.UriComponentsBuilder;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.controller.dto.LoginRequest;
import dk.digitalidentity.service.serviceprovider.ServiceProvider;
import dk.digitalidentity.util.ResponderException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class OidcAuthorizationCodeService {

	@Autowired
	private RegisteredClientRepository registeredClientRepository;

	@Autowired
	private OAuth2AuthorizationService authorizationService;

	@Autowired
	private AuditLogger auditLogger;

	@Autowired
	private SessionHelper sessionHelper;

	@Autowired
	private OpenSAMLHelperService samlHelper;

	public void createAndSendAuthorizationCode(HttpServletResponse httpServletResponse, Person person, ServiceProvider serviceProvider, LoginRequest loginRequest) throws ResponderException {
		if (log.isDebugEnabled()) {
			log.debug("Creating OIDC Authorization request response code");
		}

		Map<String, Object> attributes = serviceProvider.getAttributes(loginRequest, person, false);
		addNISTClaim(serviceProvider, attributes);
		
		OAuth2AuthorizationCodeRequestAuthenticationToken token = loginRequest.getToken();
		HashSet<String> authorizedScopes = new HashSet<>(token.getScopes());
		OAuth2AuthorizationCodeRequestAuthenticationToken successToken = createSuccessAuthentication(authorizedScopes, token, serviceProvider.getNameId(person), person.getId(), attributes);

		sendResponse(httpServletResponse, successToken, loginRequest, serviceProvider, person);
	}

	public ModelAndView createAndSendBrokeredAuthorizationCode(HttpServletResponse httpServletResponse, Assertion assertion, ServiceProvider serviceProvider, LoginRequest loginRequest) throws ResponderException {
		OAuth2AuthorizationCodeRequestAuthenticationToken token = loginRequest.getToken();
		HashSet<String> authorizedScopes = new HashSet<>(token.getScopes());

		Subject nemLogInSubject = assertion.getSubject();
		String subject = nemLogInSubject.getNameID().getValue();

		Map<String, Object> attributes = extractAttributesFromAssertion(assertion);
		addNISTClaim(serviceProvider, attributes);

		OAuth2AuthorizationCodeRequestAuthenticationToken successToken =  createSuccessAuthentication(authorizedScopes, token, subject, 0, attributes);

		return sendResponse(httpServletResponse, successToken, loginRequest, serviceProvider, null);
	}
	
	private void addNISTClaim(ServiceProvider serviceProvider, Map<String, Object> attributes) {
		if (serviceProvider.preferNIST()) {
			
			// value "2" if logged in with username/password and value "3" if logged in with 2-faktor
			String NISTValue = "2";
			if (sessionHelper.hasUsedMFA()) {
				NISTValue = "3";
			}
			
			// TODO: there is no official NIST attriubute :(
			attributes.put("loa", NISTValue);
		}		
	}

	private Map<String, Object> extractAttributesFromAssertion(Assertion nemLogInAssertion) {
		HashMap<String, Object> result = new HashMap<>();

		List<AttributeStatement> attributeStatements = nemLogInAssertion.getAttributeStatements();
		if (attributeStatements != null && !attributeStatements.isEmpty()) {
			AttributeStatement attributeStatement = attributeStatements.get(0);
			Map<String, String> attributes = samlHelper.extractAttributeValues(attributeStatement);

			// We only map some fields.
			if (attributes.containsKey("https://data.gov.dk/model/core/eid/fullName")) {
				result.put("name", attributes.get("https://data.gov.dk/model/core/eid/fullName"));
			}

			if (attributes.containsKey("https://data.gov.dk/model/core/eid/email")) {
				result.put("email", attributes.get("https://data.gov.dk/model/core/eid/email"));
			}

			if (attributes.containsKey("https://data.gov.dk/model/core/eid/cprNumber")) {
				result.put("cpr", attributes.get("https://data.gov.dk/model/core/eid/cprNumber"));
			}
		}

		return result;
	}

	public OAuth2AuthorizationCodeRequestAuthenticationToken createSuccessAuthentication(Set<String> authorizedScopes, OAuth2AuthorizationCodeRequestAuthenticationToken authorizationCodeRequestAuthentication, String nameId, long personId, Map<String, Object> attributes) throws ResponderException {
		OAuth2AuthorizationCode authorizationCode = generateAuthCode();

		if (!authorizedScopes.isEmpty()) {
			// 'openid' scope is auto-approved as it does not require consent
			authorizedScopes.add(OidcScopes.OPENID);
		}

		RegisteredClient registeredClient = registeredClientRepository.findByClientId(authorizationCodeRequestAuthentication.getClientId());
		if (registeredClient == null) {
			throw new ResponderException("No RegisteredClient found for provided ClientId"); // OAuth2ErrorCodes.INVALID_REQUEST, OAuth2ParameterNames.CLIENT_ID
		}

		// Check that the registered client has all the scopes that has been given
		Set<String> allowedScopes = registeredClient.getScopes();
		boolean authorizedScopesContainedDisallowedScopes = authorizedScopes.retainAll(allowedScopes); // Intersection between allowed scopes and authorized scopes
		if (authorizedScopesContainedDisallowedScopes) {
			log.warn("Some of the scopes that was authorized were not defined by the Registered client, they have been removed");
		}

		OAuth2AuthorizationRequest authorizationRequest = OAuth2AuthorizationRequest.authorizationCode()
				.authorizationUri(authorizationCodeRequestAuthentication.getAuthorizationUri())
				.clientId(registeredClient.getClientId())
				.redirectUri(authorizationCodeRequestAuthentication.getRedirectUri())
				.scopes(authorizedScopes)
				.state(authorizationCodeRequestAuthentication.getState())
				.additionalParameters(authorizationCodeRequestAuthentication.getAdditionalParameters())
				.build();

		// Get claims for the person, and set the computedClaims on the Authorization
		if (sessionHelper.isInSelectClaimsFlow()) {
			sessionHelper.setInSelectClaimsFlow(false);

			Map<String, String> selectedClaims = sessionHelper.getSelectedClaims();
			// Overwrite raw attributes with selected claims in case of SingleValueOnly=True in the SP Config for some field
			attributes.putAll(selectedClaims);
		}


		// TODO Find a way to make this code better, this is not exactly pretty code
		User user = new User(nameId, "INTENTIONALLY_NOT_BLANK", new HashSet<>());
		user.eraseCredentials(); // Blank password
		UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(user, null, new HashSet<>());

		OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(registeredClient)
				.principalName(nameId)
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
				.attribute(Principal.class.getName(), authToken)
				.attribute(OAuth2AuthorizationRequest.class.getName(), authorizationRequest)
				.token(authorizationCode)
				.attribute(OAuth2Authorization.AUTHORIZED_SCOPE_ATTRIBUTE_NAME, authorizationCodeRequestAuthentication.getScopes())
				.attribute("InternalPersonId", String.valueOf(personId))
				.attribute("ComputedClaims", attributes)
				.build();
		authorizationService.save(authorization);

		String redirectUri = authorizationCodeRequestAuthentication.getRedirectUri();
		if (!StringUtils.hasText(redirectUri)) {
			 redirectUri = registeredClient.getRedirectUris().iterator().next();
		}

		Authentication principal = (Authentication) authorizationCodeRequestAuthentication.getPrincipal();
		OAuth2AuthorizationCodeRequestAuthenticationToken authorizationCodeRequestAuthenticationResult =
				OAuth2AuthorizationCodeRequestAuthenticationToken.with(authorizationCodeRequestAuthentication.getClientId(), principal)
						.authorizationUri(authorizationCodeRequestAuthentication.getAuthorizationUri())
						.redirectUri(redirectUri)
						.scopes(authorizedScopes)
						.state(authorizationCodeRequestAuthentication.getState())
						.authorizationCode(authorizationCode)
						.build();

		authorizationCodeRequestAuthenticationResult.setAuthenticated(true);

		return authorizationCodeRequestAuthenticationResult;
	}

	private ModelAndView sendResponse(HttpServletResponse httpServletResponse, OAuth2AuthorizationCodeRequestAuthenticationToken successToken, LoginRequest loginRequest, ServiceProvider serviceProvider, Person person) throws ResponderException {
		UriComponentsBuilder uriBuilder = UriComponentsBuilder
				.fromUriString(successToken.getRedirectUri())
				.queryParam(OAuth2ParameterNames.CODE, successToken.getAuthorizationCode().getTokenValue());

		if (StringUtils.hasText(successToken.getState())) {
			uriBuilder.queryParam(OAuth2ParameterNames.STATE, successToken.getState());
		}

		try {
			auditLogger.oidcAuthorizationRequestResponse(person, serviceProvider.getName(loginRequest));
			httpServletResponse.sendRedirect(uriBuilder.toUriString());
		}
		catch (IOException ex) {
			throw new ResponderException("Could not send Authorization Code via url redirect.", ex); // OAuth2ErrorCodes.SERVER_ERROR
		}
		
		return null;
	}

	private OAuth2AuthorizationCode generateAuthCode() {
		Instant issuedAt = Instant.now();
		Instant expiresAt = issuedAt.plus(5, ChronoUnit.MINUTES);
		return new OAuth2AuthorizationCode(new Base64StringKeyGenerator(Base64.getUrlEncoder().withoutPadding(), 96).generateKey(), issuedAt, expiresAt);
	}
}
