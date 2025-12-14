package dk.digitalidentity.controller;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.opensaml.messaging.context.MessageContext;
import org.opensaml.saml.common.SAMLObject;
import org.opensaml.saml.common.messaging.context.SAMLBindingContext;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.NameID;
import org.opensaml.saml.saml2.core.Subject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.SqlServiceProviderConfiguration;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.SqlServiceProviderConfigurationService;
import dk.digitalidentity.common.service.mfa.MFAService;
import dk.digitalidentity.controller.dto.LoginRequest;
import dk.digitalidentity.service.AuthnRequestHelper;
import dk.digitalidentity.service.AuthnRequestService;
import dk.digitalidentity.service.ErrorHandlingService;
import dk.digitalidentity.service.ErrorResponseService;
import dk.digitalidentity.service.FlowService;
import dk.digitalidentity.service.LoginService;
import dk.digitalidentity.service.SessionHelper;
import dk.digitalidentity.service.serviceprovider.ServiceProviderFactory;
import dk.digitalidentity.service.validation.AuthnRequestValidationService;
import dk.digitalidentity.util.Constants;
import dk.digitalidentity.util.LoggingUtil;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import dk.digitalidentity.util.ShowErrorToUserException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
    private SqlServiceProviderConfigurationService sqlServiceProviderConfigurationService;

	@Autowired
	private LoggingUtil loggingUtil;

	@Autowired
	private SessionHelper sessionHelper;

	@Autowired
	private LoginService loginService;

	@Autowired
	private FlowService flowService;

	@Autowired
	private PersonService personService;
	
	@Autowired
	private MFAService mfaService;
	
	@Autowired
	private CommonConfiguration commonConfiguration;
	
	@Autowired
	private ServiceProviderFactory serviceProviderFactory;

	@GetMapping("/fragment/username")
	public String getLoginUsernameFragment(Model model, @RequestParam(name = "username", required = false, defaultValue = "") String username) {
		if (!StringUtils.hasText(username)) {
			return "fragments/username :: unknown";
		}
		
		model.addAttribute("username", username);
		
		return "fragments/username :: known";
	}
	
	@RequestMapping(value = { "/sso/saml/login", "/sso/saml/login/" }, method = { POST, GET } )
	public ModelAndView loginRequest(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Model model) throws ResponderException, RequesterException, ShowErrorToUserException {
		if ("HEAD".equals(httpServletRequest.getMethod())) {
			log.warn("Rejecting HEAD request in login handler from " + getIpAddress(httpServletRequest) + "(" + httpServletRequest.getHeader("referer") + ")");
			return new ModelAndView("redirect:/");
		}

		// If no SAMLRequest is present, we cannot decode the message for obvious reasons
		if (!StringUtils.hasLength(httpServletRequest.getParameter("SAMLRequest"))) {
			log.warn("SAMLRequest was null or empty");
			return new ModelAndView("redirect:/");
		}

		sessionHelper.prepareNewLogin();

		// Get MessageContext (Including the saml object) from the HttpServletRequest
		MessageContext<SAMLObject> messageContext;
		try {
			messageContext = authnRequestService.getMessageContext(httpServletRequest);
		}
		catch (RequesterException ex) {
			// note!
			// Also checks for POST methods now.
			// I've added a hack to HttpRedirectDeflateDecoder so it attempts normal parsing first, and if it fails,
			// it will try to detect spaces in the payload, and if they exist, it will replace them with + signs,
			// and attempt once more - if we update OpenSAML, we need to port that fix
			log.warn("Failed to parse SAMLRequest = " + httpServletRequest.getParameter("SAMLRequest"));
			
			// In case we cannot decode the message we have received just log a warning
			// and redirect to the error page with an explanation of why bookmarking does not work
			// (likely due to no actual authnRequest being sent because of bookmarking)
			return errorHandlingService.modelAndViewError("/sso/saml/login", httpServletRequest, "Could not Decode messageContext", model);
		}

		// Extract AuthnRequest from message
		AuthnRequest authnRequest = authnRequestService.getAuthnRequest(messageContext);
		if (authnRequest == null) {
			log.warn("No authnRequest found in request");
			return new ModelAndView("redirect:/");
		}
		
		// At this point we have a authnRequest,
		// so if we encounter any errors we will return them to the SP
		// instead of just displaying an error page on the IdP
		try {
			loggingUtil.logAuthnRequest(authnRequest, Constants.INCOMING);

			SAMLBindingContext subcontext = messageContext.getSubcontext(SAMLBindingContext.class);
			String relayState = subcontext != null ? subcontext.getRelayState() : null;

			sessionHelper.setRelayState(relayState);

			authnRequestValidationService.validate(httpServletRequest, messageContext);

			sessionHelper.setRequestedUsername(getRequestedUsername(authnRequest));

			LoginRequest loginRequest = new LoginRequest(authnRequest, httpServletRequest.getHeader("User-Agent"));
			loginRequest.setRelayState(relayState);

			return loginService.loginRequestReceived(httpServletRequest, httpServletResponse, model, loginRequest);
		}
		catch (RequesterException | ResponderException | SecurityException ex) {

			// special case - the signature might be invalid, so we will trigger a refresh of metadata, in case that is the issue (new certificate)
			if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("signatur")) {
				try {
					SqlServiceProviderConfiguration sqlServiceProviderConfiguration = sqlServiceProviderConfigurationService.getByEntityId(authnRequest.getIssuer().getValue());
					LocalDateTime nextRefresh = sqlServiceProviderConfiguration.getManualReloadTimestamp();

					if (nextRefresh == null || LocalDateTime.now().minusMinutes(5).isAfter(nextRefresh)) {
						sqlServiceProviderConfiguration.setManualReloadTimestamp(LocalDateTime.now());
						sqlServiceProviderConfigurationService.save(sqlServiceProviderConfiguration);
					}
				}
				catch (Exception ignored) {
					;
				}
			}

			errorResponseService.sendError(httpServletResponse, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), ex, serviceProviderFactory.getServiceProvider(authnRequest));
		}

		// we return a SAML Response with an error message instead
		return null;
	}

	private String getRequestedUsername(AuthnRequest authnRequest) {
		Subject subject = authnRequest.getSubject();
		if (subject != null) {
			NameID nameID = subject.getNameID();
			if (nameID != null) {
				String requestedUsername = nameID.getValue();
				if (StringUtils.hasLength(requestedUsername)) {
					return requestedUsername;
				}
			}
		}
		return null;
	}

	@PostMapping(value = "/sso/login-passwordless")
	public ModelAndView loginPasswordless(Model model, @RequestParam Map<String, String> body, HttpServletResponse httpServletResponse, HttpServletRequest httpServletRequest) throws ResponderException, RequesterException {
		// if passwordless is not enabled, handle this request with the normal login
		if (!commonConfiguration.getCustomer().isEnablePasswordlessMfa()) {
			log.warn("Attempted to access /sso/login-passwordless when not enabled");
			return login(model, body, httpServletResponse, httpServletRequest);
		}

		// get post data
		String username = body.get("username");

		// get potential people based on username
		List<Person> people = loginService.getPeople(username);

		// persons locked by 3rd party (municipality, admin or cpr) are filtered out
		people = people.stream()
				.filter(p -> !(p.isLockedAdmin() || p.isLockedCivilState() || p.isLockedDataset()))
				.collect(Collectors.toList());

		// if no match go back to login page
		if (people.size() == 0) {
			return flowService.initiateLogin(model, httpServletRequest, false, true, username, false);
		}

		// if more than one person, ask for password and enter "normal" loginflow without passwordless
		if (people.size() != 1) {
			return flowService.initiateLogin(model, httpServletRequest, false, false, username, true);
		}

		// if only one match continue login flow
		Person person = people.get(0);

		if (mfaService.hasPasswordlessMfa(person)) {
			// if there is already a session with a different person, clear that
			Person existingPersonOnSession = sessionHelper.getPerson();
			if (existingPersonOnSession != null && existingPersonOnSession.getId() != person.getId()) {
				sessionHelper.clearAuthentication();
			}
			
			sessionHelper.setInPasswordlessMfaFlow(true, person.getSamaccountName());

			return loginService.continueLoginFlow(person, username, null, sessionHelper.getLoginRequest(), httpServletRequest, httpServletResponse, model);
		}

		// if no passwordless, ask for password
		return flowService.initiateLogin(model, httpServletRequest, false, false, username, true);
	}

	@GetMapping(value = "/sso/login/password")
	public ModelAndView loginForcePassword(Model model, HttpServletRequest httpServletRequest) throws ResponderException, RequesterException {
		String username = sessionHelper.getPasswordlessMfaFlowUsername();
		sessionHelper.setInPasswordlessMfaFlow(false, null);

		return flowService.initiateLogin(model, httpServletRequest, false, false, username, true);
	}
	
	@PostMapping(value = "/sso/login")
	public ModelAndView login(Model model, @RequestParam Map<String, String> body, HttpServletResponse httpServletResponse, HttpServletRequest httpServletRequest) throws ResponderException, RequesterException {
		// get post data
		String username = body.get("username");
		String password = body.get("password");

		// get potential people based on username
		List<Person> people = loginService.getPeople(username);

		// persons locked by 3rd party (municipality, admin or cpr) are filtered out
		people = people.stream()
				.filter(p -> !(p.isLockedAdmin() || p.isLockedCivilState() || p.isLockedDataset()))
				.collect(Collectors.toList());

		// if no match go back to login page
		if (people.size() == 0) {
			return flowService.initiateLogin(model, httpServletRequest, false, true, username, false);
		}

		// if more than one match go to select user page
		if (people.size() != 1) {
			sessionHelper.setPassword(password);
			
			return flowService.initiateUserSelect(model, people, null, sessionHelper.getLoginRequest(), httpServletRequest, httpServletResponse);
		}

		// if only one match continue login flow
		Person person = people.get(0);
		return loginService.continueLoginFlow(person, username, password, sessionHelper.getLoginRequest(), httpServletRequest, httpServletResponse, model);
	}

	// Hitting this endpoint is not intended, but if a user does it is likely due to hitting the back button in the login flow.
	// If the person logging in picked the wrong user and pressed back, they
	@GetMapping(value = "/sso/saml/login/multiple/accounts")
	public ModelAndView altLoginGet(Model model, @RequestParam Map<String, String> body, HttpServletResponse httpServletResponse, HttpServletRequest httpServletRequest) throws ResponderException, RequesterException {
		// Sanity checks, Has the user been through multiple account select in session, is the selected person one of the available people?
		List<Person> availablePeople = sessionHelper.getAvailablePeople();
		Person person = sessionHelper.getPerson();

		if (availablePeople != null && !availablePeople.isEmpty() && person != null && availablePeople.contains(person)) {
			return flowService.initiateUserSelect(model, availablePeople, null, sessionHelper.getLoginRequest(), httpServletRequest, httpServletResponse);
		}
		else {
			LoginRequest loginRequest = sessionHelper.getLoginRequest();
			RequesterException ex = new RequesterException("Tilgik '/sso/saml/login/multiple/accounts'. Sessionen var ikke korrekt, kunne ikke fortsætte login. Prøv igen.");

			if (loginRequest != null) {
				errorResponseService.sendError(httpServletResponse, loginRequest, ex);
				return null;
			}
			else {				
				ModelAndView view = errorHandlingService.modelAndViewError("/sso/saml/login/multiple/accounts", model);
				sessionHelper.invalidateSession();

				return view;
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

		LoginRequest loginRequest = sessionHelper.getLoginRequest();

		// If person has not been determined after the user posted their selected user,
		// something is wrong, we use AuthnRequest to send an error back to the SP otherwise we just log a warning
		if (person == null) {
			if (loginRequest == null) {
				log.warn("No person on session for login with multiple accounts with chosen ID = " + id);
				return new ModelAndView("redirect:/");
			}

			errorResponseService.sendError(httpServletResponse, loginRequest, new RequesterException("Person prøvede at logge ind med bruger som de ikke havde adgang til"));
			return null;
		}

		// The Person has now been determined. If the user came to the user select screen from any flow send them back to that, otherwise default to the login flow

		// if person is in process of logging in with NemId or MitId
		if (sessionHelper.isInNemIdOrMitIDAuthenticationFlow()) {
			sessionHelper.setPerson(person);
			
			try {
				return loginService.continueLoginWithMitIdOrNemId(person, sessionHelper.getNemIDMitIDNSISLevel(), loginRequest, httpServletRequest, httpServletResponse, model);
			}
			catch (RequesterException | ResponderException ex) {
				errorResponseService.sendError(httpServletResponse, loginRequest, ex);
				return null;
			}
		}

		// if person is in process of changing password
		if (sessionHelper.isInPasswordChangeFlow()) {
			sessionHelper.setPerson(person);
			try {
				// if the person has not approved the conditions then do that first
				if (personService.requireApproveConditions(person)) {
					sessionHelper.setInChangePasswordFlowAndHasNotApprovedConditions(true);
					return flowService.initiateApproveConditions(model);
				}

				// if the user is allowed to activate their NSIS account an have not done so,
				// we should prompt first since they will not set their NSIS password without activating first
				if (person.isNsisAllowed() && !person.hasActivatedNSISUser() && !person.isLockedPerson()) {
					return flowService.initiateActivateNSISAccount(model, true);
				}

				return flowService.continueChangePassword(model);
			}
			catch (RequesterException ex) {
				if (loginRequest == null) {
					log.error("Error occured during password change with multiple persons with chosen personId = " + id, ex);

					// TODO: do something better than redirect to front-page, they are attempting to change password, so what to tell them?
					return new ModelAndView("redirect:/");
				}

				errorResponseService.sendError(httpServletResponse, loginRequest, ex);
			}

			return null;
		}

		// The user did not access the multi-user select from any other flow, so we continue with login
		return loginService.continueLoginFlow(person, null, sessionHelper.getPassword(), loginRequest, httpServletRequest, httpServletResponse, model);
	}

	@GetMapping("/sso/saml/login/nemid")
	public ModelAndView nemIdOnly(HttpServletRequest httpServletRequest, Model model) {
		return flowService.initiateNemIDOnlyLogin(model, httpServletRequest, null);
	}

	@GetMapping("/sso/saml/login/forceChangePassword/continueLogin")
	public ModelAndView continueLoginAfterForceChangePassword(Model model, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ResponderException, RequesterException {
		LoginRequest loginRequest = sessionHelper.getLoginRequest();
		if (loginRequest == null) {
			log.warn("No loginRequest found on session");
			return new ModelAndView("redirect:/");
		}

		try {
			return loginService.continueLoginAfterForceChangePassword(httpServletRequest, httpServletResponse, model);
		}
		catch (RequesterException | ResponderException ex) {
			errorResponseService.sendError(httpServletResponse, loginRequest, ex);
		}

		return null;
	}

	@GetMapping("/sso/saml/login/continueLogin")
	public ModelAndView continueLoginWithoutChangePassword(Model model, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ResponderException, RequesterException {
		LoginRequest loginRequest = sessionHelper.getLoginRequest();
		if (loginRequest == null) {
			log.warn("No loginRequest found on session");
			return new ModelAndView("redirect:/");
		}

		try {
			return loginService.continueLoginChangePasswordDeclined(httpServletRequest, httpServletResponse, model);
		}
		catch (RequesterException | ResponderException ex) {
			errorResponseService.sendError(httpServletResponse, loginRequest, ex);
		}

		return null;
	}

	@GetMapping("/sso/saml/login/cancel")
	public String cancelLogin(HttpServletResponse httpServletResponse) throws ResponderException, RequesterException {
		LoginRequest loginRequest = sessionHelper.getLoginRequest();
		if (loginRequest == null) {
			log.warn("No loginRequest found on session");
			return "redirect:/";
		}

		ResponderException ex = new ResponderException("Brugeren afbrød login flowet");
		errorResponseService.sendError(httpServletResponse, loginRequest, ex, false, true);
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
