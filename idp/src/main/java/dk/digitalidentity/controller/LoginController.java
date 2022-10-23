package dk.digitalidentity.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import dk.digitalidentity.service.model.enums.PasswordValidationResult;
import org.joda.time.DateTime;
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

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.TemporaryClientSessionKey;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.dao.model.enums.RequirementCheckResult;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.TemporaryClientSessionKeyService;
import dk.digitalidentity.service.AuthnRequestHelper;
import dk.digitalidentity.service.AuthnRequestService;
import dk.digitalidentity.service.ErrorHandlingService;
import dk.digitalidentity.service.ErrorResponseService;
import dk.digitalidentity.service.LoginService;
import dk.digitalidentity.service.OpenSAMLHelperService;
import dk.digitalidentity.service.SessionHelper;
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
	private ErrorHandlingService errorHandlingService;

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

	@Autowired
	private CommonConfiguration commonConfiguration;

	@GetMapping("/sso/saml/login")
	public ModelAndView loginRequest(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Model model) throws ResponderException, RequesterException {
		if ("HEAD".equals(httpServletRequest.getMethod())) {
			log.warn("Rejecting HEAD request in login handler from " + getIpAddress(httpServletRequest) + "(" + httpServletRequest.getHeader("referer") + ")");
			return new ModelAndView("redirect:/");
		}

		// Clear any flow states in session so user does not end up in a bad state with a new AuthnRequest
		sessionHelper.clearFlowStates();

		// If no SAMLRequest is present, we cannot decode the message for obvious reasons
		if (!StringUtils.hasLength(httpServletRequest.getParameter("SAMLRequest"))) {
			log.warn("SAMLRequest was null or empty");
			return new ModelAndView("redirect:/");
		}

		// Get MessageContext (Including the saml object) from the HttpServletRequest
		MessageContext<SAMLObject> messageContext;
		try {
			messageContext = authnRequestService.getMessageContext(httpServletRequest);
		}
		catch (RequesterException ex) {
			// note!
			// I've added a hack to HttpRedirectDeflateDecoder so it attempts normal parsing first, and if it fails,
			// it will try to detect spaces in the payload, and if they exist, it will replace them with + signs,
			// and attempt once more - if we update OpenSAML, we need to port that fix
			log.warn("Failed to parse SAMLRequest = " + httpServletRequest.getParameter("SAMLRequest"));
			
			// In case we cannot decode the message we have received just log a warning
			// and redirect to the error page with an explanation of why bookmarking does not work
			// (likely due to no actual authnRequest being sent because of bookmarking)
			return errorHandlingService.modelAndViewError("/sso/saml/login", httpServletRequest, "Could not Decode messageContext", model);
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

			// a ServiceProvider can be configured to just proxy straight to NemLog-in, skipping the build-in IdP login mechanisms.
			// In this case we will always just forward the request, and ignore any existing sessions, as NemLog-in is required here
			// Start login flow against NemLog-in no matter the session
			if (commonConfiguration.getNemlogin().isBrokerEnabled() && serviceProvider.nemLogInBrokerEnabled()) {
				sessionHelper.setInNemLogInBrokerFlow(true);
				return new ModelAndView("redirect:/nemlogin/saml/login");
			}

			// if forceAuthn and MFA required we need to force a new MFA auth
			if (authnRequest.isForceAuthn() && serviceProvider.mfaRequired(authnRequest)) {
				sessionHelper.setMFALevel(null);
			}

			// if forceAuthn is required,
			// you're not logged in,
			// or you're accessing the service from a new ip regardless of previous authentication
			// you will be asked login
			if (authnRequest.isForceAuthn() || loginState == null || !valid) {
				if (authnRequest.isPassive()) {
					throw new ResponderException("Kunne ikke gennemføre passivt login da brugeren ikke har accepteret vilkårene for brug");
				}
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
				if (loginService.requireApproveConditions(person)) {
					if (authnRequest.isPassive()) {
						throw new ResponderException("Kunne ikke gennemføre passivt login da brugeren ikke har accepteret vilkårene for brug");
					}
					else {
						return loginService.initiateApproveConditions(model);
					}
				}

				// has the user activated their NSIS User?
				if (person.isNsisAllowed() && !person.hasActivatedNSISUser() && !person.isLockedPerson()) {
					if (!authnRequest.isPassive()) {
						return loginService.initiateActivateNSISAccount(model);
					}
				}
			}

			// for non-selfservice service providers we have additional constraints
			if (person.isLockedByOtherThanPerson()) {
				if (authnRequest.isPassive()) {
					throw new ResponderException("Kunne ikke gennemføre passivt login da brugerens konto er låst");
				}
				else {
					return new ModelAndView(PersonService.getCorrectLockedPage(person));
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
		// Get post data
		String username = body.get("username");
		String password = body.get("password");

		// Get potential people based on username
		List<Person> people = loginService.getPeople(username);

		// If no match go back to login page
		if (people == null || people.size() == 0) {
			return loginService.initiateLogin(model, httpServletRequest, false, true, username);
		}

		// If more than one match go to select user page
		if (people.size() != 1) {
			sessionHelper.setPassword(password);
			return loginService.initiateUserSelect(model, people);
		}

		// If only one match continue login flow
		Person person = people.get(0);
		return continueLoginFlow(person, username, password, sessionHelper.getAuthnRequest(), httpServletRequest, httpServletResponse, model);
	}

	// Hitting this endpoint is not intended, but if a user does it is likely due to hitting the back button in the login flow.
	// If the person logging in picked the wrong user and pressed back, they
	@GetMapping(value = "/sso/saml/login/multiple/accounts")
	public ModelAndView altLoginGet(Model model, @RequestParam Map<String, String> body, HttpServletResponse httpServletResponse, HttpServletRequest httpServletRequest) throws ResponderException, RequesterException {

		// Sanity checks, Has the user been through multiple account select in session, is the selected person one of the available people?
		List<Person> availablePeople = sessionHelper.getAvailablePeople();
		Person person = sessionHelper.getPerson();

		if (availablePeople != null && !availablePeople.isEmpty() && person != null && availablePeople.contains(person)) {
			return loginService.initiateUserSelect(model, availablePeople);
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

		// If person has not been determined after the user posted their selected user,
		// something is wrong, we use AuthnRequest to send an error back to the SP otherwise we just log a warning
		if (person == null) {
			if (authnRequest == null) {
				log.warn("No person on session for login with multiple accounts with chosen ID = " + id);
				return new ModelAndView("redirect:/");
			}

			errorResponseService.sendError(httpServletResponse, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.REQUESTER, new RequesterException("Person prøvede at logge ind med bruger som de ikke havde adgang til"));
			return null;
		}

		// The Person has now been determined. If the user came to the user select screen from any flow send them back to that, otherwise default to the login flow

		// if person is in process of logging in with NemId or MitId
		if (sessionHelper.isInNemIdOrMitIDAuthenticationFlow()) {
			sessionHelper.setPerson(person);
			return loginService.continueLoginWithMitIdOrNemId(person, sessionHelper.getNemIDMitIDNSISLevel(), authnRequest, httpServletRequest, httpServletResponse, model);
		}

		// if person is in process of changing password
		if (sessionHelper.isInPasswordChangeFlow()) {
			sessionHelper.setPerson(person);
			try {
				// if the person has not approved the conditions then do that first
				if (loginService.requireApproveConditions(person)) {
					sessionHelper.setInChangePasswordFlowAndHasNotApprovedConditions(true);
					return loginService.initiateApproveConditions(model);
				}

				// if the user is allowed to activate their NSIS account an have not done so,
				// we should prompt first since they will not set their NSIS password without activating first
				if (person.isNsisAllowed() && !person.hasActivatedNSISUser() && !person.isLockedPerson()) {
					return loginService.initiateActivateNSISAccount(model, true);
				}

				return loginService.continueChangePassword(model);
			}
			catch (RequesterException ex) {
				if (authnRequest == null) {
					log.error("Error occured during password change with multiple persons with chosen personId = " + id, ex);
					
					// TODO: do something better than redirect to front-page, they are attempting to change password, so what to tell them?
					return new ModelAndView("redirect:/");
				}

				errorResponseService.sendError(httpServletResponse, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.REQUESTER, ex);
			}
			
			return null;
		}

		// The user did not access the multi-user select from any other flow, so we continue with login
		return continueLoginFlow(person, null, sessionHelper.getPassword(), authnRequest, httpServletRequest, httpServletResponse, model);
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
		if (person.isLockedByOtherThanPerson()) {
			return new ModelAndView(PersonService.getCorrectLockedPage(person));
		}

		// Has the user approved conditions?
		if (loginService.requireApproveConditions(person)) {
			return loginService.initiateApproveConditions(model);
		}

		// Has the user activated their NSIS User?
		if (person.isNsisAllowed() && !person.hasActivatedNSISUser() && !person.isLockedPerson()) {
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

		Person person = sessionHelper.getPerson();
		if (person == null) {
			// it seems weird to ever get here without a person on the session, but the endpoint can be accessed directly,
			// so a NPE could be thrown if we do not check for null here.
			log.warn("No person found on session");
			return new ModelAndView("redirect:/");
		}

		sessionHelper.setInPasswordExpiryFlow(false);

		// Check if locked
		if (person.isLockedByOtherThanPerson()) {
			return new ModelAndView(PersonService.getCorrectLockedPage(person));
		}

		// Has the user approved conditions?
		if (loginService.requireApproveConditions(person)) {
			return loginService.initiateApproveConditions(model);
		}

		// Has the user activated their NSIS User?
		if (person.isNsisAllowed() && !person.hasActivatedNSISUser() && !person.isLockedPerson()) {
			return loginService.initiateActivateNSISAccount(model);
		}

		// Check password
		String password = sessionHelper.getPassword();
		sessionHelper.setPassword(null);

		if (!PasswordValidationResult.VALID.equals(loginService.validatePassword(password, person))) {
			if (person.isLockedByOtherThanPerson()) {
				return new ModelAndView(PersonService.getCorrectLockedPage(person));
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
			log.warn("Could not find temporaryClient");
			return "redirect:/error";
		}

		// The temporaryClient should have been created at most 5 minutes before calling this endpoint
		if (LocalDateTime.now().minusMinutes(5).isAfter(temporaryClient.getTts())) {
			log.warn("Client asked for session using expired key");
			return "redirect:/error";
		}

		// Everything checks out and we can set the person and the nsislevel on the session.
		// Note this will not lower their level if they have already established a higher NSIS level.
		log.debug("Windows client token exchanged for session for personID: " + temporaryClient.getPerson().getId());
		sessionHelper.setPasswordLevel(temporaryClient.getNsisLevel());
		sessionHelper.setAuthnInstant(DateTime.now());
		sessionHelper.setPerson(temporaryClient.getPerson());

		return "redirect:/";
	}

	private ModelAndView continueLoginFlow(Person person, String username, String password, AuthnRequest authnRequest, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Model model) throws RequesterException, ResponderException {
		// Check that AuthnRequest is present and fetch the ServiceProvider by the AuthnRequest
		// 	This check is done before validating password, Fetching the service provider can fail because the SP is not supported
		// 	This error would not give any information about the user that is in the process of login.
		if (authnRequest == null) {
			log.warn("No authnRequest found on session");
			return new ModelAndView("redirect:/");
		}
		ServiceProvider serviceProvider = serviceProviderFactory.getServiceProvider(authnRequest);

		// Check password
		switch (loginService.validatePassword(password, person)) {
			case VALID:
				personService.correctPasswordAttempt(person, sessionHelper.isAuthenticatedWithADPassword(), false);
				break;
			case VALID_EXPIRED:
				personService.correctPasswordAttempt(person, sessionHelper.isAuthenticatedWithADPassword(), true);
				break;
			case INVALID:
				// Password was invalid, so we check if they have not locked themselves out of their account,
				// otherwise we just return them to the login prompt
				if (person.isLocked()) {
					return new ModelAndView(PersonService.getCorrectLockedPage(person));
				}
				else {
					return loginService.initiateLogin(model, httpServletRequest, false, true, (username != null ? username : ""));
				}
		}

		// Remember the person on session since we now know who they are confirmed by username/password
		sessionHelper.setPerson(person);

		// In both Valid and Expired we check if we should initiate a password expired prompt
		// since we start prompting the user to change their password 7 days before we force them to do so
		ModelAndView modelAndView = loginService.initiatePasswordExpired(person, model);
		if (modelAndView != null) {
			sessionHelper.setInPasswordExpiryFlow(true);
			sessionHelper.setPassword(password);
			return modelAndView;
		}

		// Check if the person meets the requirements of the ServiceProvider specified in the AuthnRequest
		RequirementCheckResult meetsRequirementsResult = serviceProvider.personMeetsRequirements(person);
		if (!RequirementCheckResult.OK.equals(meetsRequirementsResult)) {
			auditLogger.loginRejectedByConditions(person, meetsRequirementsResult);
			ResponderException e = new ResponderException("Login afbrudt, da brugeren ikke opfylder kravene til denne tjenesteudbyder");
			errorResponseService.sendError(httpServletResponse, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.REQUESTER, e);
			return null;
		}

		// If we have to re-authenticate MFA: Instead of starting MFA we clear any previous MFA Level,
		// and let login service handle mfa since it chooses between multiple options
		if (authnRequest.isForceAuthn() && serviceProvider.mfaRequired(authnRequest)) {
			sessionHelper.setMFALevel(null);
		}

		// Check if locked
		if (person.isLockedByOtherThanPerson()) {
			return new ModelAndView(PersonService.getCorrectLockedPage(person));
		}

		// If the SP requires NSIS LOW or above, extra checks required
		if (NSISLevel.LOW.equalOrLesser(serviceProvider.nsisLevelRequired(authnRequest))) {
			if (!person.isNsisAllowed()) {
				ResponderException e = new ResponderException("Login afbrudt, da brugeren ikke er godkendt til NSIS login");
				errorResponseService.sendError(httpServletResponse, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.REQUESTER, e);
				return null;
			}

			// Has the user approved conditions?
			if (loginService.requireApproveConditions(person)) {
				return loginService.initiateApproveConditions(model);
			}

			// Has the user activated their NSIS User?
			if (person.isNsisAllowed() && !person.hasActivatedNSISUser() && !person.isLockedPerson()) {
				if (sessionHelper.isAuthenticatedWithADPassword()) {
					sessionHelper.setPassword(password);
				}
				return loginService.initiateActivateNSISAccount(model);
			}
		}

		// If a person was manually activated, after successful password login they must change their password
		ModelAndView forceChangePassword = loginService.initiateForceChangePassword(person, model);
		if (forceChangePassword != null) {
			sessionHelper.setInForceChangePasswordFlow(true);
			return forceChangePassword;
		}

		return loginService.initiateFlowOrCreateAssertion(model, httpServletResponse, httpServletRequest, person);
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
