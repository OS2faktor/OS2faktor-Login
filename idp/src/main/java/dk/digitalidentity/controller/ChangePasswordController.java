package dk.digitalidentity.controller;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.service.PasswordSettingService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.controller.dto.PasswordChangeForm;
import dk.digitalidentity.controller.validator.PasswordChangeValidator;
import dk.digitalidentity.service.AuthnRequestHelper;
import dk.digitalidentity.service.ErrorResponseService;
import dk.digitalidentity.service.LoginService;
import dk.digitalidentity.service.SessionHelper;
import dk.digitalidentity.service.model.enums.RequireNemIdReason;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import lombok.extern.slf4j.Slf4j;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.StatusCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

@Slf4j
@Controller
public class ChangePasswordController {

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

    @Autowired
    private AuthnRequestHelper authnRequestHelper;

    @InitBinder("passwordForm")
    public void initClientBinder(WebDataBinder binder) {
        binder.setValidator(passwordChangeFormValidator);
    }

    @GetMapping("/sso/saml/changepassword")
    public ModelAndView changePassword(Model model, HttpServletRequest request, HttpServletResponse response, @RequestParam(name = "redirectUrl", required = false) String url) throws ResponderException, RequesterException {
        // Remember where to redirect to
        sessionHelper.setPasswordChangeSuccessRedirect(url);

        // Show NemId login form
        sessionHelper.setInPasswordChangeFlow(true);
        sessionHelper.setPassword(null); // This will allow you to change password on any of your users regardless of which you're currently logged in with

        // Instead of using the computed NSIS level,
        // we check these two levels since it allows someone who is not NSIS SUBSTANTIAL on their person
        // to change password if they have already logged in with NemID
        NSISLevel passwordLevel = sessionHelper.getPasswordLevel();
        NSISLevel mfaLevel = sessionHelper.getMFALevel();
        Person person = sessionHelper.getPerson();
        if (person != null && NSISLevel.SUBSTANTIAL.equalOrLesser(passwordLevel) && NSISLevel.SUBSTANTIAL.equalOrLesser(mfaLevel)) {
            try {
                return loginService.continueChangePassword(model);
            } catch (RequesterException e) {
                AuthnRequest authnRequest = sessionHelper.getAuthnRequest();
                sessionHelper.clearSession();
                ResponderException ex = new ResponderException("Fejl opstået ved skift kodeord");
                if (authnRequest != null) {
                    errorResponseService.sendError(response, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.RESPONDER, ex);
                    return null;
                }
                else {
                    log.warn("No AuthnRequest on session error thrown since error destination unknown");
                    throw ex;
                }
            }
        } else {
            return loginService.initiateNemIDOnlyLogin(model, request, RequireNemIdReason.CHANGE_PASSWORD);
        }
    }

    @PostMapping("/sso/saml/changepassword")
    public ModelAndView changePasswordPost(Model model, @Valid @ModelAttribute("passwordForm") PasswordChangeForm form, BindingResult bindingResult) throws ResponderException {
        // Get Person object
        Person person = sessionHelper.getPerson();
        if (person == null) {
            sessionHelper.clearSession();
            log.warn("User entered changepassword with bad session, clearing");

            String redirectUrl = "sso/saml/changepassword";
            String successRedirect = sessionHelper.getPasswordChangeSuccessRedirect();
            redirectUrl += !StringUtils.isEmpty(successRedirect) ? "?redirectUrl=" + successRedirect : "";
            return new ModelAndView("redirect:" + redirectUrl);
        }

        // Check for password errors
        if (bindingResult.hasErrors()) {
            model.addAttribute("settings", passwordSettingService.getSettings(person.getDomain()));
            return new ModelAndView("changePassword/change-password", model.asMap());
        }

        try {
            boolean success = personService.changePassword(person, form.getPassword());

            // If not a success return to password change page
            if (!success) {
                bindingResult.rejectValue("password", "page.selfservice.changePassword.error.rules");
                model.addAttribute("settings", passwordSettingService.getSettings(person.getDomain()));
                return new ModelAndView("changePassword/change-password", model.asMap());
            }

            // Save encrypted password on session (for use in password expiry flow)
            sessionHelper.setPassword(form.getPassword());

            // Show success page
			String redirectUrl = sessionHelper.getPasswordChangeSuccessRedirect();
			model.addAttribute("redirectUrl", redirectUrl);
            sessionHelper.setInPasswordChangeFlow(false);
            return new ModelAndView("changePassword/change-password-success", model.asMap());
        } catch (NoSuchPaddingException | InvalidKeyException | NoSuchAlgorithmException | IllegalBlockSizeException | BadPaddingException | UnsupportedEncodingException e) {
            throw new ResponderException("Kunne ikke kryptere kodeord"); // This should never happen
        }
    }
}
