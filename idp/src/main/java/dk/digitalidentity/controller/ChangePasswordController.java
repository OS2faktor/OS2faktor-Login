package dk.digitalidentity.controller;

import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Objects;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

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
import dk.digitalidentity.common.service.GroupService;
import dk.digitalidentity.common.service.PasswordSettingService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.enums.ChangePasswordResult;
import dk.digitalidentity.common.service.model.ADPasswordResponse;
import dk.digitalidentity.common.service.model.ADPasswordResponse.ADPasswordStatus;
import dk.digitalidentity.controller.dto.PasswordChangeForm;
import dk.digitalidentity.controller.validator.PasswordChangeValidator;
import dk.digitalidentity.service.ErrorResponseService;
import dk.digitalidentity.service.FlowService;
import dk.digitalidentity.service.PasswordService;
import dk.digitalidentity.service.SessionHelper;
import dk.digitalidentity.service.model.enums.PasswordValidationResult;
import dk.digitalidentity.service.model.enums.RequireNemIdReason;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
public class ChangePasswordController {

    @Autowired
    private SessionHelper sessionHelper;

	@Autowired
	private FlowService flowService;

    @Autowired
    private PersonService personService;

    @Autowired
    private PasswordSettingService passwordSettingService;

    @Autowired
    private PasswordChangeValidator passwordChangeFormValidator;

    @Autowired
    private ErrorResponseService errorResponseService;

	@Autowired
	private PasswordService passwordService;

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
            return flowService.initiateNemIDOnlyLogin(model, request, RequireNemIdReason.CHANGE_PASSWORD, null);
        }

        return flowService.continueChoosePasswordResetOrUnlockAccount(model);
    }

    @GetMapping("/sso/saml/changepassword")
    public ModelAndView changePassword(Model model, HttpServletRequest request, HttpServletResponse response, @RequestParam(name = "redirectUrl", required = false, defaultValue = "") String url, @RequestParam(name = "errCode", required = false) String errCode) throws ResponderException, RequesterException {
        // remember where to redirect to
        sessionHelper.setPasswordChangeSuccessRedirect(url);

        // show MitID login form
        sessionHelper.setInPasswordChangeFlow(true);

        // if there is no person on the session initiate login
        Person person = sessionHelper.getPerson();
        if (person == null || sessionHelper.hasNSISUserAndLoggedInWithNSISNone()) {
            return flowService.initiateNemIDOnlyLogin(model, request, RequireNemIdReason.CHANGE_PASSWORD, errCode);
        }
        
        // if the person has not approved the conditions then do that first
		if (personService.requireApproveConditions(person)) {
			sessionHelper.setInChangePasswordFlowAndHasNotApprovedConditions(true);
			return flowService.initiateApproveConditions(model);
		}

		// if the user is allowed to activate their NSIS account and have not done so yet,
		// we should prompt first since they will not set their NSIS password without activating first
		if (person.isNsisAllowed() && !person.hasActivatedNSISUser() && !person.isLockedPerson()) {
			return flowService.initiateActivateNSISAccount(model, true);
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
                return flowService.continueChangePassword(model);
            }
        }
        catch (RequesterException e) {
            errorResponseService.sendError(response, sessionHelper.getLoginRequest(), new ResponderException("Fejl opstået ved skift kodeord"));
            return null;
        }

        // let's prevent students from ever being send to NemLog-in - better to show an error
        if (personService.isStudent(person)) {
            errorResponseService.sendError(response, sessionHelper.getLoginRequest(), new ResponderException("Ikke muligt at skifte kodeord på studerende"));
            return null;
        }
        
        // Reach here if we have a person on the session but they are not logged in to the correct level
        return flowService.initiateNemIDOnlyLogin(model, request, RequireNemIdReason.CHANGE_PASSWORD);
    }

    @PostMapping("/sso/saml/changepassword")
    public ModelAndView changePasswordPost(HttpServletResponse response, Model model, @Valid @ModelAttribute("passwordForm") PasswordChangeForm form, BindingResult bindingResult) throws ResponderException, RequesterException {
        if (!sessionHelper.isInPasswordChangeFlow()) {
        	if (sessionHelper.getLoginRequest() != null) {
	            ResponderException ex = new ResponderException("Fejl opstået ved skift kodeord, prøvede at ændre kodeord uden at være i skift kodeord flow");
	            errorResponseService.sendError(response, sessionHelper.getLoginRequest(), ex);
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
            	sessionHelper.setPasswordChangeFailureReason(null);	
            }

            return flowService.continueChangePassword(model, form);
        }

        try {
			ADPasswordStatus adPasswordStatus = personService.changePassword(person, form.getPassword());
			if (ADPasswordResponse.isCritical(adPasswordStatus)) {
				if (adPasswordStatus.equals(ADPasswordStatus.INSUFFICIENT_PERMISSION)) {
					model.addAttribute("insufficientPermission", true);
				}
				else {
					model.addAttribute("technicalError", true);
				}

				return flowService.continueChangePassword(model, form);
			}

            // save encrypted password on session (for use in password expiry flow)
            sessionHelper.setPassword(form.getPassword());
            
            // no longer in change password flow
            sessionHelper.setInPasswordChangeFlow(false);

			// if activation is initiated, hijack flow and send them to the completed page
			if (sessionHelper.isInDedicatedActivateAccountFlow()) {
				sessionHelper.setInDedicatedActivateAccountFlow(false);
				model.addAttribute("username", PersonService.getUsername(person));
				model.addAttribute("linkToSelfservice", true);

				return new ModelAndView("activateAccount/activation-completed", model.asMap());
			}

            // if this is not part of a login flow - then wipe the session (otherwise users might access change-password, and
            // then just leave the browser afterwards, and we don't want then to stay logged in in that case
			redirectUrl = sessionHelper.getPasswordChangeSuccessRedirect();
			if (!Objects.equals("/sso/saml/login/continueLogin", redirectUrl) || sessionHelper.getLoginRequest() == null) {
				sessionHelper.clearSession(false);
			}

            // show success page
			model.addAttribute("redirectUrl", redirectUrl);

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

    	// if the user has logged in using an MFA device (including MitID) with NSIS level substantial,
    	// they are always allowed to change password
        NSISLevel mfaLevel = sessionHelper.getMFALevel();
        
        if (NSISLevel.SUBSTANTIAL.equalOrLesser(mfaLevel)) {
        	return true;
        }
        
        // if we are forcing a password change, they are allowed to change the password if the
    	// previously set password on the session is still valid (i.e. the ordinary change password case).
    	// note that these cases only work for users with a registered password, and hence an NSIS account
		if (sessionHelper.isInForceChangePasswordFlow() || sessionHelper.isInPasswordExpiryFlow()) {
			String password = sessionHelper.getPassword();

			if (StringUtils.hasLength(password)) {
				PasswordValidationResult passwordValidationResult = null;

				if (personService.isStudent(person)) {
					// for standalone setup we validate against the studentPassword field, otherwise we validate against AD
					// before performing the password change
					passwordValidationResult = passwordService.validatePassword(password, person);
				}
				else if (!person.isNsisAllowed()) {
					// verify session password, including validation against AD if available
					passwordValidationResult = passwordService.validatePassword(password, person);					
				}
				else {
					// no fallback to AD, this needs to be a validation against the NSIS password
					passwordValidationResult = passwordService.validatePasswordNoAD(password, person);
				}

				switch (passwordValidationResult) {
					case VALID:
					case VALID_EXPIRED:
					case VALID_BUT_BAD_PASWORD:
						return true;
					case INVALID:
					case LOCKED:
					case TECHNICAL_ERROR:
					case INSUFFICIENT_PERMISSION:
					case INVALID_BAD_PASSWORD:
						return false;
				}
			}
		}

		// nope - do some MitID stuff to change your password
        return false;
    }

	// check if a group of people who can not change password is set and if the person is member of it
    private boolean isInCanNotChangePasswordGroup(Person person) {
    	PasswordSetting settings = passwordSettingService.getSettingsCached(passwordSettingService.getSettingsDomainForPerson(person));
    	if (settings.isCanNotChangePasswordEnabled() && settings.getCanNotChangePasswordGroup() != null && GroupService.memberOfGroup(person, Collections.singletonList(settings.getCanNotChangePasswordGroup()))) {
    		return true;
        }

        return false;
    }
    
	// check if there is a limit of how many times a person can change password a day and then if that limit is exceeded    
    private boolean changedPasswordTooManyTimes(Person person) {
    	PasswordSetting settings = passwordSettingService.getSettingsCached(passwordSettingService.getSettingsDomainForPerson(person));
    	if (settings.isMaxPasswordChangesPrDayEnabled() && person.getDailyPasswordChangeCounter() >= settings.getMaxPasswordChangesPrDay()) {
    		return true;
        }

    	return false;
    }
}
