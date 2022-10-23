package dk.digitalidentity.controller;

import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;

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
import dk.digitalidentity.common.service.GroupService;
import dk.digitalidentity.common.service.PasswordSettingService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.enums.ChangePasswordResult;
import dk.digitalidentity.common.service.model.ADPasswordResponse;
import dk.digitalidentity.common.service.model.ADPasswordResponse.ADPasswordStatus;
import dk.digitalidentity.controller.dto.PasswordChangeForm;
import dk.digitalidentity.controller.validator.PasswordChangeValidator;
import dk.digitalidentity.service.ErrorResponseService;
import dk.digitalidentity.service.LoginService;
import dk.digitalidentity.service.SessionHelper;
import dk.digitalidentity.service.model.enums.RequireNemIdReason;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
public class ChangePasswordController {

	@Autowired
	private AuditLogger auditLogger;

    @Autowired
    private SessionHelper sessionHelper;

    @Autowired
    private LoginService loginService;

    @Autowired
    private PersonService personService;

    @Autowired
    private PasswordSettingService passwordSettingService;

    @Autowired
    private PasswordChangeValidator passwordChangeFormValidator;

    @Autowired
    private ErrorResponseService errorResponseService;

    @InitBinder("passwordForm")
    public void initClientBinder(WebDataBinder binder) {
        binder.setValidator(passwordChangeFormValidator);
    }
    
    @GetMapping("/sso/saml/forgotpworlocked")
    public ModelAndView forgotPWOrLocked(Model model, HttpServletRequest request) throws RequesterException, ResponderException {
		setRedirectUrl(request);
		sessionHelper.setInChoosePasswordResetOrUnlockAccountFlow(true);

        // if there is no person on the session initiate login
        Person person = sessionHelper.getPerson();
        if (person == null) {
            return loginService.initiateNemIDOnlyLogin(model, request, RequireNemIdReason.CHANGE_PASSWORD, null);
        }

        return loginService.continueChoosePasswordResetOrUnlockAccount(model);
    }

    @GetMapping("/sso/saml/changepassword")
    public ModelAndView changePassword(Model model, HttpServletRequest request, HttpServletResponse response, @RequestParam(name = "redirectUrl", required = false, defaultValue = "") String url, @RequestParam(name = "errCode", required = false) String errCode) throws ResponderException, RequesterException {
        // remember where to redirect to
        sessionHelper.setPasswordChangeSuccessRedirect(url);

        // show NemId login form
        sessionHelper.setInPasswordChangeFlow(true);
        sessionHelper.setPassword(null); // This will allow you to change password on any of your users regardless of which you're currently logged in with

        // if there is no person on the session initiate login
        Person person = sessionHelper.getPerson();
        if (person == null || sessionHelper.hasNSISUserAndLoggedInWithNSISNone()) {
            return loginService.initiateNemIDOnlyLogin(model, request, RequireNemIdReason.CHANGE_PASSWORD, errCode);
        }
        
        // if the person has not approved the conditions then do that first
		if (loginService.requireApproveConditions(person)) {
			sessionHelper.setInChangePasswordFlowAndHasNotApprovedConditions(true);
			return loginService.initiateApproveConditions(model);
		}

		// if the user is allowed to activate their NSIS account and have not done so yet,
		// we should prompt first since they will not set their NSIS password without activating first
		if (person.isNsisAllowed() && !person.hasActivatedNSISUser() && !person.isLockedPerson()) {
			// TODO: infinite loop?
			return loginService.initiateActivateNSISAccount(model, true);
		}
		
		if (isInCanNotChangePasswordGroup(person)) {
			model.addAttribute("redirectUrl", url);
			return new ModelAndView("changePassword/can_not_change_password_group_error", model.asMap());
		}
		
		if (changedPasswordTooManyTimes(person)) {
			model.addAttribute("redirectUrl", url);
			return new ModelAndView("changePassword/changed_password_too_many_times_error", model.asMap());
		}

        // continue change password logic
        try {
            if (isAllowedChangePassword(person)) {
                return loginService.continueChangePassword(model);
            }
        }
        catch (RequesterException e) {
            ResponderException ex = new ResponderException("Fejl opstået ved skift kodeord");
            errorResponseService.sendResponderError(response, sessionHelper.getAuthnRequest(), ex);
            return null;
        }

        // Reach here if we have a person on the session but they are not logged in to the correct level
        return loginService.initiateNemIDOnlyLogin(model, request, RequireNemIdReason.CHANGE_PASSWORD);
    }

    @PostMapping("/sso/saml/changepassword")
    public ModelAndView changePasswordPost(HttpServletResponse response, Model model, @Valid @ModelAttribute("passwordForm") PasswordChangeForm form, BindingResult bindingResult) throws ResponderException, RequesterException {
        if (!sessionHelper.isInPasswordChangeFlow()) {
        	if (sessionHelper.getAuthnRequest() != null) {
	            ResponderException ex = new ResponderException("Fejl opstået ved skift kodeord, prøvede at ændre kodeord uden at være i skift kodeord flow");
	            errorResponseService.sendResponderError(response, sessionHelper.getAuthnRequest(), ex);
	            return null;
        	}

            Person person = sessionHelper.getPerson();
        	log.warn("Fejl opstået ved skift kodeord, prøvede at ændre kodeord uden at være i skift kodeord flow, person = " + ((person != null) ? person.getId() : "<null>"));

        	return new ModelAndView("redirect:/sso/saml/changepassword");
        }

        // Get Person object and check if they are allowed to change password
        String redirectUrl = "/sso/saml/changepassword";
        String successRedirect = sessionHelper.getPasswordChangeSuccessRedirect();
        boolean failureAndRedirect = false;

        Person person = sessionHelper.getPerson();
        if (person == null) {
        	log.warn("person is null - redirecting to change password page");

        	failureAndRedirect = true;
        	redirectUrl += "?errCode=PERSON";
        }
        else if (!isAllowedChangePassword(person)) {
        	log.warn("person has accessed page without correct credentials - " + (person != null ? person.getId() : "<null>"));
        	
        	failureAndRedirect = true;
        	if (person.isNsisAllowed()) {
        		redirectUrl += "?errCode=SUBSTANTIAL";
        	}
        	else {
        		redirectUrl += "?errCode=MFA";
        	}
        }
        else if (isInCanNotChangePasswordGroup(person)) {
        	log.warn("person is not allowed to change password (group restriction) - " + (person != null ? person.getId() : "<null>"));
        	
        	failureAndRedirect = true;
        	redirectUrl += "?errCode=GROUP";
        }
        else if (changedPasswordTooManyTimes(person)) {
        	log.warn("person is not allowed to change password (too many changes in a single day) - " + (person != null ? person.getId() : "<null>"));

        	redirectUrl += "?errCode=LIMIT";
        	failureAndRedirect = true;
        }
        
        if (failureAndRedirect) {
            sessionHelper.clearSession();

            redirectUrl += StringUtils.hasLength(successRedirect) ? "&redirectUrl=" + successRedirect : "";
            return new ModelAndView("redirect:" + redirectUrl);
        }

        // Check for password errors
        if (bindingResult.hasErrors()) {
            ChangePasswordResult reason = sessionHelper.getPasswordChangeFailureReason();
            if (reason != null && !reason.equals(ChangePasswordResult.OK)) {
            	auditLogger.changePasswordFailed(person, reason.getMessage());
            	sessionHelper.setPasswordChangeFailureReason(null);	
            }

            return loginService.continueChangePassword(model, form);
        }

        try {
            ADPasswordStatus adPasswordStatus = personService.changePassword(person, form.getPassword(), false);
            if (ADPasswordResponse.isCritical(adPasswordStatus)) {
            	model.addAttribute("technicalError", true);

                return loginService.continueChangePassword(model, form);
            }

            // Save encrypted password on session (for use in password expiry flow)
            sessionHelper.setPassword(form.getPassword());

			// if activation is initiated
			if (sessionHelper.isInDedicatedActivateAccountFlow()) {
				sessionHelper.setInDedicatedActivateAccountFlow(false);
				model.addAttribute("username", PersonService.getUsername(person));

				return new ModelAndView("activateAccount/activation-completed", model.asMap());
			}
            
            // Show success page
			redirectUrl = sessionHelper.getPasswordChangeSuccessRedirect();
			model.addAttribute("redirectUrl", redirectUrl);
            sessionHelper.setInPasswordChangeFlow(false);

            return new ModelAndView("changePassword/change-password-success", model.asMap());
        }
        catch (NoSuchPaddingException | InvalidKeyException | NoSuchAlgorithmException | IllegalBlockSizeException | BadPaddingException | UnsupportedEncodingException | InvalidAlgorithmParameterException e) {
        	// This should never happen
            throw new ResponderException("Kunne ikke kryptere kodeord");
        }
    }

	private void setRedirectUrl(HttpServletRequest request) {
		String redirectUrl = "";
		String queryString = request.getQueryString();
		if (queryString != null) {
			String[] encodedParameters = queryString.split("&");

			for (String param : encodedParameters) {
				String[] keyValuePair = param.split("=");

				// Find RedirectUrl if present, otherwise set empty string
				if ("redirectUrl".equalsIgnoreCase(keyValuePair[0])) {
					redirectUrl = keyValuePair[1];
				}
			}
		}
		sessionHelper.setPasswordChangeSuccessRedirect(redirectUrl);
	}

    private boolean isAllowedChangePassword(Person person) {
        // Instead of using the computed NSIS level,
        // we check these two levels since it allows someone who is not NSIS SUBSTANTIAL on their person
        // to change password if they have already logged in with NemID
        NSISLevel passwordLevel = sessionHelper.getPasswordLevel();
        NSISLevel mfaLevel = sessionHelper.getMFALevel();

        // If the person is allowed an nsis account we require Substantial to change their password
        if (person.isNsisAllowed() && NSISLevel.SUBSTANTIAL.equalOrLesser(passwordLevel) && NSISLevel.SUBSTANTIAL.equalOrLesser(mfaLevel)) {
            return true;
        }

        // If the person is not allowed an nsis account we only require that hey are logged in and have used an MFA device
        if (!person.isNsisAllowed() && NSISLevel.NONE.equalOrLesser(passwordLevel) && NSISLevel.NONE.equalOrLesser(mfaLevel)) {
            return true;
        }

        return false;
    }

	// check if a group of people who can not change password is set and if the person is member of it
    private boolean isInCanNotChangePasswordGroup(Person person) {
    	PasswordSetting settings = passwordSettingService.getSettings(person.getDomain());
    	if (settings.isCanNotChangePasswordEnabled() && settings.getCanNotChangePasswordGroup() != null && GroupService.memberOfGroup(person, Collections.singletonList(settings.getCanNotChangePasswordGroup()))) {
    		return true;
        }

        return false;
    }
    
	// check if there is a limit of how many times a person can change password a day and then if that limit is exceeded    
    private boolean changedPasswordTooManyTimes(Person person) {
    	PasswordSetting settings = passwordSettingService.getSettings(person.getDomain());
    	if (settings.isMaxPasswordChangesPrDayEnabled() && person.getDailyPasswordChangeCounter() >= settings.getMaxPasswordChangesPrDay()) {
    		return true;
        }

    	return false;
    }
}
