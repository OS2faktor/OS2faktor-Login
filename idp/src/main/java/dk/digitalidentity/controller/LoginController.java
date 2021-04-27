package dk.digitalidentity.controller;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.opensaml.messaging.context.MessageContext;
import org.opensaml.saml.common.SAMLObject;
import org.opensaml.saml.common.messaging.context.SAMLBindingContext;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.StatusCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.controller.validator.PasswordChangeValidator;
import dk.digitalidentity.service.AuthnRequestHelper;
import dk.digitalidentity.service.AuthnRequestService;
import dk.digitalidentity.service.ErrorResponseService;
import dk.digitalidentity.service.LoginService;
import dk.digitalidentity.service.SessionHelper;
import dk.digitalidentity.service.serviceprovider.SelfServiceServiceProvider;
import dk.digitalidentity.service.serviceprovider.ServiceProvider;
import dk.digitalidentity.service.serviceprovider.ServiceProviderFactory;
import dk.digitalidentity.service.validation.AuthnRequestValidationService;
import dk.digitalidentity.util.Constants;
import dk.digitalidentity.util.LoggingUtil;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;

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
	private PasswordChangeValidator passwordChangeFormValidator;

	@InitBinder("passwordForm")
	public void initClientBinder(WebDataBinder binder) {
		binder.setValidator(passwordChangeFormValidator);
	}
	
	@GetMapping("/sso/saml/login")
	public ModelAndView loginRequest(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Model model) throws ResponderException, RequesterException {
		MessageContext<SAMLObject> messageContext = authnRequestService.getMessageContext(httpServletRequest);
		AuthnRequest authnRequest = authnRequestService.getAuthnRequest(messageContext);

		try {
			loggingUtil.logAuthnRequest(authnRequest, Constants.INCOMING);
			authnRequestValidationService.validate(httpServletRequest, messageContext);

			SAMLBindingContext subcontext = messageContext.getSubcontext(SAMLBindingContext.class);
			String relayState = subcontext != null ? subcontext.getRelayState() : null;

			sessionHelper.saveIncomingAuthnRequest(authnRequest, relayState);

			ServiceProvider serviceProvider = serviceProviderFactory.getServiceProvider(authnRequest);
			NSISLevel loginState = sessionHelper.getLoginState();

			// If force Authn and mfa required we need to force a new MFA auth
			if (authnRequest.isForceAuthn() && serviceProvider.mfaRequired(authnRequest)) {
				sessionHelper.setMFALevel(null);
			}

			if (authnRequest.isForceAuthn() || loginState == null) {
				return loginService.initiateLogin(model, httpServletRequest, serviceProvider.preferNemId());
			}

			// TODO: what happens if person is null?
			Person person = sessionHelper.getPerson();

			// If the SP requires NSIS LOW or above, extra checks required
			if (NSISLevel.LOW.equalOrLesser(serviceProvider.nsisLevelRequired(authnRequest))) {
				// Is user even allowed to login to nsis applications
				if (!person.isNsisAllowed()) {
					throw new ResponderException("Login afbrudt, da brugeren ikke er godkendt til NSIS login");
				}

				// Has the user approved conditions?
				if (!person.isApprovedConditions()) {
					if (authnRequest.isPassive()) {
						throw new ResponderException("Kunne ikke gennemføre passivt login da brugeren ikke har accepteret vilkårene for brug");
					}
					else {
						return loginService.initiateApproveConditions(model);
					}
				}

				// Has the user activated their NSIS User?
				if (person.isNsisAllowed() && !person.hasNSISUser()) {
					if (!authnRequest.isPassive()) {
						return loginService.initiateActivateNSISAccount(model);
					}
				}
			}

			// For non-selfservice service providers we have additional constraints
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
		ServiceProvider serviceProvider = serviceProviderFactory.getServiceProvider(authnRequest);

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
			personService.badPasswordAttempt(person);

			if (person.isLocked()) {
				return new ModelAndView("error-locked-account");
			}
			else {
				return loginService.initiateLogin(model, httpServletRequest, false, true, username);
			}
		}

		personService.correctPasswordAttempt(person);

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

		return loginService.initiateFlowOrCreateAssertion(model, httpServletResponse, httpServletRequest, person);
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
		if (person == null) {
			errorResponseService.sendError(httpServletResponse, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.REQUESTER,
					new RequesterException("Person prøvede at logge ind med bruger som de ikke havde adgang til"));
			return null;
		}

		sessionHelper.setPerson(person);
		// Person has now been determined

		// If we are currently in the process of changing password go to that flow
		if (sessionHelper.isInPasswordChangeFlow()) {
			try {
				return loginService.continueChangePassword(model);
			} catch (RequesterException e) {
				errorResponseService.sendError(httpServletResponse, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.REQUESTER, e);
			}
			return null;
		}






		// Get ServiceProvider
		ServiceProvider serviceProvider = serviceProviderFactory.getServiceProvider(authnRequest);

		if (authnRequest.isForceAuthn() && serviceProvider.mfaRequired(authnRequest)) {
			// Instead of just initialising mfa we just set it to null so login service can do that instead since it chooses between nemid or mfa login
			sessionHelper.setMFALevel(null);
		}

		// If the user started login by using nemid continue straight to nemid login
		if (sessionHelper.isAuthenticatedWithNemId()) {
			return loginService.continueNemdIDLogin(model, httpServletResponse, httpServletRequest);
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
			personService.badPasswordAttempt(person);

			if (person.isLocked()) {
				return new ModelAndView("error-locked-account");
			}
			else {
				return loginService.initiateLogin(model, httpServletRequest, false, true, "");
			}
		}

		personService.correctPasswordAttempt(person);

		return loginService.initiateFlowOrCreateAssertion(model, httpServletResponse, httpServletRequest, person);
	}

	@GetMapping("/sso/saml/login/continueLogin")
	public ModelAndView continueLoginWithoutChangePassword(Model model, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ResponderException, RequesterException {
		AuthnRequest authnRequest = sessionHelper.getAuthnRequest();

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
			personService.badPasswordAttempt(person);

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
		ResponderException ex = new ResponderException("Brugeren afbrød login flowet");
		errorResponseService.sendError(httpServletResponse, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.RESPONDER, ex, false, true);
		return null;
	}
}
