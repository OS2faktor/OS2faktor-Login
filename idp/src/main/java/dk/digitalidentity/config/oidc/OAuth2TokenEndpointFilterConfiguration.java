package dk.digitalidentity.config.oidc;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.ClientSecretAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.JwtClientAssertionAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientCredentialsAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.PublicClientAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.ProviderSettings;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.web.OAuth2ClientAuthenticationFilter;
import org.springframework.security.oauth2.server.authorization.web.OAuth2TokenEndpointFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class OAuth2TokenEndpointFilterConfiguration {

	@Autowired
	private ProviderSettings providerSettings;

	@Autowired
	private OAuth2AuthorizationService authorizationService;

	@Autowired
	private OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator;

	@Autowired
	private RegisteredClientRepository registeredClientRepository;

	/**
	 * Configuration of the filter which will authenticate the client (ServiceProvider) calling us
	 */
	@Bean
	public FilterRegistrationBean<OAuth2ClientAuthenticationFilter> clientAuthenticationFilterConfig() {
		AuthenticationManager authenticationManager = getAuthenticationManager();
		OrRequestMatcher requestMatcher = getRequestMatcher();

		OAuth2ClientAuthenticationFilter clientAuthenticationFilter = new OAuth2ClientAuthenticationFilter(authenticationManager, requestMatcher);
		FilterRegistrationBean<OAuth2ClientAuthenticationFilter> filterRegistrationBean = new FilterRegistrationBean<>(clientAuthenticationFilter);
		filterRegistrationBean.addUrlPatterns(providerSettings.getTokenEndpoint()); 				// default: "/oauth2/token"
		filterRegistrationBean.addUrlPatterns(providerSettings.getTokenIntrospectionEndpoint()); 	// default: "/oauth2/introspect"
		filterRegistrationBean.addUrlPatterns(providerSettings.getTokenRevocationEndpoint()); 		// default: "/oauth2/revoke"
		filterRegistrationBean.setOrder(1210);

		return filterRegistrationBean;
	}

	/**
	 * Configuration of the filter that handles traffic to the /oauth2/token endpoint.
	 * This filter reads our authenticated users from the DB and generates a token based on what they got approved for at login-time
	 * It is not the users browser that access this endpoint, but rather the ServiceProvider through a back-channel
	 */
	@Bean
	public FilterRegistrationBean<OAuth2TokenEndpointFilter> tokenEndpointFilterConfig() {
		AuthenticationManager authenticationManager = getAuthenticationManager();

		// Create token endpoint filter with optional handlers/converters
		OAuth2TokenEndpointFilter tokenEndpointFilter = new OAuth2TokenEndpointFilter(authenticationManager, providerSettings.getTokenEndpoint());

		// Setup filter url pattern matching
		FilterRegistrationBean<OAuth2TokenEndpointFilter> filterRegistrationBean = new FilterRegistrationBean<>(tokenEndpointFilter);
		filterRegistrationBean.addUrlPatterns(providerSettings.getTokenEndpoint()); // default: "/oauth2/token"
		filterRegistrationBean.setOrder(3310);
		return filterRegistrationBean;
	}

	private AuthenticationManager getAuthenticationManager() {
		// Setup "Authentication providers"
		List<AuthenticationProvider> authenticationProviders = new ArrayList<>();
		authenticationProviders.add(new OAuth2AuthorizationCodeAuthenticationProvider(authorizationService, tokenGenerator));
		authenticationProviders.add(new OAuth2RefreshTokenAuthenticationProvider(authorizationService, tokenGenerator));
		authenticationProviders.add(new OAuth2ClientCredentialsAuthenticationProvider(authorizationService, tokenGenerator));
		// TODO fix password encoders are already populated in the provider but we probably want to exclude some of them
		authenticationProviders.add(new ClientSecretAuthenticationProvider(registeredClientRepository, authorizationService));
		authenticationProviders.add(new JwtClientAssertionAuthenticationProvider(registeredClientRepository, authorizationService));
		authenticationProviders.add(new PublicClientAuthenticationProvider(registeredClientRepository, authorizationService));

		// Setup AuthenticationManager that basically just picks between the providers above
		return new ProviderManager(authenticationProviders);
	}

	private OrRequestMatcher getRequestMatcher() {
		return new OrRequestMatcher(
				new AntPathRequestMatcher(
						providerSettings.getTokenEndpoint(),
						HttpMethod.POST.name()),
				new AntPathRequestMatcher(
						providerSettings.getTokenIntrospectionEndpoint(),
						HttpMethod.POST.name()),
				new AntPathRequestMatcher(
						providerSettings.getTokenRevocationEndpoint(),
						HttpMethod.POST.name()));
	}
}
