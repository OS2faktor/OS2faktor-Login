package dk.digitalidentity.controller;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.StatusCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.ModelAndView;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.PasswordSettingService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.controller.dto.PasswordChangeForm;
import dk.digitalidentity.controller.validator.PasswordChangeValidator;
import dk.digitalidentity.service.AuthnRequestHelper;
import dk.digitalidentity.service.ErrorResponseService;
import dk.digitalidentity.service.LoginService;
import dk.digitalidentity.service.SessionHelper;
import dk.digitalidentity.service.model.enums.RequireNemIdReason;
import dk.digitalidentity.service.serviceprovider.SelfServiceServiceProvider;
import dk.digitalidentity.service.serviceprovider.ServiceProvider;
import dk.digitalidentity.service.serviceprovider.ServiceProviderFactory;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import dk.digitalidentity.util.UsernameAndPasswordHelper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
public class ActivateAccountController {

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

	@Autowired
	private PasswordChangeValidator passwordChangeFormValidator;
	
	@Autowired
	private UsernameAndPasswordHelper usernameAndPasswordHelper;

	@InitBinder("passwordForm")
	public void initClientBinder(WebDataBinder binder) {
		binder.setValidator(passwordChangeFormValidator);
	}

	@GetMapping("/konto/aktiver")
	public ModelAndView beginActivateAccount(Model model, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ResponderException, RequesterException {
		// Sanity checks
		RequesterException ex = null;
		if (!sessionHelper.isInActivateAccountFlow()) {
			ex = new RequesterException("Prøvede at tilgå aktiver erhvervsidentitet endpoint direkte");
		}

		Person person = sessionHelper.getPerson();
		if (ex == null && person == null) {
			ex = new RequesterException("Kunne ikke aktivere kontoen, ingen person var associeret til login sessionen");
		}

		if (ex == null && !person.isNsisAllowed()) {
			ex = new RequesterException("Kunne ikke aktivere kontoen, personen ikke har adgang til at få tildelt en erhvervsidentitet");
		}

		if (ex == null && person.hasNSISUser()) {
			ex = new RequesterException("Kunne ikke aktivere kontoen, personen allerede har en konto");
		}

		// Handle error in sanity checks
		if (ex != null) {
			AuthnRequest authnRequest = sessionHelper.getAuthnRequest();
			if (authnRequest != null) {
				errorResponseService.sendError(httpServletResponse, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.REQUESTER, ex);
				return null;
			}
			else {
				log.warn("No AuthnRequest on session error thrown since error destination unknown");
				throw ex;
			}
		}

		// Check if the person has authorized with nemid, if so both PasswordLevel and MFALevel should be SUBSTANTIAL and nemidpid should be saved on session
		String nemIDPid = sessionHelper.getNemIDPid();
		NSISLevel passwordLevel = sessionHelper.getPasswordLevel();
		NSISLevel mfaLevel = sessionHelper.getMFALevel();
		if (StringUtils.isEmpty(nemIDPid) || !NSISLevel.SUBSTANTIAL.equalOrLesser(passwordLevel) || !NSISLevel.SUBSTANTIAL.equalOrLesser(mfaLevel)) {
			return loginService.initiateNemIDOnlyLogin(model, httpServletRequest, RequireNemIdReason.ACTIVATE_ACCOUNT);
		}

		// Create User for person
		String userId = usernameAndPasswordHelper.getUserId(person);
		if (userId == null) {
			log.warn("Could not issue identity to " + person.getId() + " because userId generation failed!");
			return new ModelAndView("activateAccount/activate-failed");
		}

		person.setUserId(userId);
		person.setNemIdPid(nemIDPid);
		person.setNsisLevel(NSISLevel.SUBSTANTIAL);
		auditLogger.activatedByPerson(person, nemIDPid);

		// Check if person has authenticated with their AD password. If they have, replicate the AD password to the nsis password field
		if (sessionHelper.isAuthenticatedWithADPassword() && !StringUtils.isEmpty(person.getAdPassword())) {
			try {
				personService.changePassword(person, person.getAdPassword(), false, true);
				sessionHelper.setAuthenticatedWithADPassword(false);
			} catch (NoSuchPaddingException | InvalidKeyException | NoSuchAlgorithmException | IllegalBlockSizeException | BadPaddingException | UnsupportedEncodingException e) {
				log.error("Kunne ikke kryptere password, password blev derfor ikke ændret", e);
				throw new ResponderException("Der opstod en fejl i skift kodeord");
			}
		}

		personService.save(person);

		// Populate model for success page
		String shownUserId = userId;
		if (!StringUtils.isEmpty(person.getSamaccountName())) {
			shownUserId = person.getSamaccountName();
		}
		model.addAttribute("userId", shownUserId);

		// If password has already been set, just continue login
		if (person.getNsisPassword() != null) {
			model.addAttribute("continueLogin", true);
		}

		return new ModelAndView("activateAccount/activate-complete");
	}

	@GetMapping("/konto/fortsaetlogin")
	public ModelAndView continueLogin(Model model, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ResponderException, RequesterException {
		// Sanity checks
		RequesterException ex = null;
		if (!sessionHelper.isInActivateAccountFlow()) {
			ex = new RequesterException("Prøvede at tilgå forsæt login uden lige at have aktiveret sin erhvervskonto");
		}

		Person person = sessionHelper.getPerson();
		if (ex == null && person == null) {
			ex = new RequesterException("Prøvede at tilgå forsæt login, men ingen person var associeret med sessionen");
		}

		// Handle error in sanity checks
		AuthnRequest authnRequest = sessionHelper.getAuthnRequest();
		if (ex != null) {
			if (authnRequest != null) {
				errorResponseService.sendError(httpServletResponse, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.REQUESTER, ex);
				return null;
			}
			else {
				log.warn("No AuthnRequest on session error thrown since error destination unknown");
				throw ex;
			}
		}

		sessionHelper.setInActivateAccountFlow(false);
		ServiceProvider serviceProvider = serviceProviderFactory.getServiceProvider(authnRequest);

		// Different places in the code need to handle a locked person differently,
		// so this has to be checked BEFORE the initiateFlowOrCreateAssertion method, with the logic fitting the situation.
		// If trying to login to anything else than selfservice check if person is locked
		if (!(serviceProvider instanceof SelfServiceServiceProvider)) {
			if (person.isLocked()) {
				return new ModelAndView("error-locked-account");
			}
		}

		sessionHelper.setDeclineUserActivation(true);
		return loginService.initiateFlowOrCreateAssertion(model, httpServletResponse, httpServletRequest, person);
	}

	@GetMapping("/konto/vaelgkode")
	public String activateSelectPassword(Model model, HttpServletResponse httpServletResponse) throws ResponderException, RequesterException {
		Person person = sessionHelper.getPerson();
		if (person == null || !person.hasNSISUser()) {
			AuthnRequest authnRequest = sessionHelper.getAuthnRequest();
			String message = (person == null) ? "Prøvede at tilgå vælgkode, men ingen person var associeret med sessionen" : "Tilgik vælgkode, men havde ingen NSIS bruger";
			RequesterException ex = new RequesterException(message);

			if (authnRequest != null) {
				errorResponseService.sendError(httpServletResponse, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.REQUESTER, ex);
				return null;
			}
			else {
				log.warn("No AuthnRequest on session error thrown since error destination unknown");
				throw ex;
			}
		}

		model.addAttribute("settings", passwordService.getSettings(person.getDomain()));
		model.addAttribute("passwordForm", new PasswordChangeForm());

		return "activateAccount/activate-select-password";
	}

	@PostMapping("/konto/vaelgkode")
	public ModelAndView postChangePassword(Model model, @Valid @ModelAttribute("passwordForm") PasswordChangeForm form, BindingResult bindingResult, HttpServletRequest request, HttpServletResponse response) throws ResponderException, RequesterException {
		RequesterException ex = null;
		Person person = sessionHelper.getPerson();
		if (person == null || !person.hasNSISUser()) {
			ex = (person == null) ? new RequesterException("Prøvede at vælge kode, men ingen person var associeret med sessionen")
								  : new RequesterException("Prøvede at vælge kode, men personen havde ikke en oprettet en NSIS Bruger");

			AuthnRequest authnRequest = sessionHelper.getAuthnRequest();
			if (authnRequest != null) {
				errorResponseService.sendError(response, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.REQUESTER, ex);
				return null;
			}
			else {
				log.warn("No AuthnRequest on session error thrown since error destination unknown");
				throw ex;
			}
		}

		if (bindingResult.hasErrors()) {
			model.addAttribute("settings", passwordService.getSettings(person.getDomain()));
			return new ModelAndView("activateAccount/activate-select-password");
		}

		try {
			boolean success = personService.changePassword(person, form.getPassword());

			if (!success) {
				model.addAttribute("settings", passwordService.getSettings(person.getDomain()));
				return new ModelAndView("activateAccount/activate-select-password");
			}
		}
		catch (NoSuchPaddingException | InvalidKeyException | NoSuchAlgorithmException | IllegalBlockSizeException | BadPaddingException | UnsupportedEncodingException e) {
			AuthnRequest authnRequest = sessionHelper.getAuthnRequest();
			errorResponseService.sendError(response, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.RESPONDER, new RequesterException("Kunne ikke skifte password på personen", e));
			return null;
		}

		return loginService.initiateFlowOrCreateAssertion(model, response, request, person);
	}
}
