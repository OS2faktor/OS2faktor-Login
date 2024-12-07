package dk.digitalidentity.config.oidc;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import dk.digitalidentity.config.oidc.logging.OAuth2ErrorHandlerAndLogger;

@Configuration
public class AuthorizationServerSecurityConfig {

	@Autowired
	private OAuth2ErrorHandlerAndLogger oAuth2ErrorHandlerAndLogger;

	@Autowired
	private OidcLogoutHandler oidcLogoutHandler;

	@Bean
	@Order(1)
	public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
		// This applies endpoint security (and CSRF) for the default OAuth2 endpoints:
		//	- OAuth2 Authorization endpoint
		//	- OAuth2 Device Authorization Endpoint
		//	- OAuth2 Device Verification Endpoint
		//	- OAuth2 Token endpoint
		//	- OAuth2 Token Introspection endpoint
		//	- OAuth2 Token Revocation endpoint
		//	- OAuth2 Authorization Server Metadata endpoint
		//	- JWK Set endpoint (only if JWKSource<SecurityContext> bean is present (it is))
		OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = new OAuth2AuthorizationServerConfigurer();
		authorizationServerConfigurer
				.tokenEndpoint(tokenEndpoint ->
						tokenEndpoint
								.errorResponseHandler(oAuth2ErrorHandlerAndLogger)
				)
				.authorizationEndpoint(authorizationEndpoint ->
						authorizationEndpoint
								.errorResponseHandler(oAuth2ErrorHandlerAndLogger));

		RequestMatcher endpointsMatcher = authorizationServerConfigurer.getEndpointsMatcher();
		http.securityMatcher(endpointsMatcher)
				.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
				.csrf(csrf -> csrf.ignoringRequestMatchers(endpointsMatcher))
				.apply(authorizationServerConfigurer);


		// This applies endpoint security (and CSRF) for the default OIDC endpoints:
		//	- OpenID Connect 1.0 Provider Configuration endpoint
		//	- OpenID Connect 1.0 Logout endpoint
		//	- OpenID Connect 1.0 UserInfo endpoint
		// Note:
		// 	- The Client Registration endpoint is disabled by default, and we do not want it enabled
		http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
				.oidc(oidcConfigurer ->
						oidcConfigurer.logoutEndpoint(logoutEndpoint ->
									logoutEndpoint
											.logoutResponseHandler(oidcLogoutHandler::onLogoutSuccess)
											.errorResponseHandler(oAuth2ErrorHandlerAndLogger)
								)
				);    // Enable OpenID Connect 1.0

		// Handles login if not authenticated.
		http
				// Redirect to the login page when not authenticated from the
				// authorization endpoint
				.exceptionHandling((exceptions) -> exceptions.defaultAuthenticationEntryPointFor(new LoginUrlAuthenticationEntryPoint("/oauth2/login"), new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
				// Accept access tokens for User Info and/or Client Registration
				.oauth2ResourceServer((resourceServer) -> resourceServer.jwt(Customizer.withDefaults()));

		return http.build();
	}
}
