package dk.digitalidentity.filter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@Setter
public class OAuth2ClientAuthenticationErrorLoggingFilter extends OncePerRequestFilter {
	private RegisteredClientRepository registeredClientRepository;
	private PasswordEncoder delegatingPasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
		// first we need to see how the oidc framework responds to the request
		filterChain.doFilter(request, response);

		try {
			HttpStatus httpStatus = HttpStatus.valueOf(response.getStatus());
			
			if (!httpStatus.is2xxSuccessful()) {
				log.warn("OIDC request failed with HTTP Status: " + httpStatus.toString());

				// non-successful response, so we look for oidc parameters and log them for further debugging
				if (HttpMethod.POST.matches(request.getMethod())) {
					StringBuilder sb = new StringBuilder("Incoming token request failed: ");

					Map<String, String[]> parameterMap = request.getParameterMap();

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
		}
		catch (Exception ex) {
			log.warn("Failed in logging token request", ex);
		}
	}
}
