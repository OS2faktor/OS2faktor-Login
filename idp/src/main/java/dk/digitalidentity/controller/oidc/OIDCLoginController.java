package dk.digitalidentity.controller.oidc;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;

import dk.digitalidentity.controller.dto.LoginRequest;
import dk.digitalidentity.service.ErrorHandlingService;
import dk.digitalidentity.service.ErrorResponseService;
import dk.digitalidentity.service.LoginService;
import dk.digitalidentity.service.OidcAuthCodeRequestService;
import dk.digitalidentity.service.SessionHelper;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import dk.digitalidentity.util.ShowErrorToUserException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
public class OIDCLoginController {

	@Autowired
	private ErrorResponseService errorResponseService;

	@Autowired
	private ErrorHandlingService errorHandlingService;

	@Autowired
	private LoginService loginService;

	@Autowired
	private OidcAuthCodeRequestService oidcAuthCodeRequestService;

	@Autowired
	private SessionHelper sessionHelper;

	@GetMapping("/oauth2/login")
	public ModelAndView oidcLogin(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Model model) throws RequesterException, ResponderException, ShowErrorToUserException {
		if ("HEAD".equals(httpServletRequest.getMethod())) {
			log.warn("Rejecting HEAD request in login handler from " + getIpAddress(httpServletRequest) + "(" + httpServletRequest.getHeader("referer") + ")");
			return new ModelAndView("redirect:/");
		}

		sessionHelper.prepareNewLogin();

		try {
			LoginRequest loginRequest = null;
			try {
				// Extract OAuth2 Request from the HttpServletRequest
				OAuth2AuthorizationCodeRequestAuthenticationToken authorizationCodeRequestAuthentication = oidcAuthCodeRequestService.extractAuthRequestTokenFromHttpRequest(httpServletRequest);
				if (log.isDebugEnabled()) {
					log.debug("OAuth2AuthorizationCodeRequestAuthenticationToken extracted from request");
				}

				loginRequest = new LoginRequest(authorizationCodeRequestAuthentication, httpServletRequest.getHeader("User-Agent"));
				sessionHelper.setRequestedUsername(null);

				return loginService.loginRequestReceived(httpServletRequest, httpServletResponse, model, loginRequest);
			}
			catch (OAuth2AuthenticationException ex) {
				// Call Auth Fail if anything went wrong
				errorResponseService.sendOIDCError(httpServletResponse, null, new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST));
			}
			catch (RequesterException | ResponderException ex) {
				errorResponseService.sendError(httpServletResponse, loginRequest, ex);
			}
		}
		catch (IOException ex) {
			errorHandlingService.error("/oauth2/authorize", model);
			return null;
		}

		return null;
	}

	private String getIpAddress(HttpServletRequest request) {
		String remoteAddr = "";

		if (request != null) {
			remoteAddr = request.getHeader("X-FORWARDED-FOR");
			if (remoteAddr == null || "".equals(remoteAddr)) {
				remoteAddr = request.getRemoteAddr();
			}
		}

		return remoteAddr;
	}
}
