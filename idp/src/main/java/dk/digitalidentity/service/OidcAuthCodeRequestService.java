package dk.digitalidentity.service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponseType;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;

@Service
public class OidcAuthCodeRequestService {
	private RequestMatcher authorizationEndpointMatcher;

	@Autowired
	private AuthorizationServerSettings providerSettings;

	@PostConstruct
	public void postConstruct() {
		this.authorizationEndpointMatcher = createDefaultRequestMatcher(providerSettings.getAuthorizationEndpoint()); // default:  /oauth2/authorize
	}

	public boolean validRequest(HttpServletRequest request) {
		return this.authorizationEndpointMatcher.matches(request);
	}

	private static RequestMatcher createOidcRequestMatcher() {
		RequestMatcher postMethodMatcher = (request) -> "POST".equals(request.getMethod());
		RequestMatcher responseTypeParameterMatcher = (
				request) -> request.getParameter(OAuth2ParameterNames.RESPONSE_TYPE) != null;
		RequestMatcher openidScopeMatcher = (request) -> {
			String scope = request.getParameter(OAuth2ParameterNames.SCOPE);
			return StringUtils.hasText(scope) && scope.contains(OidcScopes.OPENID);
		};
		return new AndRequestMatcher(postMethodMatcher, responseTypeParameterMatcher, openidScopeMatcher);
	}

	private static void throwError(String errorCode, String parameterName) {
		throwError(errorCode, parameterName, DEFAULT_ERROR_URI);
	}

	private static void throwError(String errorCode, String parameterName, String errorUri) {
		OAuth2Error error = new OAuth2Error(errorCode, "OAuth 2.0 Parameter: " + parameterName, errorUri);
		throw new OAuth2AuthorizationCodeRequestAuthenticationException(error, null);
	}

	private static final String DEFAULT_ERROR_URI = "https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.2.1";

	private static final String PKCE_ERROR_URI = "https://datatracker.ietf.org/doc/html/rfc7636#section-4.4.1";

	private static final Authentication ANONYMOUS_AUTHENTICATION = new AnonymousAuthenticationToken("anonymous",
			"anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));


	public OAuth2AuthorizationCodeRequestAuthenticationToken extractAuthRequestTokenFromHttpRequest(HttpServletRequest request) throws OAuth2AuthenticationException {
//		Authentication authentication = this.authenticationConverter.convert(request);
//		if (!(authentication instanceof OAuth2AuthorizationCodeRequestAuthenticationToken)) {
//			throw new OAuth2AuthenticationException(OAuth2ErrorCodes.SERVER_ERROR);
//		}
//
//		return (OAuth2AuthorizationCodeRequestAuthenticationToken) authentication;

		SavedRequest savedRequest = (SavedRequest) request.getSession().getAttribute("SPRING_SECURITY_SAVED_REQUEST");
		if (savedRequest == null || savedRequest.getParameterMap() == null) {
			throwError(OAuth2ErrorCodes.INVALID_REQUEST, OAuth2ParameterNames.RESPONSE_TYPE);
		}

		Map<String, String[]> parameterMap = savedRequest.getParameterMap();

		HashMap<String, List<String>> stringListHashMap = new HashMap<>();
		for (Map.Entry<String, String[]> stringEntry : parameterMap.entrySet()) {
			stringListHashMap.put(stringEntry.getKey(), Arrays.asList(stringEntry.getValue()));
		}

		if (!"GET".equals(request.getMethod()) && !createOidcRequestMatcher().matches(request)) {
			return null;
		}

		MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
		parameters.putAll(stringListHashMap);

//		MultiValueMap<String, String> parameters = "GET".equals(request.getMethod())
//				? OAuth2EndpointUtils.getQueryParameters(request) : OAuth2EndpointUtils.getFormParameters(request);

		// response_type (REQUIRED)
		String responseType = parameters.getFirst(OAuth2ParameterNames.RESPONSE_TYPE);
		if (!StringUtils.hasText(responseType) || parameters.get(OAuth2ParameterNames.RESPONSE_TYPE).size() != 1) {
			throwError(OAuth2ErrorCodes.INVALID_REQUEST, OAuth2ParameterNames.RESPONSE_TYPE);
		}
		else if (!responseType.equals(OAuth2AuthorizationResponseType.CODE.getValue())) {
			throwError(OAuth2ErrorCodes.UNSUPPORTED_RESPONSE_TYPE, OAuth2ParameterNames.RESPONSE_TYPE);
		}

		String authorizationUri = request.getRequestURL().toString();

		// client_id (REQUIRED)
		String clientId = parameters.getFirst(OAuth2ParameterNames.CLIENT_ID);
		if (!StringUtils.hasText(clientId) || parameters.get(OAuth2ParameterNames.CLIENT_ID).size() != 1) {
			throwError(OAuth2ErrorCodes.INVALID_REQUEST, OAuth2ParameterNames.CLIENT_ID);
		}

		Authentication principal = SecurityContextHolder.getContext().getAuthentication();
		if (principal == null) {
			principal = ANONYMOUS_AUTHENTICATION;
		}

		// redirect_uri (OPTIONAL)
		String redirectUri = parameters.getFirst(OAuth2ParameterNames.REDIRECT_URI);
		if (StringUtils.hasText(redirectUri) && parameters.get(OAuth2ParameterNames.REDIRECT_URI).size() != 1) {
			throwError(OAuth2ErrorCodes.INVALID_REQUEST, OAuth2ParameterNames.REDIRECT_URI);
		}

		// scope (OPTIONAL)
		Set<String> scopes = null;
		String scope = parameters.getFirst(OAuth2ParameterNames.SCOPE);
		if (StringUtils.hasText(scope) && parameters.get(OAuth2ParameterNames.SCOPE).size() != 1) {
			throwError(OAuth2ErrorCodes.INVALID_REQUEST, OAuth2ParameterNames.SCOPE);
		}
		if (StringUtils.hasText(scope)) {
			scopes = new HashSet<>(Arrays.asList(StringUtils.delimitedListToStringArray(scope, " ")));
		}

		// state (RECOMMENDED)
		String state = parameters.getFirst(OAuth2ParameterNames.STATE);
		if (StringUtils.hasText(state) && parameters.get(OAuth2ParameterNames.STATE).size() != 1) {
			throwError(OAuth2ErrorCodes.INVALID_REQUEST, OAuth2ParameterNames.STATE);
		}

		// code_challenge (REQUIRED for public clients) - RFC 7636 (PKCE)
		String codeChallenge = parameters.getFirst(PkceParameterNames.CODE_CHALLENGE);
		if (StringUtils.hasText(codeChallenge) && parameters.get(PkceParameterNames.CODE_CHALLENGE).size() != 1) {
			throwError(OAuth2ErrorCodes.INVALID_REQUEST, PkceParameterNames.CODE_CHALLENGE, PKCE_ERROR_URI);
		}

		// code_challenge_method (OPTIONAL for public clients) - RFC 7636 (PKCE)
		String codeChallengeMethod = parameters.getFirst(PkceParameterNames.CODE_CHALLENGE_METHOD);
		if (StringUtils.hasText(codeChallengeMethod)
				&& parameters.get(PkceParameterNames.CODE_CHALLENGE_METHOD).size() != 1) {
			throwError(OAuth2ErrorCodes.INVALID_REQUEST, PkceParameterNames.CODE_CHALLENGE_METHOD, PKCE_ERROR_URI);
		}

		Map<String, Object> additionalParameters = new HashMap<>();
		parameters.forEach((key, value) -> {
			if (!key.equals(OAuth2ParameterNames.RESPONSE_TYPE) && !key.equals(OAuth2ParameterNames.CLIENT_ID)
					&& !key.equals(OAuth2ParameterNames.REDIRECT_URI) && !key.equals(OAuth2ParameterNames.SCOPE)
					&& !key.equals(OAuth2ParameterNames.STATE)) {
				additionalParameters.put(key, (value.size() == 1) ? value.get(0) : value.toArray(new String[0]));
			}
		});

		return new OAuth2AuthorizationCodeRequestAuthenticationToken(authorizationUri, clientId, principal, redirectUri,
				state, scopes, additionalParameters);
	}

	private static RequestMatcher createDefaultRequestMatcher(String authorizationEndpointUri) {
		RequestMatcher authorizationRequestGetMatcher = new AntPathRequestMatcher(authorizationEndpointUri, HttpMethod.GET.name());
		
		RequestMatcher authorizationRequestPostMatcher = new AntPathRequestMatcher(authorizationEndpointUri, HttpMethod.POST.name());
		
		RequestMatcher openidScopeMatcher = request -> {
			String scope = request.getParameter(OAuth2ParameterNames.SCOPE);
			return StringUtils.hasText(scope) && scope.contains(OidcScopes.OPENID);
		};
		
		RequestMatcher responseTypeParameterMatcher = request -> request.getParameter(OAuth2ParameterNames.RESPONSE_TYPE) != null;

		RequestMatcher authorizationRequestMatcher = new OrRequestMatcher(
				authorizationRequestGetMatcher,
				new AndRequestMatcher(authorizationRequestPostMatcher, responseTypeParameterMatcher, openidScopeMatcher));

		RequestMatcher authorizationConsentMatcher = new AndRequestMatcher(
				authorizationRequestPostMatcher, new NegatedRequestMatcher(responseTypeParameterMatcher));

		return new OrRequestMatcher(authorizationRequestMatcher, authorizationConsentMatcher);
	}

	public static String tokenToString(OAuth2AuthorizationCodeRequestAuthenticationToken token) {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("ClientId: ").append(token.getClientId()).append("\n");
		stringBuilder.append("AuthorizationUri: ").append(token.getAuthorizationUri()).append("\n");
		stringBuilder.append("RedirectUri: ").append(token.getRedirectUri()).append("\n");
		stringBuilder.append("Scopes: ").append(token.getScopes()).append("\n");
		stringBuilder.append("State: ").append(token.getState()).append("\n");
		stringBuilder.append("AdditionalParameters: ").append(token.getAdditionalParameters()).append("\n");
		
		// JAVA21 : no idea - consent fandtes ikke mere
//		stringBuilder.append("Consent: ").append(token.isConsent()).append("\n");
		stringBuilder.append("State: ").append(token.getState());
		
		return stringBuilder.toString();
	}
}
