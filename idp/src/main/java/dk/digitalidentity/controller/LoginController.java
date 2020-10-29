package dk.digitalidentity.controller;

import dk.digitalidentity.service.model.enums.RequireNemIdReason;
import dk.digitalidentity.common.service.PersonService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
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
			if (authnRequest.isForceAuthn() || loginState == null) {
				return loginService.initiateLogin(model, httpServletRequest, serviceProvider.preferNemId());
			}

			Person person = sessionHelper.getPerson();

			// If logging in with NSIS LOW or above, conditions must be approved
			if (!person.isApprovedConditions() && NSISLevel.LOW.equalOrLesser(serviceProvider.nsisLevelRequired(authnRequest))) {
				if (authnRequest.isPassive()) {
					throw new ResponderException("Kunne ikke gennemføre passivt login da brugeren ikke har accepteret vilkårene for brug af erhvervsidentiteter");
				}
				else {
					return loginService.initiateNemIDOnlyLogin(model, httpServletRequest, RequireNemIdReason.TERMS_AND_CONDITIONS);
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

		List<Person> people = loginService.getPeople(username);

		// If no match go back to login page
		if (people == null || people.size() == 0) {
			return loginService.initiateLogin(model, httpServletRequest, false, true, username);
		}

		// If more than one match go to select user page
		if (people.size() != 1) {
			sessionHelper.setPassword(body.get("password"));
			return loginService.initiateUserSelect(model, people, false);
		}

		// If only one match
		Person person = people.get(0);

		// Check if locked
		if (person.isLocked()) {
			return new ModelAndView("error-locked-account");
		}

		// Get ServiceProvider
		AuthnRequest authnRequest = sessionHelper.getAuthnRequest();
		ServiceProvider serviceProvider = serviceProviderFactory.getServiceProvider(authnRequest);

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
		sessionHelper.setPerson(person);

		// Check if confirmed conditions
		if (!person.isApprovedConditions() && NSISLevel.LOW.equalOrLesser(serviceProvider.nsisLevelRequired(authnRequest))) {
			return loginService.initiateNemIDOnlyLogin(model, httpServletRequest, RequireNemIdReason.TERMS_AND_CONDITIONS);
		}

		if (authnRequest.isForceAuthn() && serviceProvider.mfaRequired(authnRequest)) {
			// Instead of just initialising mfa we just set it to null so login service can do that instead since it chooses between nemid or mfa login
			sessionHelper.setMFALevel(null);
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

		// If the user started login by using nemid continue straight to nemid login
		if (sessionHelper.isAuthenticatedWithNemId()) {
			return loginService.continueNemdIDLogin(model, httpServletResponse, httpServletRequest);
		}

		// Check if locked
		if (person.isLocked()) {
			return new ModelAndView("error-locked-account");
		}

		// Get ServiceProvider
		ServiceProvider serviceProvider = serviceProviderFactory.getServiceProvider(authnRequest);

		// Check confirmed conditions
		if (!person.isApprovedConditions() && !(serviceProvider instanceof SelfServiceServiceProvider)) {
			return new ModelAndView("error-conditions-not-approved");
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

		if (authnRequest.isForceAuthn() && serviceProvider.mfaRequired(authnRequest)) {
			// Instead of just initialising mfa we just set it to null so login service can do that instead since it chooses between nemid or mfa login
			sessionHelper.setMFALevel(null);
		}

		return loginService.initiateFlowOrCreateAssertion(model, httpServletResponse, httpServletRequest, person);
	}

	@GetMapping("/sso/saml/login/forgotpassword")
	public String forgotPassword() {
		return "forgot-password";		
	}

	@GetMapping("/sso/saml/login/cancel")
	public String cancelLogin(HttpServletResponse httpServletResponse) throws ResponderException, RequesterException {
		sessionHelper.clearSession();

		AuthnRequest authnRequest = sessionHelper.getAuthnRequest();

		ResponderException ex = new ResponderException("Brugeren afbrød login flowet");
		errorResponseService.sendError(httpServletResponse, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.RESPONDER, ex, false);
		return null;
	}
}
