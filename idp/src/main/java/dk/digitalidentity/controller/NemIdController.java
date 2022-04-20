package dk.digitalidentity.controller;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.StatusCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.service.AuthnRequestHelper;
import dk.digitalidentity.service.ErrorResponseService;
import dk.digitalidentity.service.LoginService;
import dk.digitalidentity.service.NemIDService;
import dk.digitalidentity.service.SessionHelper;
import dk.digitalidentity.service.model.PidAndCprOrError;
import dk.digitalidentity.service.serviceprovider.SelfServiceServiceProvider;
import dk.digitalidentity.service.serviceprovider.ServiceProvider;
import dk.digitalidentity.service.serviceprovider.ServiceProviderFactory;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
public class NemIdController {

	@Autowired
	private NemIDService nemIDService;

	@Autowired
	private SessionHelper sessionHelper;

	@Autowired
	private ErrorResponseService errorResponseService;

	@Autowired
	private PersonService personService;

	@Autowired
	private AuthnRequestHelper authnRequestHelper;

	@Autowired
	private ServiceProviderFactory serviceProviderFactory;

	@Autowired
	private LoginService loginService;
	
	@Autowired
	private AuditLogger auditlogger;

	// Hitting this endpoint is not intended, but if a user does it is likely due to hitting the back button in the login flow.
	@GetMapping("/sso/saml/nemid")
	public ModelAndView loginGet(Model model, @RequestParam Map<String, String> map, HttpServletRequest request, HttpServletResponse response) throws Exception {

		// Check if state matches a user who has just posted to the nemID endpoint if it does continue flow
		Person person = sessionHelper.getPerson();
		String nemIDPid = sessionHelper.getNemIDPid();
		NSISLevel mfaLevel = sessionHelper.getMFALevel();
		NSISLevel passwordLevel = sessionHelper.getPasswordLevel();

		if (person != null && nemIDPid != null && NSISLevel.SUBSTANTIAL.equalOrLesser(passwordLevel) && NSISLevel.SUBSTANTIAL.equalOrLesser(mfaLevel)) {
			log.warn("Person ("+ person.getId() +") hit GET /sso/saml/nemid likely due to hitting the backbutton. Session is ok. resuming login");

			// if activation go to terms and conditions
			if (sessionHelper.isInDedicatedActivateAccountFlow()) {
				if (person.hasNSISUser() || !person.isNsisAllowed()) {
					sessionHelper.clearSession();
					return new ModelAndView("activateAccount/no-account-to-activate-error");
				}

				return loginService.initiateApproveConditions(model);
			}

			AuthnRequest authnRequest = sessionHelper.getAuthnRequest();
			ServiceProvider serviceProvider = serviceProviderFactory.getServiceProvider(authnRequest);

			// If trying to login to anything else than selfservice check if person is locked
			if (!(serviceProvider instanceof SelfServiceServiceProvider)) {
				if (person.isLocked()) {
					return new ModelAndView("error-locked-account");
				}
			}

			return loginService.initiateFlowOrCreateAssertion(model, response, request, person);
		}
		else {
			AuthnRequest authnRequest = sessionHelper.getAuthnRequest();

			if (authnRequest != null) {
				errorResponseService.sendError(response, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.REQUESTER, new RequesterException("Tilgik '/sso/saml/nemid'. Sessionen var ikke korrekt, kan ikke fortsætte login. Prøv igen."));
				return null;
			}
			else {
				return invalidateSessionAndSendRedirect();
			}
		}
	}

	@PostMapping("/sso/saml/nemid")
	public ModelAndView loginPost(Model model, @RequestParam Map<String, String> map, HttpServletRequest request, HttpServletResponse response) throws Exception {
		String responseB64 = map.get("response");
		PidAndCprOrError result = nemIDService.verify(responseB64, request);

		AuthnRequest authnRequest = sessionHelper.getAuthnRequest();

		// Check if NemID was successful
		if (result.hasError()) {
			if (authnRequest == null) {
				return invalidateSessionAndSendRedirect();
			}

			// authnRequest present, redirect there instead
			sessionHelper.clearSession();
			errorResponseService.sendError(response, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.RESPONDER,
					new ResponderException(result.getError()));

			return null;
		}
		sessionHelper.setNemIDPid(result.getPid());

		// Check if we have a person on the session, if we do we will only work with this person
		Person person = sessionHelper.getPerson();

		// If null treat as fresh login
		if (person == null) {
			List<Person> people = personService.getByCpr(result.getCpr());
			
			if (people == null || people.size() == 0) {
				auditlogger.usedNemID(result.getPid(), null);
				auditlogger.rejectedUnknownPerson(result.getPid());
				if (authnRequest == null) {
					return invalidateSessionAndSendRedirect();
				}

				sessionHelper.clearSession();
				errorResponseService.sendError(response, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.RESPONDER,
						new ResponderException("Brugeren loggede ind med NemID, men er ikke kendt af systemet"));
				
				return null;
			}
			else if (people.size() != 1) {
				if (sessionHelper.isInDedicatedActivateAccountFlow()) {
					List<Person> peopleThatCanBeActivated = people.stream().filter(p -> !p.hasNSISUser() && p.isNsisAllowed()).collect(Collectors.toList());
					if (peopleThatCanBeActivated.size() == 1) {
						person = peopleThatCanBeActivated.get(0);
					} else {
						auditlogger.usedNemID(result.getPid(), null);
						return loginService.initiateUserSelect(model, people, true);
					}
				} else {
					auditlogger.usedNemID(result.getPid(), null);
					return loginService.initiateUserSelect(model, people, true);
				}
			}
			else {
				// We only have one person, and none on the session
				person = people.get(0);
			}
		}
		
		auditlogger.usedNemID(result.getPid(), person);

		// We already have a person on the session, we only accept the nemid login if the cpr matches what we have on session
		if (result.getCpr() == null || !Objects.equals(result.getCpr(), person.getCpr())) {
			if (authnRequest == null) {
				return invalidateSessionAndSendRedirect();
			}

			sessionHelper.clearSession();
			errorResponseService.sendError(response, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.RESPONDER,
					new ResponderException("Bruger foretog NemId login med et andet CPR end der allerede var gemt på sessionen"));

			return null;
		}

		// User has now been determined. set nsis level and continue checks on user
		return loginService.continueLoginWithMitIdOrNemId(person, NSISLevel.SUBSTANTIAL, authnRequest, request, response, model);
	}

	private ModelAndView invalidateSessionAndSendRedirect() {
		String redirectUrl = sessionHelper.getPasswordChangeSuccessRedirect();
		sessionHelper.invalidateSession();

		if (StringUtils.hasLength(redirectUrl)) {
			return new ModelAndView("redirect:" + redirectUrl);
		}

		log.warn("No authnRequest found on session, redirecting to index page");

		return new ModelAndView("redirect:/");
	}
}
