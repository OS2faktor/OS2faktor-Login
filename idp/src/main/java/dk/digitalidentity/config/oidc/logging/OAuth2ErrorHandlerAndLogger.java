package dk.digitalidentity.config.oidc.logging;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.WebAttributes;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
// This is a copy of final class OAuth2ErrorAuthenticationFailureHandler with our own logging added
public class OAuth2ErrorHandlerAndLogger implements AuthenticationFailureHandler {
	private final PasswordEncoder delegatingPasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

	@Autowired
	private AuthorizationServerSettings providerSettings;

	@Autowired
	private RegisteredClientRepository registeredClientRepository;

	/**
	 * Called when an authentication attempt fails.
	 *
	 * @param request   the request during which the authentication attempt occurred.
	 * @param response  the response.
	 * @param exception the exception which was thrown to reject the authentication
	 *                  request.
	 */
	@Override
	public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException authenticationException) throws IOException, ServletException {
		ServletServerHttpResponse httpResponse = new ServletServerHttpResponse(response);
		
		try {
			httpResponse.setStatusCode(HttpStatus.BAD_REQUEST);
	
			logFailure(request, response);
	
			if (authenticationException instanceof OAuth2AuthenticationException) {
				OAuth2Error error = ((OAuth2AuthenticationException) authenticationException).getError();
				log.warn(error.getDescription());
	
				request.getSession().setAttribute(WebAttributes.AUTHENTICATION_EXCEPTION, error);
				response.sendRedirect("/error");
			}
			else {
				if (log.isWarnEnabled()) {
					log.warn(AuthenticationException.class.getSimpleName() + " must be of type " + OAuth2AuthenticationException.class.getName() + " but was " + authenticationException.getClass().getName());
				}
			}
		}
		finally {
			try {
				httpResponse.close();
			}
			catch (Exception ignored) {
				;
			}
		}
	}

	private void logFailure(HttpServletRequest request, HttpServletResponse response) {
		HttpStatus httpStatus = HttpStatus.valueOf(response.getStatus());
		log.warn("OIDC request failed");

		// non-successful response, so we look for oidc parameters and log them for further debugging
		Map<String, String[]> parameterMap = request.getParameterMap();

		if (providerSettings.getOidcLogoutEndpoint().equals(request.getRequestURI())) {
			logLogoutRequest(request, parameterMap, httpStatus);
		}

		if (providerSettings.getAuthorizationEndpoint().equals(request.getRequestURI())) {
			logAuthorizationRequest(request, parameterMap, httpStatus);
		}

		if (providerSettings.getTokenEndpoint().equals(request.getRequestURI())) {
			logClientBackchannelInfo(request, parameterMap, httpStatus);
		}
	}

	private void logLogoutRequest(HttpServletRequest request, Map<String, String[]> parameterMap, HttpStatus httpStatus) {
		StringBuilder sb = new StringBuilder();
		sb.append("OIDC Logout Request: ").append("\n");
		for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
			sb.append(entry.getKey()).append("=");

			if (entry.getValue() != null && entry.getValue().length > 0) {
				sb.append(Arrays.toString(entry.getValue()));
			}
			else {
				sb.append("null");
			}
			sb.append("\n");
		}
		log.warn(sb.toString());
	}

	private void logAuthorizationRequest(HttpServletRequest request, Map<String, String[]> parameterMap, HttpStatus httpStatus) {
		StringBuilder sb = new StringBuilder();
		sb.append("OIDC Authorization Request: ").append("\n");
		for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
			sb.append(entry.getKey()).append("=");

			if (entry.getValue() != null && entry.getValue().length > 0) {
				sb.append(Arrays.toString(entry.getValue()));
			}
			else {
				sb.append("null");
			}
			sb.append("\n");
		}
		log.warn(sb.toString());
	}

	private void logClientBackchannelInfo(HttpServletRequest request, Map<String, String[]> parameterMap, HttpStatus httpStatus) {
		if (HttpMethod.POST.matches(request.getMethod())) {
			StringBuilder sb = new StringBuilder("Incoming token request failed: ");
			String[] grant_types = parameterMap.getOrDefault("grant_type", new String[] { "no grant_type supplied" });
			sb.append("Grant type: ").append("'").append(grant_types[0]).append("'");
			sb.append(", HttpStatus: ").append(httpStatus.toString());

			String authorization = request.getHeader("authorization");
			if (authorization != null && authorization.toUpperCase().startsWith("BASIC ")) {
				sb.append(", ").append("AuthType: 'client_secret_basic'");

				String[] s = authorization.split(" ");
				String auth = s[1];

				try {
					String decodedAuth = new String(Base64.getUrlDecoder().decode(auth), StandardCharsets.UTF_8);
					String[] split = decodedAuth.split(":");

					RegisteredClient client = registeredClientRepository.findByClientId(split[0]);
					if (client != null) {
						sb.append(", ").append("Client: '").append(client.getClientId()).append("'");
						sb.append(", ").append("Client_Secret: ")
								.append(delegatingPasswordEncoder.matches(split[1], client.getClientSecret())
										? "Matches"
										: "Non-matching");
					}
					else {
						sb.append(", unknown client with ID '").append(split[0]).append("'");

					}
				}
				catch (Exception e) {
					log.warn("Could not decode base64 encoded ClientId/ClientSecret combo");
				}
			}
			else if (parameterMap.containsKey("client_id") && parameterMap.containsKey("client_secret")) {
				String[] client_ids = parameterMap.get("client_id");
				String[] client_secrets = parameterMap.get("client_secret");

				if (client_ids.length == 1 && client_secrets.length == 1) {
					sb.append(", ").append("AuthType: 'client_secret_post'");

					RegisteredClient client = registeredClientRepository.findByClientId(client_ids[0]);
					if (client != null) {
						sb.append(", ").append("Client found: ").append(client.getClientId());
						sb.append(", ").append("ClientSecret: ").append(
								delegatingPasswordEncoder.matches(client_secrets[0], client.getClientSecret())
										? "Matches"
										: "Non-matching");
					}
					else {
						sb.append(", unknown client with ID '").append(client_ids[0]).append("'");
					}
				}
			}
			log.warn(sb.toString());
		}
	}

	/**
	 * Sets the {@link HttpMessageConverter} used for converting an {@link OAuth2Error} to
	 * an HTTP response.
	 * @param errorResponseConverter the {@link HttpMessageConverter} used for converting
	 * an {@link OAuth2Error} to an HTTP response
	 */
	public void setErrorResponseConverter(HttpMessageConverter<OAuth2Error> errorResponseConverter) {
		Assert.notNull(errorResponseConverter, "errorResponseConverter cannot be null");
	}
}
