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
import dk.digitalidentity.controller.dto.PasswordChangeForm;
import dk.digitalidentity.controller.dto.ValidateADPasswordForm;
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
	private PasswordSettingService passwordSettingService;

	@Autowired
	private UsernameAndPasswordHelper usernameAndPasswordHelper;
	
	@Autowired
	private ADPasswordService adPasswordService;

	@Autowired
	private CprService cprService;
	
	@InitBinder("passwordForm")
	public void initClientBinder(WebDataBinder binder) {
		binder.setValidator(passwordChangeFormValidator);
	}

	// TODO: der er flere der har bookmarket denne side, så vi bør håndtere fejlene på en bedre måde (med mindre der er et AuthnRequest på sessionen,
	//       så sender vi dem bare tilbage til hvor de kom fra)
	@GetMapping("/konto/aktiver")
	public ModelAndView beginActivateAccount(Model model, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ResponderException, RequesterException {
		// Sanity checks
		RequesterException ex = null;
		Person person = sessionHelper.getPerson();
		if (person == null) {
			ex = new RequesterException("Kunne ikke aktivere kontoen, ingen person var associeret til login sessionen");
		}

		if (ex == null && !person.isNsisAllowed()) {
			ex = new RequesterException("Kunne ikke aktivere kontoen, personen ikke har adgang til at få tildelt en erhvervsidentitet: " + person.getId());
		}

		// If not in activate account flow, check for stored authnRequest on session and proceed with login. Otherwise send error.
		if (ex == null && !sessionHelper.isInActivateAccountFlow()) {
			AuthnRequest authnRequest = sessionHelper.getAuthnRequest();
			if (authnRequest == null) {
				ex = new RequesterException("Prøvede at tilgå aktiver erhvervsidentitet endpoint, men var ikke i activateAccountFlow (Person ID: '" + person.getId() + "')");
			}
			else {
				log.warn("Prøvede at tilgå aktiver erhvervsidentitet endpoint, men var ikke i activateAccountFlow (Person ID: '" + person.getId() + "')");

				ServiceProvider serviceProvider = serviceProviderFactory.getServiceProvider(authnRequest);

				// Different places in the code need to handle a locked person differently,
				// so this has to be checked BEFORE the initiateFlowOrCreateAssertion method, with the logic fitting the situation.
				// If trying to login to anything else than selfservice check if person is locked
				if (!(serviceProvider instanceof SelfServiceServiceProvider)) {
					if (person.isLocked()) {
						return new ModelAndView("error-locked-account");
					}
				}

				return loginService.initiateFlowOrCreateAssertion(model, httpServletResponse, httpServletRequest, person);
			}
		}

		// if person has an NSIS user already and is in the
		if (ex == null && person.hasNSISUser()) {
			if (!StringUtils.hasLength(person.getNsisPassword())) {
				// if password has not been set yet, go to pick password page
				return new ModelAndView("redirect:/konto/vaelgkode");
			}

			// TODO: disse sker (mest i Greve pga deres tendends til at bookmarke sub-sider i et flow), så lad os håndtere dem
			//       ved at sende dem til en fejlside specifikt til dette formål
			ex = new RequesterException("Kunne ikke aktivere kontoen, personen allerede har en konto: " + person.getId());
		}

		// handle error in sanity checks
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

		// check if the person has authorized with nemid, if so both PasswordLevel and MFALevel should be SUBSTANTIAL and nemidpid should be saved on session
		String nemIDPid = sessionHelper.getNemIDPid();
		String mitIDNameID = sessionHelper.getMitIDNameID();
		NSISLevel passwordLevel = sessionHelper.getPasswordLevel();
		NSISLevel mfaLevel = sessionHelper.getMFALevel();
		if ((!StringUtils.hasLength(nemIDPid) && !StringUtils.hasLength(mitIDNameID)) || !NSISLevel.SUBSTANTIAL.equalOrLesser(passwordLevel) || !NSISLevel.SUBSTANTIAL.equalOrLesser(mfaLevel)) {
			return loginService.initiateNemIDOnlyLogin(model, httpServletRequest, RequireNemIdReason.ACTIVATE_ACCOUNT);
		}

		// create User for person
		String userId = usernameAndPasswordHelper.getUserId(person);
		if (userId == null) {
			log.warn("Could not issue identity to " + person.getId() + " because userId generation failed!");
			return new ModelAndView("activateAccount/activate-failed");
		}

		person.setUserId(userId);
		person.setNemIdPid(nemIDPid);
		person.setMitIdNameId(mitIDNameID);
		person.setNsisLevel(NSISLevel.SUBSTANTIAL);
		
		if (cprService.checkIsDead(person)) {
			log.error("Could not issue identity to " + person.getId() + " because cpr says the person is dead!");
			return new ModelAndView("activateAccount/activate-failed-dead");
		}
		
		auditLogger.activatedByPerson(person, nemIDPid, mitIDNameID);

		// if you were prompted to activate your NSIS account during your change password request and accepted
		// you will end up in this controller and we should just finish the change password flow which will set the NSIS password (and AD if enabled)
		// along with redirecting you to where you were supposed to go.
		if (sessionHelper.isInPasswordChangeFlow()) {
			personService.save(person);
			return loginService.continueChangePassword(model);
		}

		// check if person has authenticated with their AD password. If they have, replicate the AD password to the nsis password field
		if (sessionHelper.isAuthenticatedWithADPassword() && StringUtils.hasLength(sessionHelper.getPassword())) {

			// replication of the AD password to the NSIS Password field is only allowed if the password follows the rules set for
			// NSIS passwords otherwise ask the user for a new password
			ChangePasswordResult validPassword = passwordSettingService.validatePasswordRules(sessionHelper.getPerson(), sessionHelper.getPassword());
			if (validPassword.equals(ChangePasswordResult.OK)) {
				try {
					// can ignore return value because we bypass replication
					personService.changePassword(person, sessionHelper.getPassword(), true);
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
			ModelAndView mav = new ModelAndView("activateAccount/activate-complete");
			mav.addObject("userId", PersonService.getUsername(person));
			
			return mav;
		}

		// if password has not been set yet, go to pick password page
		return new ModelAndView("redirect:/konto/vaelgkode");
	}

	@GetMapping("/konto/fortsaetlogin")
	public ModelAndView continueLogin(Model model, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ResponderException, RequesterException {
		AuthnRequest authnRequest = sessionHelper.getAuthnRequest();

		try {
			// Sanity checks
			RequesterException ex = null;
			if (!sessionHelper.isInActivateAccountFlow()) {
				ex = new RequesterException("Prøvede at tilgå forsæt login uden lige at have aktiveret sin erhvervskonto");
			}
	
			Person person = sessionHelper.getPerson();
			if (ex == null && person == null) {
				ex = new RequesterException("Prøvede at tilgå forsæt login, men ingen person var associeret med sessionen");
			}
	
			// if you were prompted to activate your NSIS acocunt during your change password request and rejected the prompt
			// you will end up in this controller and we should just finish the change password flow which will NOT set the NSIS password
			if (sessionHelper.isInPasswordChangeFlow()) {
				return loginService.continueChangePassword(model);
			}
	
			// Handle error in sanity checks
			if (authnRequest == null) {
				// probably some kind of bookmark - want to contact the end-user at some point and ask
				String referer = (httpServletRequest.getHeader("referer"));
				String personId = (person != null) ? Long.toString(person.getId()) : "<null>";
				
				log.warn("Attempted to access /konto/fortsaetlogin without an authnRequest present. Person = " + personId + ", Referer = " + referer);
				return new ModelAndView("redirect:/");
			}
	
			if (ex != null) {
				errorResponseService.sendError(httpServletResponse, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.REQUESTER, ex);
				return null;
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
		catch (RequesterException ex) {
			errorResponseService.sendError(httpServletResponse, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.REQUESTER, ex);
		}
		catch (ResponderException ex) {
			errorResponseService.sendError(httpServletResponse, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.RESPONDER, ex);
		}

		return null;
	}

	@GetMapping("/konto/vaelgkode")
	public String activateSelectPassword(Model model, HttpServletResponse httpServletResponse, @RequestParam(value="doNotUseCurrentADPassword", required = false, defaultValue = "false") boolean doNotUseCurrentADPassword) throws ResponderException, RequesterException {
		Person person = sessionHelper.getPerson();
		if (person == null || !person.hasNSISUser()) {
			String message = (person == null) ? "Prøvede at tilgå vælgkode, men ingen person var associeret med sessionen" : ("Tilgik vælgkode, men havde ingen NSIS bruger: " + person.getUuid());

			// if they end up here they likely bookmarked this page
			// we don't know why they ended on the select password page (either through activation or password change)
			// but we can just log a warning and show it on the index page instead
			log.warn(message);
			sessionHelper.invalidateSession();

			return "redirect:/";
		}

		// sometimes people bookmark this page as a "change password" page, but it is intended to be used in-flow of the activation,
		// so detect those, and redirect them to the actual change password page
		if (!sessionHelper.isInDedicatedActivateAccountFlow() && !sessionHelper.isInActivateAccountFlow()) {
			return "redirect:/sso/saml/changepassword";
		}
		
		if (doNotUseCurrentADPassword) {
			sessionHelper.setDoNotUseCurrentADPassword(true);
		}
		
		PasswordSetting settings = passwordService.getSettings(person.getDomain());
		if (sessionHelper.isInDedicatedActivateAccountFlow() && sessionHelper.isDoNotUseCurrentADPassword()) {
			String samaccountName = null;

            if (person.getSamaccountName() != null && settings.isReplicateToAdEnabled()) {
            	samaccountName = person.getSamaccountName();
            }

            model.addAttribute("samaccountName", samaccountName);
            model.addAttribute("settings", settings);			
			model.addAttribute("passwordForm", new PasswordChangeForm());

			return "changePassword/change-password";
		}
		
		if (!sessionHelper.isDoNotUseCurrentADPassword() && !sessionHelper.isAuthenticatedWithADPassword() && StringUtils.hasLength(person.getSamaccountName()) && settings.isValidateAgainstAdEnabled()) {
			model.addAttribute("validateADPasswordForm", new ValidateADPasswordForm());
			return "activateAccount/activate-validate-ad-password";
		}

		model.addAttribute("settings", settings);
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
			ADPasswordStatus adPasswordStatus = personService.changePassword(person, form.getPassword(), false);
			if (ADPasswordResponse.isCritical(adPasswordStatus)) {
				model.addAttribute("technicalError", true);
				model.addAttribute("settings", passwordService.getSettings(person.getDomain()));
				return new ModelAndView("activateAccount/activate-select-password");
			}
		}
		catch (NoSuchPaddingException | InvalidKeyException | NoSuchAlgorithmException | IllegalBlockSizeException | BadPaddingException | UnsupportedEncodingException | InvalidAlgorithmParameterException e) {
			AuthnRequest authnRequest = sessionHelper.getAuthnRequest();
			errorResponseService.sendError(response, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.RESPONDER, new RequesterException("Kunne ikke skifte password på personen", e));
			return null;
		}

		sessionHelper.setDoNotUseCurrentADPassword(false);
		
		return loginService.initiateFlowOrCreateAssertion(model, response, request, person);
	}
	
	@PostMapping("/konto/valideradkodeord")
	public ModelAndView postValidateADPassword(Model model, @ModelAttribute("validateADPasswordForm") ValidateADPasswordForm form, BindingResult bindingResult, HttpServletRequest request, HttpServletResponse response) throws ResponderException, RequesterException {
		RequesterException ex = null;
		Person person = sessionHelper.getPerson();
		if (person == null || !person.hasNSISUser()) {
			ex = (person == null) ? new RequesterException("Prøvede at validere AD kodeord, men ingen person var associeret med sessionen")
								  : new RequesterException("Prøvede at validere AD kodeord, men personen havde ikke en oprettet en NSIS Bruger");

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
			return new ModelAndView("activateAccount/activate-validate-ad-password");
		}
		
		if (!adPasswordService.validatePassword(person, form.getPassword())) {
			model.addAttribute("wrongPassword", true);
			return new ModelAndView("activateAccount/activate-validate-ad-password");
		}
		
		ChangePasswordResult validPassword = passwordSettingService.validatePasswordRules(person, form.getPassword());
		if (!validPassword.equals(ChangePasswordResult.OK)) {
			sessionHelper.setDoNotUseCurrentADPassword(true);
			model.addAttribute("redirectUrl", "/konto/vaelgkode");

			return new ModelAndView("activateAccount/ad_password_too_weak");
		}

		try {
			// can ignore return value because we bypass replication
			personService.changePassword(person, form.getPassword(), true);
		}
		catch (NoSuchPaddingException | InvalidKeyException | NoSuchAlgorithmException | IllegalBlockSizeException | BadPaddingException | UnsupportedEncodingException | InvalidAlgorithmParameterException e) {
			AuthnRequest authnRequest = sessionHelper.getAuthnRequest();
			errorResponseService.sendError(response, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.RESPONDER, new RequesterException("Kunne ikke skifte password på personen", e));
			return null;
		}

		if (sessionHelper.isInDedicatedActivateAccountFlow()) {
			sessionHelper.setInDedicatedActivateAccountFlow(false);
			model.addAttribute("username", PersonService.getUsername(person));

			return new ModelAndView("activateAccount/activation-completed", model.asMap());
		}

		return loginService.initiateFlowOrCreateAssertion(model, response, request, person);
	}
	
	@GetMapping("/konto/init-aktiver")
	public String initiateActivation(Model model) {
		return "activateAccount/initiate-activate";
	}
	
	@PostMapping("/konto/init-aktiver")
	public ModelAndView startActivation(Model model, HttpServletRequest request) {
		sessionHelper.clearSession();
		sessionHelper.setInDedicatedActivateAccountFlow(true);
		
		return loginService.initiateNemIDOnlyLogin(model, request, RequireNemIdReason.DEDICATED_ACTIVATE_ACCOUNT);
	}
}
