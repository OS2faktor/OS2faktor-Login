package dk.digitalidentity.controller;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.mfa.MFAService;
import dk.digitalidentity.common.service.mfa.model.ClientType;
import dk.digitalidentity.common.service.mfa.model.MfaAuthenticationResponse;
import dk.digitalidentity.common.service.mfa.model.MfaClient;
import dk.digitalidentity.service.AuthnRequestHelper;
import dk.digitalidentity.service.ErrorHandlingService;
import dk.digitalidentity.service.ErrorResponseService;
import dk.digitalidentity.service.LoginService;
import dk.digitalidentity.service.SessionHelper;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import java.util.List;
import java.util.Objects;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.joda.time.DateTime;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.StatusCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.ModelAndView;
import org.thymeleaf.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

@Controller
@Slf4j
public class MultiFactorAuthenticationController {

	@Autowired
	private AuthnRequestHelper authnRequestHelper;

	@Autowired
	private ErrorHandlingService errorHandlingService;

	@Autowired
	private ErrorResponseService errorResponseService;

	@Autowired
	private MFAService mfaService;

	@Autowired
	private SessionHelper sessionHelper;

	@Autowired
	private LoginService loginService;
	
	@Autowired
	private CommonConfiguration configuration;
	
	@Autowired
	private AuditLogger auditlogger;

	@GetMapping("/sso/saml/mfa/{deviceId}")
	public String mfaChallengePage(HttpServletResponse response, HttpServletRequest httpServletRequest, Model model, @PathVariable("deviceId") String deviceId) throws ResponderException, RequesterException {
		List<MfaClient> mfaClients = sessionHelper.getMFAClients();
		if (mfaClients == null) {
			String message = "Kunne ikke hente 2-faktor enheder, valgt deviceId: " + deviceId;

			AuthnRequest authnRequest = sessionHelper.getAuthnRequest();
			if (authnRequest != null) {
				errorResponseService.sendError(response, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.RESPONDER, new ResponderException(message));
				return null;
			}
			else {
				// Probably a bookmarking error
				String destination = errorHandlingService.error("/sso/saml/mfa/{deviceId}", httpServletRequest, "No MFA Clients on session", model);
				sessionHelper.invalidateSession();
				return destination;
			}
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
			model.addAttribute("wakeEvent", ClientType.CHROME.equals(matchingClient.getType()) || ClientType.EDGE.equals(matchingClient.getType()));
		}
		else {
			model.addAttribute("redirectUrl", redirectUrl);
		}
		
		model.addAttribute("deviceId", deviceId);
		model.addAttribute("os2faktorBackend", configuration.getMfa().getBaseUrl());

		return "login-mfa-challenge";
	}

	@GetMapping("/sso/saml/mfa/{deviceId}/completed")
	public ModelAndView mfaChallengeDone(Model model, HttpServletRequest request, HttpServletResponse response, @PathVariable("deviceId") String deviceId) throws ResponderException, RequesterException {
		Person person = sessionHelper.getPerson();
		if (sessionHelper.getLoginState() == null || person == null) {
			ModelAndView modelAndView = errorHandlingService.modelAndViewError("/sso/saml/mfa/deviceId/completed", request, "Bruger tilgik 2-faktor login uden at have gennemført brugernavn og kodeord login", model);
			sessionHelper.invalidateSession();
			return modelAndView;
		}

		AuthnRequest authnRequest = sessionHelper.getAuthnRequest();
		if (authnRequest == null) {
			log.warn("No authnRequest found on session");
			return new ModelAndView("redirect:/");
		}

		String subscriptionKey = sessionHelper.getSubscriptionKey();
		MfaClient selectedMFAClient = sessionHelper.getSelectedMFAClient();

		// clear session for next login attempt
		sessionHelper.setSubscriptionKey(null);
		sessionHelper.setSelectedMFAClient(null);
		
		if (subscriptionKey == null || selectedMFAClient == null || !Objects.equals(selectedMFAClient.getDeviceId(), deviceId)) {
			errorResponseService.sendError(response, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.RESPONDER, new RequesterException("Fejl i 2-faktor login"));
			return null;
		}

		if (!mfaService.isAuthenticated(subscriptionKey, person)) {
			errorResponseService.sendError(response, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.REQUESTER, new RequesterException("2-faktor login ikke gennemført. Person uuid: " + person.getUuid()));
			auditlogger.rejectedMFA(person);
			return null;
		}

		auditlogger.acceptedMFA(person, selectedMFAClient);

		sessionHelper.setMFALevel(selectedMFAClient.getNsisLevel());
		sessionHelper.setAuthnInstant(new DateTime());

		return loginService.initiateFlowOrCreateAssertion(model, response, request, person);
	}
}
