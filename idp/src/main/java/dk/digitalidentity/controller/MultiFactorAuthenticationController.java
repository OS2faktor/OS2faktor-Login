package dk.digitalidentity.controller;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.service.mfa.MFAService;
import dk.digitalidentity.common.service.mfa.model.MfaAuthenticationResponse;
import dk.digitalidentity.common.service.mfa.model.MfaClient;
import dk.digitalidentity.service.AuthnRequestHelper;
import dk.digitalidentity.service.ErrorResponseService;
import dk.digitalidentity.service.LoginService;
import dk.digitalidentity.service.SessionHelper;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import java.util.List;
import java.util.Objects;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.StatusCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.ModelAndView;
import org.thymeleaf.util.StringUtils;

@Controller
@Slf4j
public class MultiFactorAuthenticationController {

	@Autowired
	private AuthnRequestHelper authnRequestHelper;

	@Autowired
	private ErrorResponseService errorResponseService;

	@Autowired
	private MFAService mfaService;

	@Autowired
	private SessionHelper sessionHelper;

	@Autowired
	private LoginService loginService;

	@GetMapping("/sso/saml/mfa/{deviceId}")
	public String mfaChallengePage(HttpServletResponse response, HttpServletRequest httpServletRequest, Model model, @PathVariable("deviceId") String deviceId) throws ResponderException, RequesterException {
		// Fetch MFA Clients
		List<MfaClient> mfaClients = sessionHelper.getMFAClients();
		if (mfaClients == null) {
			ResponderException error = new ResponderException("Kunne ikke hente 2-faktor enheder");

			AuthnRequest authnRequest = sessionHelper.getAuthnRequest();
			if (authnRequest != null) {
				errorResponseService.sendError(response, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.RESPONDER, error);
			}
			else {
				log.error("No AuthnRequest on session, SP and error endpoint unknown", error);
			}
			return null;
		}

		// Find the matching client
		MfaClient matchingClient = null;
		for (MfaClient mfaClient : mfaClients) {
			if (deviceId != null && Objects.equals(deviceId, mfaClient.getDeviceId())) {
				matchingClient = mfaClient;
				break;
			}
		}
		

		if (matchingClient == null) {
			ResponderException error = new ResponderException("Den valgte 2-faktor enhed ikke fundet");

			AuthnRequest authnRequest = sessionHelper.getAuthnRequest();
			if (authnRequest != null) {
				errorResponseService.sendError(response, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.RESPONDER, error);
			}
			else {
				log.error("No AuthnRequest on session, SP and error endpoint unknown", error);
			}
			return null;
		}

		// Start mfa authentication
		MfaAuthenticationResponse mfaResponse = mfaService.authenticate(matchingClient.getDeviceId());
		sessionHelper.setSelectedMFAClient(matchingClient);
		sessionHelper.setSubscriptionKey(mfaResponse.getSubscriptionKey());

		// Show challenge page
		model.addAttribute("pollingKey", mfaResponse.getPollingKey());
		String redirectUrl = mfaResponse.getRedirectUrl();
		if (StringUtils.isEmpty(redirectUrl)) {
			model.addAttribute("challenge", mfaResponse.getChallenge());
		}
		else {
			model.addAttribute("redirectUrl", redirectUrl);
		}

		return "login-mfa-challenge";
	}

	@PostMapping("/sso/saml/mfa/{deviceId}")
	public ModelAndView mfaChallengeDone(Model model, HttpServletRequest request, HttpServletResponse response) throws ResponderException, RequesterException {
		Person person = sessionHelper.getPerson();
		if (sessionHelper.getLoginState() == null || person == null) {
			throw new ResponderException("Bruger tilgik 2-faktor login uden at have gennemført brugernavn og kodeord login");
		}
		AuthnRequest authnRequest = sessionHelper.getAuthnRequest();

		MfaClient selectedMFAClient = sessionHelper.getSelectedMFAClient();
		if (selectedMFAClient == null) {
			errorResponseService.sendError(response, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.RESPONDER,
					new RequesterException("Fejl i 2-faktor login"));
			return null;
		}

		if (!mfaService.isAuthenticated(sessionHelper.getSubscriptionKey())) {
			errorResponseService.sendError(response, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.REQUESTER,
					new RequesterException("2-faktor login ikke gennemført"));
			return null;
		}

		sessionHelper.setMFALevel(selectedMFAClient.getNsisLevel());
		sessionHelper.setSelectedMFAClient(null);
		return loginService.initiateFlowOrCreateAssertion(model, response, request, person);
	}
}
