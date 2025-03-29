package dk.digitalidentity.controller;

import java.util.List;
import java.util.Objects;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.ModelAndView;
import org.thymeleaf.util.StringUtils;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.dto.MfaAuthenticationResponseDTO;
import dk.digitalidentity.common.service.mfa.MFAService;
import dk.digitalidentity.common.service.mfa.model.ClientType;
import dk.digitalidentity.common.service.mfa.model.MfaClient;
import dk.digitalidentity.controller.dto.LoginRequest;
import dk.digitalidentity.service.ErrorHandlingService;
import dk.digitalidentity.service.ErrorResponseService;
import dk.digitalidentity.service.FlowService;
import dk.digitalidentity.service.SessionHelper;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Controller
@Slf4j
public class MultiFactorAuthenticationController {

	@Autowired
	private ErrorHandlingService errorHandlingService;

	@Autowired
	private ErrorResponseService errorResponseService;

	@Autowired
	private MFAService mfaService;

	@Autowired
	private SessionHelper sessionHelper;

	@Autowired
	private FlowService flowService;
	
	@Autowired
	private CommonConfiguration configuration;
	
	@Autowired
	private AuditLogger auditlogger;
	
	@GetMapping("/sso/saml/mfa/{deviceId}")
	public ModelAndView mfaChallengePage(HttpServletResponse response, HttpServletRequest httpServletRequest, Model model, @PathVariable("deviceId") String deviceId) throws ResponderException, RequesterException {
		List<MfaClient> mfaClients = sessionHelper.getMFAClients();
		if (mfaClients == null) {
			String message = "Kunne ikke hente 2-faktor enheder, valgt deviceId: " + deviceId;

			LoginRequest loginRequest = sessionHelper.getLoginRequest();
			if (loginRequest != null) {
				errorResponseService.sendError(response, loginRequest, new ResponderException(message));
				return null;
			}
			else {
				// Probably a bookmarking error
				ModelAndView destination = errorHandlingService.modelAndViewError("/sso/saml/mfa/{deviceId}", httpServletRequest, "No MFA Clients on session", model);
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

			LoginRequest loginRequest = sessionHelper.getLoginRequest();
			if (loginRequest != null) {
				errorResponseService.sendError(response, loginRequest, error);
			}
			else {
				log.warn("No LoginRequest on session, SP and error endpoint unknown", error);
			}

			return null;
		}

		// start MFA authentication
		MfaAuthenticationResponseDTO mfaResponseDto = mfaService.authenticate(matchingClient.getDeviceId(), sessionHelper.isInPasswordlessMfaFlow());
		if (!mfaResponseDto.isSuccess()) {
			// Handle error in initialising MFA authentication
			log.warn("mfaResponse was null exception: " + mfaResponseDto.getFailureMessage());

			Person person = sessionHelper.getPerson();
			NSISLevel requiredNSISLevel = sessionHelper.getMFAClientRequiredNSISLevel();
			if (person != null && requiredNSISLevel != null) {
				return flowService.initiateMFA(model, person, requiredNSISLevel);
			}

			// Wrong state, show error instead of silently handling it
			LoginRequest loginRequest = sessionHelper.getLoginRequest();
			ResponderException error = new ResponderException("mfaResponse was null exception: " + mfaResponseDto.getFailureMessage());
			if (loginRequest != null) {
				errorResponseService.sendError(response, loginRequest, error);
			}
			else {
				log.warn("No LoginRequest on session, SP and error endpoint unknown", error);
			}

			return null;
		}

		sessionHelper.setSelectedMFAClient(matchingClient);
		sessionHelper.setSubscriptionKey(mfaResponseDto.getMfaAuthenticationResponse().getSubscriptionKey());

		// Show challenge page
		model.addAttribute("pollingKey", mfaResponseDto.getMfaAuthenticationResponse().getPollingKey());
		String redirectUrl = mfaResponseDto.getMfaAuthenticationResponse().getRedirectUrl();
		if (StringUtils.isEmpty(redirectUrl)) {
			model.addAttribute("challenge", mfaResponseDto.getMfaAuthenticationResponse().getChallenge());
			model.addAttribute("wakeEvent", ClientType.CHROME.equals(matchingClient.getType()) || ClientType.EDGE.equals(matchingClient.getType()));
		}
		else {
			model.addAttribute("redirectUrl", redirectUrl);
		}
		
		model.addAttribute("deviceId", deviceId);
		model.addAttribute("os2faktorBackend", configuration.getMfa().getBaseUrl());
		
		// TODO: make this configurable
		model.addAttribute("delayedLoginOnMobile", configuration.getMfa().isDelayedLoginOnMobile());

		return new ModelAndView("login-mfa-challenge", model.asMap());
	}

	@GetMapping("/sso/saml/mfa/{deviceId}/completed")
	public ModelAndView mfaChallengeDone(Model model, HttpServletRequest request, HttpServletResponse response, @PathVariable("deviceId") String deviceId) throws ResponderException, RequesterException {
		Person person = sessionHelper.getPerson();
		if (person == null) {
			ModelAndView modelAndView = errorHandlingService.modelAndViewError("/sso/saml/mfa/deviceId/completed", request, "Der er ingen bruger på sessionen", model);
			sessionHelper.invalidateSession();
			return modelAndView;
		}
		
		// we allow a null loginState for the entraMfaFlow only
		if (sessionHelper.getLoginState() == null && !sessionHelper.isInEntraMfaFlow() && !sessionHelper.isInPasswordlessMfaFlow()) {
			ModelAndView modelAndView = errorHandlingService.modelAndViewError("/sso/saml/mfa/deviceId/completed", request, "Bruger tilgik 2-faktor login uden at have gennemført brugernavn og kodeord login", model);
			sessionHelper.invalidateSession();
			return modelAndView;			
		}
		
		LoginRequest loginRequest = sessionHelper.getLoginRequest();
		if (loginRequest == null) {
			log.warn("No loginRequest found on session");
			return new ModelAndView("redirect:/");
		}

		String subscriptionKey = sessionHelper.getSubscriptionKey();
		MfaClient selectedMFAClient = sessionHelper.getSelectedMFAClient();

		// clear session for next login attempt
		sessionHelper.setSubscriptionKey(null);
		sessionHelper.setSelectedMFAClient(null);
		sessionHelper.setMFAClientRequiredNSISLevel(null);
		
		if (subscriptionKey == null || selectedMFAClient == null || !Objects.equals(selectedMFAClient.getDeviceId(), deviceId)) {
			errorResponseService.sendError(response, loginRequest, new RequesterException("Fejl i 2-faktor login"));
			return null;
		}

		if (!mfaService.isAuthenticated(subscriptionKey, person)) {
			errorResponseService.sendError(response, loginRequest, new RequesterException("2-faktor login ikke gennemført. Person uuid: " + person.getUuid()));
			auditlogger.rejectedMFA(person);
			return null;
		}

		auditlogger.acceptedMFA(person, selectedMFAClient);
		
		if (sessionHelper.isInPasswordlessMfaFlow()) {
			// set the NSIS level on the "password login" to the lowest of MFA and Person levels
			NSISLevel personLevel = person.getNsisLevel();
			NSISLevel mfaLevel = selectedMFAClient.getNsisLevel();
			if (personLevel.isGreater(mfaLevel)) {
				personLevel = mfaLevel;
			}

			sessionHelper.setPasswordLevel(personLevel);
			sessionHelper.setInPasswordlessMfaFlow(false, null);
		}

		sessionHelper.setMFALevel(selectedMFAClient.getNsisLevel());
		sessionHelper.setAuthnInstant(new DateTime());

		return flowService.initiateFlowOrSendLoginResponse(model, response, request, person);
	}
}
