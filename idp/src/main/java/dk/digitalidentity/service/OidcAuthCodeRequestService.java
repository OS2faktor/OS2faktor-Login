package dk.digitalidentity.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.config.ProviderSettings;
import org.springframework.security.oauth2.server.authorization.web.authentication.OAuth2AuthorizationCodeRequestAuthenticationConverter;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;

@Service
public class OidcAuthCodeRequestService {
	private RequestMatcher authorizationEndpointMatcher;
	private final AuthenticationConverter authenticationConverter = new OAuth2AuthorizationCodeRequestAuthenticationConverter();

	@Autowired
	private ProviderSettings providerSettings;

	@PostConstruct
	public void postConstruct() {
		this.authorizationEndpointMatcher = createDefaultRequestMatcher(providerSettings.getAuthorizationEndpoint()); // default:  /oauth2/authorize
	}

	public boolean validRequest(HttpServletRequest request) {
		return this.authorizationEndpointMatcher.matches(request);
	}

	public OAuth2AuthorizationCodeRequestAuthenticationToken extractAuthRequestTokenFromHttpRequest(HttpServletRequest request) throws OAuth2AuthenticationException {
		Authentication authentication = this.authenticationConverter.convert(request);
		if (!(authentication instanceof OAuth2AuthorizationCodeRequestAuthenticationToken)) {
			throw new OAuth2AuthenticationException(OAuth2ErrorCodes.SERVER_ERROR);
		}
		
		return (OAuth2AuthorizationCodeRequestAuthenticationToken) authentication;
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
		stringBuilder.append("Consent: ").append(token.isConsent()).append("\n");
		stringBuilder.append("State: ").append(token.getState());
		
		return stringBuilder.toString();
	}
}
