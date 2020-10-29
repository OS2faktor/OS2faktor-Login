package dk.digitalidentity.controller;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.PasswordSettingService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.controller.dto.PasswordChangeForm;
import dk.digitalidentity.service.AuthnRequestHelper;
import dk.digitalidentity.service.ErrorResponseService;
import dk.digitalidentity.service.LoginService;
import dk.digitalidentity.service.SessionHelper;
import dk.digitalidentity.service.serviceprovider.SelfServiceServiceProvider;
import dk.digitalidentity.service.serviceprovider.ServiceProvider;
import dk.digitalidentity.service.serviceprovider.ServiceProviderFactory;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.StatusCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.ModelAndView;

@Slf4j
@Controller
public class ActivateAccountController {
	private static final SecureRandom random = new SecureRandom();

	@Autowired
	private PersonService personService;
	
	@Autowired
	private AuditLogger auditLogger;

	@Autowired
	private SessionHelper sessionHelper;

	@Autowired
	private PasswordSettingService passwordService;

	@Autowired
	private LoginService loginService;

	@Autowired
	private ErrorResponseService errorResponseService;

	@Autowired
	private AuthnRequestHelper authnRequestHelper;

	@Autowired
	private ServiceProviderFactory serviceProviderFactory;

	@PostMapping("/konto/aktiver")
	public String activateComplete(Model model, HttpServletResponse httpServletResponse) throws ResponderException, RequesterException {
		Person person = sessionHelper.getPerson();
		String nemIDPid = sessionHelper.getNemIDPid();

		if (person == null || person.hasNSISUser() || StringUtils.isEmpty(nemIDPid)) {
			AuthnRequest authnRequest = sessionHelper.getAuthnRequest();
			errorResponseService.sendError(httpServletResponse, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.REQUESTER, new RequesterException("Prøvede at tilgå accepter vilkår, men ingen person var associeret eller personen havde allerede godkendt vilkårne"));
			return null;
		}

		//Create User for person
		String userId = getUserId();
		if (userId == null) {
			log.warn("Could not issue identity to " + person.getId() + " because userId generation failed!");
			return "activateAccount/activate-failed";
		}

		person.setUserId(userId);
		person.setApprovedConditions(true);
		person.setApprovedConditionsTts(LocalDateTime.now());
		person.setNemIdPid(nemIDPid);
		person.setNsisLevel(NSISLevel.SUBSTANTIAL);

		auditLogger.activatedByPerson(person);

		// Check if person has authenticated with their ad password. If they have, replicate the AD password to the nsis password field
		if (sessionHelper.isAuthenticatedWithADPassword() && !StringUtils.isEmpty(person.getAdPassword())) {
			person.setNsisPassword(person.getAdPassword());
			sessionHelper.setAuthenticatedWithADPassword(false);
		}

		personService.save(person);
		sessionHelper.setActivateAccountCompleted(true);

		// Populate model for success page
		model.addAttribute("userId", userId);
		if (!StringUtils.isEmpty(person.getSamaccountName())) {
			model.addAttribute("adUserId", person.getSamaccountName());
		}

		if (person.getNsisPassword() != null) {
			model.addAttribute("passwordSet", true);
		}

		return "activateAccount/activate-complete";
	}

	@GetMapping("/konto/login")
	public ModelAndView continueLogin(Model model, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ResponderException, RequesterException {
		// ActivateAccountCompleted is used to check that access to this endpoint is only given if you've just completed your activation
		// and that access is given only once (Which is why it is set to false later in the method)
		AuthnRequest authnRequest = sessionHelper.getAuthnRequest();
		if (!sessionHelper.getActivateAccountCompleted()) {
			errorResponseService.sendError(httpServletResponse, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.REQUESTER, new RequesterException("Prøvede at tilgå forsæt login uden lige at have aktiveret sin erhvervskonto"));
			return null;
		}
		sessionHelper.setActivateAccountCompleted(false);

		Person person = sessionHelper.getPerson();
		if (person == null || !person.hasNSISUser()) {
			errorResponseService.sendError(httpServletResponse, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.REQUESTER, new RequesterException("Prøvede at tilgå forsæt login, men ingen person var associeret med sessionen"));
			return null;
		}

		ServiceProvider serviceProvider = serviceProviderFactory.getServiceProvider(authnRequest);

		// If trying to login to anything else than selfservice check if person is locked
		if (!(serviceProvider instanceof SelfServiceServiceProvider)) {
			if (person.isLocked()) {
				return new ModelAndView("error-locked-account");
			}
		}

		return loginService.initiateFlowOrCreateAssertion(model, httpServletResponse, httpServletRequest, person);
	}

	@GetMapping("/konto/vaelgkode")
	public String activateSelectPassword(Model model, HttpServletResponse httpServletResponse) throws ResponderException, RequesterException {
		Person person = sessionHelper.getPerson();
		if (person == null || !person.hasNSISUser()) {
			AuthnRequest authnRequest = sessionHelper.getAuthnRequest();
			errorResponseService.sendError(httpServletResponse, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.REQUESTER, new RequesterException("Prøvede at tilgå vælgkode, men ingen person var associeret med sessionen"));
			return null;
		}

		model.addAttribute("settings", passwordService.getSettings());
		model.addAttribute("passwordForm", new PasswordChangeForm());

		return "activateAccount/activate-select-password";
	}

	@PostMapping("/konto/vaelgkode")
	public ModelAndView postChangePassword(Model model, @Valid @ModelAttribute("passwordForm") PasswordChangeForm form, BindingResult bindingResult, HttpServletRequest request, HttpServletResponse response) throws ResponderException, RequesterException {
		if (bindingResult.hasErrors()) {
			model.addAttribute("settings", passwordService.getSettings());
			return new ModelAndView("activateAccount/activate-select-password");
		}

		Person person = sessionHelper.getPerson();
		if (person == null) {
			AuthnRequest authnRequest = sessionHelper.getAuthnRequest();
			errorResponseService.sendError(response, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.REQUESTER, new RequesterException("Prøvede at vælge kode, men ingen person var associeret med sessionen"));
			log.error("No person associated with session");
			return null;
		}

		if (!person.hasNSISUser()) {
			AuthnRequest authnRequest = sessionHelper.getAuthnRequest();
			errorResponseService.sendError(response, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.REQUESTER, new RequesterException("Prøvede at vælge kode, men personen havde ikke en oprettet en NSIS Bruger"));
			return null;
		}

		try {
			personService.changePassword(person, form.getPassword());
		} catch (NoSuchPaddingException | InvalidKeyException | NoSuchAlgorithmException | IllegalBlockSizeException | BadPaddingException | UnsupportedEncodingException ex) {
			AuthnRequest authnRequest = sessionHelper.getAuthnRequest();
			errorResponseService.sendError(response, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.RESPONDER, new RequesterException("Kunne ikke skifte password på personen", ex));
			return null;
		}

		return loginService.initiateFlowOrCreateAssertion(model, response, request, person);
	}

	private String getUserId() {
		String userId = null;
		int maxTries = 30;
		do {
			userId = "NS" + (random.nextInt(999999) + 100000);

			if (personService.getByUserId(userId) == null) {
				break;
			}

			// make sure userId is null if we get to this point, so all failed tries will result in null value
			userId = null;
		}
		while (--maxTries > 0);
		return userId;
	}
}
