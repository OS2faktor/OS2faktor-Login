package dk.digitalidentity.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import dk.digitalidentity.common.dao.model.enums.RequirementCheckResult;
import org.opensaml.messaging.context.MessageContext;
import org.opensaml.saml.common.SAMLObject;
import org.opensaml.saml.common.messaging.context.SAMLBindingContext;
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
import dk.digitalidentity.common.dao.model.TemporaryClientSessionKey;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.TemporaryClientSessionKeyService;
import dk.digitalidentity.service.AuthnRequestHelper;
import dk.digitalidentity.service.AuthnRequestService;
import dk.digitalidentity.service.ErrorResponseService;
import dk.digitalidentity.service.LoginService;
import dk.digitalidentity.service.OpenSAMLHelperService;
import dk.digitalidentity.service.SessionHelper;
import dk.digitalidentity.service.serviceprovider.SelfServiceServiceProvider;
import dk.digitalidentity.service.serviceprovider.ServiceProvider;
import dk.digitalidentity.service.serviceprovider.ServiceProviderFactory;
import dk.digitalidentity.service.validation.AuthnRequestValidationService;
import dk.digitalidentity.util.Constants;
import dk.digitalidentity.util.LoggingUtil;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
public class LoginController {

	@Autowired
	private AuthnRequestService authnRequestService;

	@Autowired
	private AuthnRequestHelper authnRequestHelper;

	@Autowired
	private ErrorResponseService errorResponseService;

	@Autowired
	private AuthnRequestValidationService authnRequestValidationService;

	@Autowired
	private LoggingUtil loggingUtil;

	@Autowired
	private SessionHelper sessionHelper;

	@Autowired
	private ServiceProviderFactory serviceProviderFactory;

	@Autowired
	private LoginService loginService;

	@Autowired
	private PersonService personService;

	@Autowired
	private AuditLogger auditLogger;

	@Autowired
	private OpenSAMLHelperService samlHelper;

	@Autowired
	private TemporaryClientSessionKeyService temporaryClientSessionKeyService;

	@GetMapping("/sso/saml/login")
	public ModelAndView loginRequest(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Model model) throws ResponderException, RequesterException {
		if ("HEAD".equals(httpServletRequest.getMethod())) {
			log.warn("Rejecting HEAD request in login handler from " + getIpAddress(httpServletRequest) + "(" + httpServletRequest.getHeader("referer") + ")");
			return new ModelAndView("redirect:/");
		}

		// Clear any flow states in session so user does not end up in a bad state with a new AuthnRequest
		sessionHelper.clearFlowStates();

		// Get MessageContext (Including the saml object) from the HttpServletRequest
		MessageContext<SAMLObject> messageContext;
		try {
			messageContext = authnRequestService.getMessageContext(httpServletRequest);
		}
		catch (RequesterException ex) {
			// In case we cannot decode the message we have received just log a warning and redirect to the index page
			// (likely due to no actual authnRequest being sent because of bookmarking)
			log.warn("Could not Decode messageContext for /sso/saml/login", ex);
			return new ModelAndView("redirect:/");
		}

		AuthnRequest authnRequest = authnRequestService.getAuthnRequest(messageContext);
		if (authnRequest == null) {
			log.warn("No authnRequest found in request");
			return new ModelAndView("redirect:/");
		}

		try {
			loggingUtil.logAuthnRequest(authnRequest, Constants.INCOMING);

			authnRequestValidationService.validate(httpServletRequest, messageContext);

			SAMLBindingContext subcontext = messageContext.getSubcontext(SAMLBindingContext.class);
			String relayState = subcontext != null ? subcontext.getRelayState() : null;

			sessionHelper.saveIncomingAuthnRequest(authnRequest, relayState);

			boolean valid = sessionHelper.handleValidateIP();

			ServiceProvider serviceProvider = serviceProviderFactory.getServiceProvider(authnRequest);
			auditLogger.authnRequest(sessionHelper.getPerson(), samlHelper.prettyPrint(authnRequest), serviceProvider.getName(authnRequest));
			NSISLevel loginState = sessionHelper.getLoginState();

			// if forceAuthn and MFA required we need to force a new MFA auth
			if (authnRequest.isForceAuthn() && serviceProvider.mfaRequired(authnRequest)) {
				sessionHelper.setMFALevel(null);
			}

			if (authnRequest.isForceAuthn() || loginState == null || !valid) {
				return loginService.initiateLogin(model, httpServletRequest, serviceProvider.preferNemId());
			}

			// TODO: what happens if person is null?
			Person person = sessionHelper.getPerson();

			// if the SP requires NSIS LOW or above, extra checks required
			if (NSISLevel.LOW.equalOrLesser(serviceProvider.nsisLevelRequired(authnRequest))) {
				// is user even allowed to login to NSIS applications
				if (!person.isNsisAllowed()) {
					throw new ResponderException("Login afbrudt, da brugeren ikke er godkendt til NSIS login");
				}

				// has the user approved conditions?
				if (!person.isApprovedConditions()) {
					if (authnRequest.isPassive()) {
						throw new ResponderException("Kunne ikke gennemføre passivt login da brugeren ikke har accepteret vilkårene for brug");
					}
					else {
						return loginService.initiateApproveConditions(model);
					}
				}

				// has the user activated their NSIS User?
				if (person.isNsisAllowed() && !person.hasNSISUser()) {
					if (!authnRequest.isPassive()) {
						return loginService.initiateActivateNSISAccount(model);
					}
				}
			}

			// for non-selfservice service providers we have additional constraints
			if (!(serviceProvider instanceof SelfServiceServiceProvider)) {
				if (person.isLocked()) {
					if (authnRequest.isPassive()) {
						throw new ResponderException("Kunne ikke gennemføre passivt login da brugerens konto er låst");
					}
					else {
						return new ModelAndView("error-locked-account");
					}
				}
			}

			return loginService.initiateFlowOrCreateAssertion(model, httpServletResponse, httpServletRequest, person);
		}
		catch (RequesterException ex) {
			errorResponseService.sendError(httpServletResponse, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.REQUESTER, ex);
		}
		catch (ResponderException ex) {
			errorResponseService.sendError(httpServletResponse, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.RESPONDER, ex);
		}

		// we return a SAML Response with an error message instead
		return null;
	}

	@PostMapping(value = "/sso/saml/login")
	public ModelAndView login(Model model, @RequestParam Map<String, String> body, HttpServletResponse httpServletResponse, HttpServletRequest httpServletRequest) throws ResponderException, RequesterException {
		// Get User
		String username = body.get("username");
		String password = body.get("password");

		List<Person> people = loginService.getPeople(username);

		// If no match go back to login page
		if (people == null || people.size() == 0) {
			return loginService.initiateLogin(model, httpServletRequest, false, true, username);
		}

		// If more than one match go to select user page
		if (people.size() != 1) {
			sessionHelper.setPassword(password);
			return loginService.initiateUserSelect(model, people, false);
		}

		// If only one match
		Person person = people.get(0);
		sessionHelper.setPerson(person);

		// Get ServiceProvider
		AuthnRequest authnRequest = sessionHelper.getAuthnRequest();
		if (authnRequest == null) {
			log.warn("No authnRequest found on session");
			return new ModelAndView("redirect:/");
		}
		
		ServiceProvider serviceProvider = serviceProviderFactory.getServiceProvider(authnRequest);

		RequirementCheckResult meetsRequirementsResult = serviceProvider.personMeetsRequirements(person);
		if (!RequirementCheckResult.OK.equals(meetsRequirementsResult)) {
			auditLogger.loginRejectedByConditions(person, meetsRequirementsResult);
			ResponderException e = new ResponderException("Login afbrudt, da brugeren ikke opfylder kravene til denne tjenesteudbyder");
			errorResponseService.sendError(httpServletResponse, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.REQUESTER, e);
			return null;
		}

		if (authnRequest.isForceAuthn() && serviceProvider.mfaRequired(authnRequest)) {
			// Instead of just initialising mfa we just set it to null so login service can do that instead since it chooses between nemid or mfa login
			sessionHelper.setMFALevel(null);
		}

		// Check if locked
		if (person.isLocked()) {
			return new ModelAndView("error-locked-account");
		}

		// Initiate the password expired flow if necessary
		ModelAndView modelAndView = loginService.initiatePasswordExpired(person, model);
		if (modelAndView != null) {
			sessionHelper.setInPasswordExpiryFlow(true);
			sessionHelper.setPassword(password);
			return modelAndView;
		}

		// Check password
		if (!loginService.validPassword(body.get("password"), person)) {
			if (person.isLocked()) {
				return new ModelAndView("error-locked-account");
			}
			else {
				return loginService.initiateLogin(model, httpServletRequest, false, true, username);
			}
		}

		personService.correctPasswordAttempt(person, sessionHelper.isAuthenticatedWithADPassword());

		// If a person was manually activated, after successful password login they must change their password
		ModelAndView forceChangePassword = loginService.initiateForceChangePassword(person, model);
		if (forceChangePassword != null) {
			sessionHelper.setInForceChangePasswordFlow(true);
			return forceChangePassword;
		}

		// If the SP requires NSIS LOW or above, extra checks required
		if (NSISLevel.LOW.equalOrLesser(serviceProvider.nsisLevelRequired(authnRequest))) {
			if (!person.isNsisAllowed()) {
				ResponderException e = new ResponderException("Login afbrudt, da brugeren ikke er godkendt til NSIS login");
				errorResponseService.sendError(httpServletResponse, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.REQUESTER, e);
				return null;
			}

			// Has the user approved conditions?
			if (!person.isApprovedConditions()) {
					return loginService.initiateApproveConditions(model);
			}

			// Has the user activated their NSIS User?
			if (person.isNsisAllowed() && !person.hasNSISUser()) {
				if (sessionHelper.isAuthenticatedWithADPassword()) {
					sessionHelper.setPassword(body.get("password"));
				}
				return loginService.initiateActivateNSISAccount(model);
			}
		}

		return loginService.initiateFlowOrCreateAssertion(model, httpServletResponse, httpServletRequest, person);
	}

	// Hitting this endpoint is not intended, but if a user does it is likely due to hitting the back button in the login flow.
	// If the person logging in picked the wrong user and pressed back, they
	@GetMapping(value = "/sso/saml/login/multiple/accounts")
	public ModelAndView altLoginGet(Model model, @RequestParam Map<String, String> body, HttpServletResponse httpServletResponse, HttpServletRequest httpServletRequest) throws ResponderException, RequesterException {

		// Sanity checks, Has the user been through multiple account select in session, is the selected person one of the available people?
		List<Person> availablePeople = sessionHelper.getAvailablePeople();
		Person person = sessionHelper.getPerson();

		if (availablePeople != null && !availablePeople.isEmpty() && person != null && availablePeople.contains(person)) {
			return loginService.initiateUserSelect(model, availablePeople, sessionHelper.isAuthenticatedWithNemIdOrMitId());
		}
		else {
			AuthnRequest authnRequest = sessionHelper.getAuthnRequest();
			RequesterException ex = new RequesterException("Tilgik '/sso/saml/login/multiple/accounts'. Sessionen var ikke korrekt, kunne ikke fortsætte login. Prøv igen.");

			if (authnRequest != null) {
				errorResponseService.sendError(httpServletResponse, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.REQUESTER, ex);
				return null;
			}
			else {
				sessionHelper.invalidateSession();
				throw ex;
			}
		}
	}

	@PostMapping(value = "/sso/saml/login/multiple/accounts")
	public ModelAndView altLogin(Model model, @RequestParam Map<String, String> body, HttpServletResponse httpServletResponse, HttpServletRequest httpServletRequest) throws ResponderException, RequesterException {
		// This is the list of people the user was presented with, and matched what they logged in with
		// so the personid that is returned from the person select page should be among these
		List<Person> availablePeople = sessionHelper.getAvailablePeople();

		// Get User
		long id = Long.parseLong(body.get("personId"));
		Person person = null;
		for (Person availablePerson : availablePeople) {
			if (Objects.equals(id, availablePerson.getId())) {
				person = availablePerson;
				break;
			}
		}

		AuthnRequest authnRequest = sessionHelper.getAuthnRequest();
		if (authnRequest == null) {
			log.warn("No authnRequest found on session");
			return new ModelAndView("redirect:/");
		}

		if (person == null) {
			errorResponseService.sendError(httpServletResponse, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.REQUESTER, new RequesterException("Person prøvede at logge ind med bruger som de ikke havde adgang til"));
			return null;
		}

		sessionHelper.setPerson(person);
		// Person has now been determined

		// If the user started login by using nemid continue straight to nemid login
		if (sessionHelper.isAuthenticatedWithNemIdOrMitId()) {
			return loginService.continueNemIDLogin(model, httpServletResponse, httpServletRequest);
		}

		// If we are currently in the process of changing password go to that flow
		if (sessionHelper.isInPasswordChangeFlow()) {
			try {
				// if the person has not approved the conditions then do that first
				if (!person.isApprovedConditions()) {
					sessionHelper.setInChangePasswordFlowAndHasNotApprovedConditions(true);
					return loginService.initiateApproveConditions(model);
				}

				// if the user is allowed to activate their NSIS account an have not done so,
				// we should prompt first since they will not set their NSIS password without activating first
				if (person.isNsisAllowed() && !person.hasNSISUser()) {
					return loginService.initiateActivateNSISAccount(model, true);
				}

				return loginService.continueChangePassword(model);
			}
			catch (RequesterException e) {
				errorResponseService.sendError(httpServletResponse, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.REQUESTER, e);
			}
			
			return null;
		}

		// Get ServiceProvider
		ServiceProvider serviceProvider = serviceProviderFactory.getServiceProvider(authnRequest);

		RequirementCheckResult meetsRequirementsResult = serviceProvider.personMeetsRequirements(person);
		if (!RequirementCheckResult.OK.equals(meetsRequirementsResult)) {
			auditLogger.loginRejectedByConditions(person, meetsRequirementsResult);
			ResponderException e = new ResponderException("Login afbrudt, da brugeren ikke opfylder kravene til denne tjenesteudbyder");
			errorResponseService.sendError(httpServletResponse, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.REQUESTER, e);
			return null;
		}

		if (authnRequest.isForceAuthn() && serviceProvider.mfaRequired(authnRequest)) {
			// Instead of just initialising mfa we just set it to null so login service can do that instead since it chooses between nemid or mfa login
			sessionHelper.setMFALevel(null);
		}

		// Check if locked
		if (person.isLocked()) {
			return new ModelAndView("error-locked-account");
		}

		// If the SP requires NSIS LOW or above, extra checks required
		if (NSISLevel.LOW.equalOrLesser(serviceProvider.nsisLevelRequired(authnRequest))) {
			if (!person.isNsisAllowed()) {
				ResponderException e = new ResponderException("Login afbrudt, da brugeren ikke er godkendt til NSIS login");
				errorResponseService.sendError(httpServletResponse, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.REQUESTER, e);
				return null;
			}

			// Has the user approved conditions?
			if (!person.isApprovedConditions()) {
				return loginService.initiateApproveConditions(model);
			}

			// Has the user activated their NSIS User?
			if (person.isNsisAllowed() && !person.hasNSISUser()) {
				return loginService.initiateActivateNSISAccount(model);
			}
		}

		// Initiate the password expired flow if necessary
		ModelAndView modelAndView = loginService.initiatePasswordExpired(person, model);
		if (modelAndView != null) {
			sessionHelper.setInPasswordExpiryFlow(true);
			return modelAndView;
		}

		// Check password
		String password = sessionHelper.getPassword();

		if (!loginService.validPassword(password, person)) {
			if (person.isLocked()) {
				return new ModelAndView("error-locked-account");
			}
			else {
				return loginService.initiateLogin(model, httpServletRequest, false, true, "");
			}
		}

		personService.correctPasswordAttempt(person, sessionHelper.isAuthenticatedWithADPassword());

		// If a person was manually activated, after successful password login they must change their password
		ModelAndView forceChangePassword = loginService.initiateForceChangePassword(person, model);
		if (forceChangePassword != null) {
			sessionHelper.setInForceChangePasswordFlow(true);
			return forceChangePassword;
		}

		return loginService.initiateFlowOrCreateAssertion(model, httpServletResponse, httpServletRequest, person);
	}

	@GetMapping("/sso/saml/login/nemid")
	public ModelAndView nemIdOnly(HttpServletRequest httpServletRequest, Model model) {
		return loginService.initiateNemIDOnlyLogin(model, httpServletRequest, null);
	}

	@GetMapping("/sso/saml/login/forceChangePassword/continueLogin")
	public ModelAndView continueLoginAfterForceChangePassword(Model model, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ResponderException, RequesterException {
		AuthnRequest authnRequest = sessionHelper.getAuthnRequest();
		if (authnRequest == null) {
			log.warn("No authnRequest found on session");
			return new ModelAndView("redirect:/");
		}

		if (!sessionHelper.isInForceChangePasswordFlow()) {
			sessionHelper.clearSession();
			RequesterException ex = new RequesterException("Bruger tilgik en url de ikke havde adgang til, prøv igen");
			errorResponseService.sendError(httpServletResponse, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.REQUESTER, ex);
			return null;
		}

		sessionHelper.setInForceChangePasswordFlow(false); // allows only onetime access

		Person person = sessionHelper.getPerson();

		// Check if locked
		if (person.isLocked()) {
			return new ModelAndView("error-locked-account");
		}

		// Has the user approved conditions?
		if (!person.isApprovedConditions()) {
			return loginService.initiateApproveConditions(model);
		}

		// Has the user activated their NSIS User?
		if (person.isNsisAllowed() && !person.hasNSISUser()) {
			return loginService.initiateActivateNSISAccount(model);
		}

		// Reaching this endpoint the person have already authenticated with their manually activated password or NemID
		// so we just continue login flow from here
		return loginService.initiateFlowOrCreateAssertion(model, httpServletResponse, httpServletRequest, person);
	}

	@GetMapping("/sso/saml/login/continueLogin")
	public ModelAndView continueLoginWithoutChangePassword(Model model, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ResponderException, RequesterException {
		AuthnRequest authnRequest = sessionHelper.getAuthnRequest();
		if (authnRequest == null) {
			log.warn("No authnRequest found on session");
			return new ModelAndView("redirect:/");
		}

		if (!sessionHelper.isInPasswordExpiryFlow()) {
			sessionHelper.clearSession();
			RequesterException ex = new RequesterException("Bruger tilgik en url de ikke havde adgang til, prøv igen");
			errorResponseService.sendError(httpServletResponse, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.REQUESTER, ex);
			return null;
		}

		sessionHelper.setInPasswordExpiryFlow(false); // allows only onetime access

		Person person = sessionHelper.getPerson();

		// Check if locked
		if (person.isLocked()) {
			return new ModelAndView("error-locked-account");
		}

		// Has the user approved conditions?
		if (!person.isApprovedConditions()) {
			return loginService.initiateApproveConditions(model);
		}

		// Has the user activated their NSIS User?
		if (person.isNsisAllowed() && !person.hasNSISUser()) {
			return loginService.initiateActivateNSISAccount(model);
		}

		// Check password
		String password = sessionHelper.getPassword();
		sessionHelper.setPassword(null);

		if (!loginService.validPassword(password, person)) {
			if (person.isLocked()) {
				return new ModelAndView("error-locked-account");
			}
			else {
				return loginService.initiateLogin(model, httpServletRequest, false, true, "");
			}
		}

		return loginService.initiateFlowOrCreateAssertion(model, httpServletResponse, httpServletRequest, person);
	}

	@GetMapping("/sso/saml/login/cancel")
	public String cancelLogin(HttpServletResponse httpServletResponse) throws ResponderException, RequesterException {
		AuthnRequest authnRequest = sessionHelper.getAuthnRequest();
		if (authnRequest == null) {
			log.warn("No authnRequest found on session");
			return "redirect:/";
		}

		ResponderException ex = new ResponderException("Brugeren afbrød login flowet");
		errorResponseService.sendError(httpServletResponse, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.RESPONDER, ex, false, true);
		return null;
	}

	@GetMapping("/sso/saml/client/login")
	public String continueClientLogin(@RequestParam("sessionKey") String sessionKey) {
		// No real reason to have meaningful error messages on this endpoint.
		// It is only hit by headless programs running to establish sessions for users using the windows login credential provider

		if (sessionKey == null || !StringUtils.hasLength(sessionKey)) {
			return "redirect:/error";
		}

		TemporaryClientSessionKey temporaryClient = temporaryClientSessionKeyService.getBySessionKey(sessionKey);
		if (temporaryClient == null) {
			return "redirect:/error";
		}

		// The temporaryClient should have been created at most 5 minutes before calling this endpoint
		if (LocalDateTime.now().minusMinutes(5).isAfter(temporaryClient.getTts())) {
			return "redirect:/error";
		}

		// Everything checks out and we can set the person and the nsislevel on the session.
		// Note this will not lower their level if they have already established a higher NSIS level.
		sessionHelper.setPasswordLevel(temporaryClient.getNsisLevel());
		sessionHelper.setPerson(temporaryClient.getPerson());

		return "redirect:/";
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
