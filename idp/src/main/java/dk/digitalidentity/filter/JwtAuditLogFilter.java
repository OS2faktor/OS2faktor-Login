package dk.digitalidentity.filter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.SqlServiceProviderConfiguration;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.SqlServiceProviderConfigurationService;
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
public class JwtAuditLogFilter extends OncePerRequestFilter {
	private AuditLogger auditLogger;
	private OAuth2AuthorizationService authorizationService;
	private PersonService personService;
	private SqlServiceProviderConfigurationService serviceProviderService;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

		// Code associated with authorization
		String code = request.getParameter(OAuth2ParameterNames.CODE);
		if (StringUtils.hasText(code)) {
			OAuth2Authorization token = authorizationService.findByToken(code, new OAuth2TokenType(OAuth2ParameterNames.CODE));

			if (token != null) {
				Object internalPersonId = token.getAttribute("InternalPersonId");

				if (internalPersonId != null && internalPersonId instanceof String) {
					String personId = (String) internalPersonId;
                    Person person = personService.getById(Long.parseLong(personId));

                    ContentCachingResponseWrapper responseCacheWrapperObject = new ContentCachingResponseWrapper(response);
					filterChain.doFilter(request, responseCacheWrapperObject);

					byte[] responseArray = responseCacheWrapperObject.getContentAsByteArray();
					String responseStr = new String(responseArray, responseCacheWrapperObject.getCharacterEncoding());
					String[] responseKeyValuePairs = responseStr.split(",");
					
					for (String keyValuePair : responseKeyValuePairs) {
						String[] splitKeyAndValue = keyValuePair.split(":");

						if (splitKeyAndValue.length == 2) {
							if (splitKeyAndValue[0].contains("id_token")) {

								String idToken = splitKeyAndValue[1];
								String[] idTokenParts = idToken.split("\\.");
								if (idTokenParts.length == 3) {
									SqlServiceProviderConfiguration sp = serviceProviderService.getByEntityId(token.getRegisteredClientId());
									
									byte[] decode = Base64.getUrlDecoder().decode(idTokenParts[1]);
									String idTokenBody = new String(decode, StandardCharsets.UTF_8);
									auditLogger.sentJWTIdToken(idTokenBody, person, (sp != null) ? sp.getName() : token.getRegisteredClientId());
								}
							}
						}
					}
					
					responseCacheWrapperObject.copyBodyToResponse();
				}
				else {
					log.error("No person associated with saved token: " + token.getId());
					response.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value(), "No person associated with saved token");
				}
			}
			else {
				log.warn("No Token found for authorization code: " + code);
				response.sendError(HttpStatus.NOT_FOUND.value(), "No Token found for authorization code");
			}
		}
		else {
			String refreshToken = request.getParameter(OAuth2ParameterNames.REFRESH_TOKEN);
			if (StringUtils.hasText(refreshToken)) {
				filterChain.doFilter(request, response);
			}
			else {
				log.warn("No code supplied");
				response.sendError(HttpStatus.BAD_REQUEST.value(), "No code supplied");
			}
		}
	}

	@Override
	public void destroy() {
		;
	}
}
