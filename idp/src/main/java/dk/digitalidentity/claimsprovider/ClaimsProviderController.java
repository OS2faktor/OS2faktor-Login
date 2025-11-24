package dk.digitalidentity.claimsprovider;

import org.opensaml.messaging.context.MessageContext;
import org.opensaml.messaging.encoder.MessageEncodingException;
import org.opensaml.saml.common.SAMLObject;
import org.opensaml.saml.common.binding.SAMLBindingSupport;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.binding.encoding.impl.HTTPPostEncoder;
import org.opensaml.saml.saml2.binding.encoding.impl.HTTPRedirectDeflateEncoder;
import org.opensaml.saml.saml2.core.LogoutRequest;
import org.opensaml.saml.saml2.core.LogoutResponse;
import org.opensaml.saml.saml2.metadata.SingleLogoutService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;

import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.controller.dto.LoginRequest;
import dk.digitalidentity.samlmodule.model.TokenUser;
import dk.digitalidentity.service.ErrorResponseService;
import dk.digitalidentity.service.LogoutResponseService;
import dk.digitalidentity.service.OpenSAMLHelperService;
import dk.digitalidentity.service.SessionHelper;
import dk.digitalidentity.service.serviceprovider.ServiceProvider;
import dk.digitalidentity.service.serviceprovider.ServiceProviderFactory;
import dk.digitalidentity.util.Constants;
import dk.digitalidentity.util.LoggingUtil;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.velocity.VelocityEngine;

@Slf4j
@Controller
public class ClaimsProviderController {

	@Autowired
	private AuditLogger auditLogger;

	@Autowired
	private SessionHelper sessionHelper;

	@Autowired
	private ErrorResponseService errorResponseService;

	@Autowired
	private ClaimsProviderUtil claimsProviderUtil;

	@Autowired
	private ServiceProviderFactory serviceProviderFactory;

	@Autowired
	private LogoutResponseService logoutResponseService;

	@Autowired
	private OpenSAMLHelperService samlHelper;

	@Autowired
	private LoggingUtil loggingUtil;
	
	@Autowired
	private MitIDService mitIDService;

	@Autowired
	private UniLoginService uniLoginService;

	@Autowired
	private OS2faktorConfiguration configuration;

	// not actually NemLog-in anymore, but a common entrypoint for 3rd party claims providers,
	// unfortunately this is part of metadata that is already provisioned, so here we are :)
	@GetMapping("/sso/saml/nemlogin/complete")
	public ModelAndView loginComplete(Model model, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws RequesterException, ResponderException {
		TokenUser tokenUser = claimsProviderUtil.getTokenUser();
		if (tokenUser == null) {
			return handleErrors(httpServletResponse, "Intet bruger-token modtaget efter afsluttet login");
		}
		
		if (configuration.getClaimsProvider().getMitIdEntityId().equals(tokenUser.getIssuer())) {
			return mitIDService.nemLogInComplete(model, httpServletRequest, httpServletResponse, tokenUser);
		}
		else if (configuration.getClaimsProvider().getStilEntityId().equals(tokenUser.getIssuer())) {
			return uniLoginService.uniLoginComplete(model, httpServletRequest, httpServletResponse, tokenUser);
		}

		return handleErrors(httpServletResponse, "Ukendt udsteder: " + tokenUser.getIssuer());
	}

	@GetMapping("/sso/saml/nemlogin/logout/complete")
	public String nemLogInLogout(Model model, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws RequesterException, ResponderException {

		// redirect to error page instead of logout
		if (sessionHelper.isInInsufficientNSISLevelFromMitIDFlow()) {
			sessionHelper.setInInsufficientNSISLevelFromMitIDFlow(false);
			return "error-mitid-insufficient-nsis-level";
		}

		// If there is no LogoutRequest on session, show IdP index.
		// this happens when logout is IdP initiated
		LogoutRequest logoutRequest = sessionHelper.getLogoutRequest();
		if (logoutRequest == null) {
			auditLogger.logout(sessionHelper.getPerson());
			sessionHelper.invalidateSession();
			return "redirect:/";
		}

		// Create LogoutResponse
		ServiceProvider serviceProvider = serviceProviderFactory.getServiceProvider(logoutRequest.getIssuer().getValue());
		SingleLogoutService logoutEndpoint = serviceProvider.getLogoutResponseEndpoint();

		String destination = StringUtils.hasLength(logoutEndpoint.getResponseLocation()) ? logoutEndpoint.getResponseLocation() : logoutEndpoint.getLocation();
		MessageContext<SAMLObject> messageContext = logoutResponseService.createMessageContextWithLogoutResponse(logoutRequest, destination, logoutEndpoint.getBinding(), serviceProvider);

		// Log to Console and AuditLog
		auditLogger.logoutResponse(sessionHelper.getPerson(), samlHelper.prettyPrint((LogoutResponse) messageContext.getMessage()), true, serviceProvider.getName(null));
		auditLogger.logout(sessionHelper.getPerson());
		loggingUtil.logLogoutResponse((LogoutResponse) messageContext.getMessage(), Constants.OUTGOING);

		// Set RelayState
		SAMLBindingSupport.setRelayState(messageContext, sessionHelper.getRelayState());

		// Logout Response is sent as the last thing after all LogoutRequests so delete
		// the remaining values
		sessionHelper.invalidateSession();

		// Deflating and sending the message
		try {
			if (SAMLConstants.SAML2_REDIRECT_BINDING_URI.equals(logoutEndpoint.getBinding())) {
				HTTPRedirectDeflateEncoder encoder = new HTTPRedirectDeflateEncoder();

				encoder.setMessageContext(messageContext);
				encoder.setHttpServletResponse(httpServletResponse);

				encoder.initialize();
				encoder.encode();
			}
			else if (SAMLConstants.SAML2_POST_BINDING_URI.equals(logoutEndpoint.getBinding())) {
				HTTPPostEncoder encoder = new HTTPPostEncoder();

				encoder.setHttpServletResponse(httpServletResponse);
				encoder.setMessageContext(messageContext);
				encoder.setVelocityEngine(VelocityEngine.newVelocityEngine());

				encoder.initialize();
				encoder.encode();
			}
		}
		catch (ComponentInitializationException | MessageEncodingException e) {
			throw new ResponderException("Kunne ikke sende logout svar (LogoutResponse)", e);
		}

		return null;
	}

	private ModelAndView invalidateSessionAndSendRedirect() {
		log.warn("No authnRequest found on session, redirecting to index page");
		return new ModelAndView("redirect:/");
	}

	private ModelAndView handleErrors(HttpServletResponse httpServletResponse, String errMsg) throws ResponderException, RequesterException {
		LoginRequest loginRequest = sessionHelper.getLoginRequest();
		if (loginRequest == null) {
			log.warn(errMsg);
			return invalidateSessionAndSendRedirect();
		}

		errorResponseService.sendError(httpServletResponse, loginRequest, new ResponderException(errMsg));
		return null;
	}
}
