package dk.digitalidentity.controller;

import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import dk.digitalidentity.common.dao.model.PasswordSetting;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.ADPasswordService;
import dk.digitalidentity.common.service.CprService;
import dk.digitalidentity.common.service.PasswordSettingService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.enums.ChangePasswordResult;
import dk.digitalidentity.common.service.model.ADPasswordResponse;
import dk.digitalidentity.common.service.model.ADPasswordResponse.ADPasswordStatus;
import dk.digitalidentity.controller.dto.LoginRequest;
import dk.digitalidentity.controller.dto.PasswordChangeForm;
import dk.digitalidentity.controller.dto.ValidateADPasswordForm;
import dk.digitalidentity.controller.validator.PasswordChangeValidator;
import dk.digitalidentity.service.ErrorHandlingService;
import dk.digitalidentity.service.ErrorResponseService;
import dk.digitalidentity.service.FlowService;
import dk.digitalidentity.service.SessionHelper;
import dk.digitalidentity.service.model.enums.RequireNemIdReason;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
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
	private FlowService flowService;

	@Autowired
	private ErrorHandlingService errorHandlingService;

	@Autowired
	private ErrorResponseService errorResponseService;

	@Autowired
	private PasswordChangeValidator passwordChangeFormValidator;
	
	@Autowired
	private PasswordSettingService passwordSettingService;
	
	@Autowired
	private ADPasswordService adPasswordService;

	@Autowired
	private CprService cprService;
	
	@InitBinder("passwordForm")
	public void initClientBinder(WebDataBinder binder) {
		binder.setValidator(passwordChangeFormValidator);
	}

	@GetMapping("/konto/aktiver")
	public ModelAndView beginActivateAccount(Model model, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ResponderException, RequesterException {
		// Sanity checks
		String error = null;
		Person person = sessionHelper.getPerson();
		if (person == null) {
			error = "Kunne ikke aktivere kontoen, ingen person var associeret til login sessionen";
		}

		if (error == null && !person.isNsisAllowed()) {
			error = "Kunne ikke aktivere kontoen, personen ikke har adgang til at få tildelt en erhvervsidentitet";
		}
		
		// this also catches persons that has locked themselves, so all types of locks
		if (error == null && person.isLocked()) {
			error = "Kunne ikke aktivere kontoen, da personen er låst";
		}

		// If not in activate account flow, check for stored authnRequest on session and proceed with login. Otherwise send error.
		if (error == null && !sessionHelper.isInActivateAccountFlow()) {
			LoginRequest loginRequest = sessionHelper.getLoginRequest();
			if (loginRequest == null) {
				error = "Prøvede at tilgå aktiver erhvervsidentitet endpoint, men var ikke i activateAccountFlow";
			}
			else {
				log.warn("Prøvede at tilgå aktiver erhvervsidentitet endpoint, men var ikke i activateAccountFlow (Person ID: '" + person.getId() + "')");

				if (person.isLockedByOtherThanPerson()) {
					return new ModelAndView(PersonService.getCorrectLockedPage(person));
				}

				return flowService.initiateFlowOrSendLoginResponse(model, httpServletResponse, httpServletRequest, person);
			}
		}

		// if person has an NSIS user already and is in the
		if (error == null && person.hasActivatedNSISUser()) {
			if (!StringUtils.hasLength(person.getNsisPassword())) {
				// if password has not been set yet, go to pick password page
				return new ModelAndView("redirect:/konto/vaelgkode");
			}

			error = "Kunne ikke aktivere kontoen, personen allerede har en konto";
		}

		// handle error in sanity checks
		if (error != null) {
			LoginRequest loginRequest = sessionHelper.getLoginRequest();
			if (loginRequest != null) {
				errorResponseService.sendError(httpServletResponse, loginRequest, new RequesterException(error));
				return null;
			}
			else {
				return errorHandlingService.modelAndViewError("/konto/aktiver", httpServletRequest, error, model);
			}
		}

		// check if the person has authorized with nemid, if so both PasswordLevel and MFALevel should be SUBSTANTIAL and nemidpid should be saved on session
		String mitIDNameID = sessionHelper.getMitIDNameID();
		NSISLevel passwordLevel = sessionHelper.getPasswordLevel();
		NSISLevel mfaLevel = sessionHelper.getMFALevel();
		if (!StringUtils.hasLength(mitIDNameID) || !NSISLevel.SUBSTANTIAL.equalOrLesser(passwordLevel) || !NSISLevel.SUBSTANTIAL.equalOrLesser(mfaLevel)) {
			return flowService.initiateNemIDOnlyLogin(model, httpServletRequest, RequireNemIdReason.ACTIVATE_ACCOUNT);
		}

		// create User for person
		person.setMitIdNameId(mitIDNameID);
		person.setNsisLevel(NSISLevel.SUBSTANTIAL);
		
		if (cprService.checkIsDead(person)) {
			log.error("Could not issue identity to " + person.getId() + " because cpr says the person is dead!");
			return new ModelAndView("activateAccount/activate-failed-dead");
		}
		
		auditLogger.activatedByPerson(person, mitIDNameID);

		// if you were prompted to activate your NSIS account during your change password request and accepted
		// you will end up in this controller and we should just finish the change password flow which will set the NSIS password (and AD if enabled)
		// along with redirecting you to where you were supposed to go.
		if (sessionHelper.isInPasswordChangeFlow()) {
			personService.save(person);
			return flowService.continueChangePassword(model);
		}

		// check if person has authenticated with their AD password. If they have, replicate the AD password to the nsis password field
		if (sessionHelper.isAuthenticatedWithADPassword() && StringUtils.hasLength(sessionHelper.getPassword())) {

			// replication of the AD password to the NSIS Password field is only allowed if the password follows the rules set for
			// NSIS passwords otherwise ask the user for a new password.
			// In the case where a user has previously had a NSIS password (and therefore password history exists)
			// we allow the user to use their AD password even if it matches with a password history we have saved.
			ChangePasswordResult validPassword = passwordSettingService.validatePasswordRulesWithoutSlowValidationRules(sessionHelper.getPerson(), sessionHelper.getPassword());
			if (validPassword.equals(ChangePasswordResult.OK)) {
				try {
					// can ignore return value because we bypass replication
					personService.changePassword(person, sessionHelper.getPassword(), true, null, null, false);
					sessionHelper.setAuthenticatedWithADPassword(false);
				} catch (NoSuchPaddingException | InvalidKeyException | NoSuchAlgorithmException | IllegalBlockSizeException | BadPaddingException | UnsupportedEncodingException | InvalidAlgorithmParameterException e) {
					log.error("Kunne ikke kryptere password, password blev derfor ikke ændret", e);
					throw new ResponderException("Der opstod en fejl i skift kodeord");
				}
			}
		}

		personService.save(person);

		// if password has already been set, just continue login
		if (person.getNsisPassword() != null) {
			ModelAndView mav = new ModelAndView("activateAccount/activation-completed");
			mav.addObject("username", PersonService.getUsername(person));
			
			return mav;
		}
		
		// Go to choose password reset or unlock account page
		if (sessionHelper.isInChoosePasswordResetOrUnlockAccountFlow()) {
			return flowService.continueChoosePasswordResetOrUnlockAccount(model);
		}
		
		// if password has not been set yet, go to pick password page
		return new ModelAndView("redirect:/konto/vaelgkode");
	}

	@GetMapping("/konto/fortsaetlogin")
	public ModelAndView continueLogin(Model model, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ResponderException, RequesterException {
		LoginRequest loginRequest = sessionHelper.getLoginRequest();

		try {
			// sanity checks
			RequesterException ex = null;
			if (!sessionHelper.isInActivateAccountFlow()) {
				ex = new RequesterException("Prøvede at tilgå forsæt login uden at være i et aktiveringsflow");
			}
	
			Person person = sessionHelper.getPerson();
			if (ex == null && person == null) {
				ex = new RequesterException("Prøvede at tilgå forsæt login, men ingen person var associeret med sessionen");
			}
	
			// if you were prompted to activate your NSIS account during your change password request and rejected the prompt
			// you will end up in this controller and we should just finish the change password flow which will NOT set the NSIS password
			if (sessionHelper.isInPasswordChangeFlow()) {
				return flowService.continueChangePassword(model);
			}
			else if (sessionHelper.isInChoosePasswordResetOrUnlockAccountFlow()) {
				return flowService.continueChoosePasswordResetOrUnlockAccount(model);
			}

			// Handle error in sanity checks
			if (loginRequest == null) {
				// probably some kind of bookmark
				return errorHandlingService.modelAndViewError("/konto/fortsaetlogin", httpServletRequest, "Attempted to access endpoint without an loginRequest present", model);
			}
	
			if (ex != null) {
				errorResponseService.sendError(httpServletResponse, loginRequest, ex);
				return null;
			}
	
			sessionHelper.setInActivateAccountFlow(false);
	
			if (person.isLockedByOtherThanPerson()) {
				return new ModelAndView(PersonService.getCorrectLockedPage(person));
			}

			sessionHelper.setDeclineUserActivation(true);

			return flowService.initiateFlowOrSendLoginResponse(model, httpServletResponse, httpServletRequest, person);
		}
		catch (RequesterException | ResponderException ex) {
			if (loginRequest == null) {
				// TODO: remove this error log once we know why this happens (BSG)
				log.error("Got an exception while loginRequest = null", ex);

				return errorHandlingService.modelAndViewError("/konto/fortsaetlogin", httpServletRequest, ex.getMessage(), model);
			}
			else {
				errorResponseService.sendError(httpServletResponse, loginRequest, ex);
			}
		}

		return null;
	}

	@GetMapping("/konto/vaelgkode")
	public String activateSelectPassword(Model model, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, @RequestParam(value="doNotUseCurrentADPassword", required = false, defaultValue = "false") boolean doNotUseCurrentADPassword) throws ResponderException, RequesterException {
		Person person = sessionHelper.getPerson();

		if (person == null || !person.hasActivatedNSISUser()) {		
			String message = (person == null) ? "Prøvede at tilgå vælgkode, men ingen person var associeret med sessionen" : ("Tilgik vælgkode, men havde ingen NSIS bruger: " + person.getUuid());
			String destination = errorHandlingService.error("/konto/vaelgkode", httpServletRequest, message, model);

			sessionHelper.invalidateSession();
			return destination;
		}

		// sometimes people bookmark this page as a "change password" page, but it is intended to be used in-flow of the activation,
		// so detect those, and redirect them to the actual change password page
		if (!sessionHelper.isInDedicatedActivateAccountFlow() && !sessionHelper.isInActivateAccountFlow()) {
			return "redirect:/sso/saml/changepassword";
		}
		
		if (doNotUseCurrentADPassword) {
			sessionHelper.setDoNotUseCurrentADPassword(true);
		}
		
		PasswordSetting topDomainSettings = passwordService.getSettingsCached(person.getTopLevelDomain());
		PasswordSetting settings = passwordService.getSettingsCached(person.getDomain());
		if (sessionHelper.isInDedicatedActivateAccountFlow() && sessionHelper.isDoNotUseCurrentADPassword()) {
			return flowService.continueChangePassword(model).getViewName();
		}
		
		if (!sessionHelper.isDoNotUseCurrentADPassword() && !sessionHelper.isAuthenticatedWithADPassword() && StringUtils.hasLength(person.getSamaccountName()) && topDomainSettings.isValidateAgainstAdEnabled()) {
			model.addAttribute("validateADPasswordForm", new ValidateADPasswordForm());
			return "activateAccount/activate-validate-ad-password";
		}

		model.addAttribute("settings", settings);
		model.addAttribute("passwordForm", new PasswordChangeForm());

		return "activateAccount/activate-select-password";
	}

	@PostMapping("/konto/vaelgkode")
	public ModelAndView postChangePassword(Model model, @Valid @ModelAttribute("passwordForm") PasswordChangeForm form, BindingResult bindingResult, HttpServletRequest request, HttpServletResponse response) throws ResponderException, RequesterException {
		if (!sessionHelper.isInActivateAccountFlow()) {
			// User his postChangePassword in activate account controller without being in the activate account flow
			return new ModelAndView("redirect:/sso/saml/changepassword");
		}

		RequesterException ex = null;
		Person person = sessionHelper.getPerson();
		if (person == null || !person.hasActivatedNSISUser()) {
			ex = (person == null) ? new RequesterException("Prøvede at vælge kode, men ingen person var associeret med sessionen")
								  : new RequesterException("Prøvede at vælge kode, men personen havde ikke en oprettet en NSIS Bruger");

			LoginRequest loginRequest = sessionHelper.getLoginRequest();
			if (loginRequest != null) {
				errorResponseService.sendError(response, loginRequest, ex);
				return null;
			}
			else {
				log.warn("No AuthnRequest on session error thrown since error destination unknown");
				throw ex;
			}
		}

		if (bindingResult.hasErrors()) {
			model.addAttribute("settings", passwordService.getSettingsCached(person.getDomain()));
			return new ModelAndView("activateAccount/activate-select-password");
		}

		try {
			ADPasswordStatus adPasswordStatus = personService.changePassword(person, form.getPassword());
			if (ADPasswordResponse.isCritical(adPasswordStatus)) {
				model.addAttribute("technicalError", true);
				model.addAttribute("settings", passwordService.getSettingsCached(person.getDomain()));
				
				return new ModelAndView("activateAccount/activate-select-password");
			}
		}
		catch (NoSuchPaddingException | InvalidKeyException | NoSuchAlgorithmException | IllegalBlockSizeException | BadPaddingException | UnsupportedEncodingException | InvalidAlgorithmParameterException e) {
			LoginRequest loginRequest = sessionHelper.getLoginRequest();
			errorResponseService.sendError(response, loginRequest, new RequesterException("Kunne ikke skifte password på personen", e));
			return null;
		}

		// send to activation completed and allow them to login if they want
		if (sessionHelper.isInDedicatedActivateAccountFlow()) {
			sessionHelper.clearSession();
			model.addAttribute("username", PersonService.getUsername(person));
			model.addAttribute("linkToSelfservice", true);

			return new ModelAndView("activateAccount/activation-completed", model.asMap());
		}

		sessionHelper.setDoNotUseCurrentADPassword(false);

		return flowService.initiateFlowOrSendLoginResponse(model, response, request, person);
	}
	
	@PostMapping("/konto/valideradkodeord")
	public ModelAndView postValidateADPassword(Model model, @ModelAttribute("validateADPasswordForm") ValidateADPasswordForm form, BindingResult bindingResult, HttpServletRequest request, HttpServletResponse response) throws ResponderException, RequesterException {
		RequesterException ex = null;
		Person person = sessionHelper.getPerson();
		if (person == null || !person.hasActivatedNSISUser()) {
			ex = (person == null) ? new RequesterException("Prøvede at validere AD kodeord, men ingen person var associeret med sessionen")
								  : new RequesterException("Prøvede at validere AD kodeord, men personen havde ikke en oprettet en NSIS Bruger");

			LoginRequest loginRequest = sessionHelper.getLoginRequest();
			if (loginRequest != null) {
				errorResponseService.sendError(response, loginRequest, ex);
				return null;
			}
			else {
				log.warn("No AuthnRequest on session error thrown since error destination unknown");
				throw ex;
			}
		}
		
		if (bindingResult.hasErrors()) {
			return new ModelAndView("activateAccount/activate-validate-ad-password");
		}
		
		if (!ADPasswordResponse.ADPasswordStatus.OK.equals(adPasswordService.validatePassword(person, form.getPassword()))) {
			model.addAttribute("wrongPassword", true);
			return new ModelAndView("activateAccount/activate-validate-ad-password");
		}

		// In the case where a user has previously had a NSIS password (and therefore password history exists)
		// we allow the user to use their AD password even if it matches with a password history we have saved.
		ChangePasswordResult validPassword = passwordSettingService.validatePasswordRulesWithoutSlowValidationRules(person, form.getPassword());
		if (!validPassword.equals(ChangePasswordResult.OK)) {
			sessionHelper.setDoNotUseCurrentADPassword(true);
			model.addAttribute("redirectUrl", "/konto/vaelgkode");

			return new ModelAndView("activateAccount/ad_password_too_weak");
		}

		try {
			// can ignore return value because we bypass replication
			personService.changePassword(person, form.getPassword(), true, null, null, false);
		}
		catch (NoSuchPaddingException | InvalidKeyException | NoSuchAlgorithmException | IllegalBlockSizeException | BadPaddingException | UnsupportedEncodingException | InvalidAlgorithmParameterException e) {
			LoginRequest loginRequest = sessionHelper.getLoginRequest();
			errorResponseService.sendError(response, loginRequest, new RequesterException("Kunne ikke skifte password på personen", e));
			return null;
		}

		if (sessionHelper.isInDedicatedActivateAccountFlow()) {
			sessionHelper.clearSession();
			model.addAttribute("username", PersonService.getUsername(person));
			model.addAttribute("linkToSelfservice", true);

			return new ModelAndView("activateAccount/activation-completed", model.asMap());
		}

		return flowService.initiateFlowOrSendLoginResponse(model, response, request, person);
	}
	
	@GetMapping("/konto/init-aktiver")
	public String initiateActivation(Model model) {
		// clear all states/flows, so we get a clean activation
		sessionHelper.clearFlowStates();

		try {
			sessionHelper.setLoginRequest(null);
		}
		catch (Exception ignored) {
			;
		}
		
		return "activateAccount/initiate-activate";
	}
	
	@PostMapping("/konto/init-aktiver")
	public ModelAndView startActivation(Model model, HttpServletRequest request) {
		sessionHelper.clearSession();
		sessionHelper.setInDedicatedActivateAccountFlow(true);
		
		return flowService.initiateNemIDOnlyLogin(model, request, RequireNemIdReason.DEDICATED_ACTIVATE_ACCOUNT);
	}
}
