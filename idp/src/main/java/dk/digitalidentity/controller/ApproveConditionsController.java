package dk.digitalidentity.controller;

import java.time.LocalDateTime;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.CprService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.controller.dto.LoginRequest;
import dk.digitalidentity.controller.dto.ValidateADPasswordForm;
import dk.digitalidentity.service.ErrorHandlingService;
import dk.digitalidentity.service.ErrorResponseService;
import dk.digitalidentity.service.FlowService;
import dk.digitalidentity.service.SessionHelper;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
public class ApproveConditionsController {
	
	@Autowired
	private PersonService personService;
	
	@Autowired
	private AuditLogger auditLogger;

	@Autowired
	private SessionHelper sessionHelper;

	@Autowired
	private ErrorHandlingService errorHandlingService;

	@Autowired
	private ErrorResponseService errorResponseService;

	@Autowired
	private FlowService flowService;

	@Autowired
	private CprService cprService;

	// Hitting this endpoint is not intended, but if a user does it is likely due to hitting the back button in the login flow.
	@GetMapping("/vilkaar/godkendt")
	public ModelAndView approvedConditionsGet(Model model, HttpServletResponse httpServletResponse, HttpServletRequest httpServletRequest) throws ResponderException, RequesterException {
		Person person = sessionHelper.getPerson();
		LoginRequest loginRequest = sessionHelper.getLoginRequest();
		RequesterException ex = new RequesterException("Tilgik '/vilkaar/godkendt'. Sessionen var ikke korrekt, kan ikke fortsætte login. Prøv igen.");

		if (loginRequest != null) {
			if (person != null) {
				log.warn("Person ("+ person.getId() +") hit GET '/vilkaar/godkendt' likely due to hitting the backbutton. Session is ok. resuming login");

				// person-lock does not block login, only activation of NSIS account (and use of same)
				if (person.isLockedByOtherThanPerson()) {
					return new ModelAndView(PersonService.getCorrectLockedPage(person));
				}

				return flowService.initiateFlowOrSendLoginResponse(model, httpServletResponse, httpServletRequest, person);
			}
			else {
				// No person but we have LoginRequest
				errorResponseService.sendError(httpServletResponse, loginRequest, ex);
				return null;
			}
		}
		else {
			// No AuthnRequest
			ModelAndView modelAndView = errorHandlingService.modelAndViewError("/vilkaar/godkendt", httpServletRequest, "No AuthnRequest on session", model);
			sessionHelper.invalidateSession();
			return modelAndView;
		}
	}

	@PostMapping("/vilkaar/godkendt")
	public ModelAndView approvedConditions(Model model, HttpServletResponse httpServletResponse, HttpServletRequest httpServletRequest, @RequestParam(value="doNotUseCurrentADPassword", required = false, defaultValue = "false") boolean doNotUseCurrentADPassword) throws ResponderException, RequesterException {
		// Access not allowed
		if (!sessionHelper.isInApproveConditionsFlow()) {
			LoginRequest loginRequest = sessionHelper.getLoginRequest();
			if (loginRequest == null) {
				log.warn("No loginRequest found on session");
				return new ModelAndView("redirect:/");
			}

			errorResponseService.sendError(httpServletResponse, loginRequest, new RequesterException("Bruger tilgik accepter vilkår uden at være sendt til dette endpoint af backend"));
			return null;
		}

		// Get person
		Person person = sessionHelper.getPerson();
		if (person == null) {
			LoginRequest loginRequest = sessionHelper.getLoginRequest();
			if (loginRequest == null) {
				log.warn("No loginRequest found on session");
				return new ModelAndView("redirect:/");
			}

			errorResponseService.sendError(httpServletResponse, loginRequest, new RequesterException("Kunne ikke acceptere vilkår, da ingen person var associeret til login sessionen"));
			return null;
		}

		// Save and log Approved conditions
		person.setApprovedConditions(true);
		person.setApprovedConditionsTts(LocalDateTime.now());
		auditLogger.acceptedTermsByPerson(person);

		personService.save(person);

		// After successfully approving conditions revoke access to endpoint so it only happens once
		sessionHelper.setInApproveConditionsFlow(false);

		// if in dedicated activation flow
		if (sessionHelper.isInDedicatedActivateAccountFlow()) {
			String mitIDNameID = sessionHelper.getMitIDNameID();
			NSISLevel passwordLevel = sessionHelper.getPasswordLevel();
			NSISLevel mfaLevel = sessionHelper.getMFALevel();

			if (!StringUtils.hasLength(mitIDNameID) || !NSISLevel.SUBSTANTIAL.equalOrLesser(passwordLevel) || !NSISLevel.SUBSTANTIAL.equalOrLesser(mfaLevel)) {
				sessionHelper.invalidateSession();
				log.warn("Person (" + person.getId() + ") is activation initiated true but has not logged in with NemID or MitID. Bad session, clearing.");

				return new ModelAndView("activateAccount/activate-failed");
			}
			
			if (cprService.checkIsDead(person)) {
				log.error("Could not issue identity to " + person.getId() + " because cpr says the person is dead!");
				return new ModelAndView("activateAccount/activate-failed-dead");
			}

			// Create User for person
			person.setMitIdNameId(mitIDNameID);
			person.setNsisLevel(NSISLevel.SUBSTANTIAL);

			auditLogger.activatedByPerson(person, mitIDNameID);
			
			sessionHelper.setInPasswordChangeFlow(true);
			
            if (doNotUseCurrentADPassword) {
    			sessionHelper.setDoNotUseCurrentADPassword(true);
    		}

    		if (!sessionHelper.isDoNotUseCurrentADPassword() &&
    			!sessionHelper.isAuthenticatedWithADPassword() &&
    			StringUtils.hasLength(person.getSamaccountName()) &&
    			!person.getDomain().isStandalone()) {
    			
    			model.addAttribute("validateADPasswordForm", new ValidateADPasswordForm());
    			return new ModelAndView("activateAccount/activate-validate-ad-password", model.asMap());
    		}
    		
    		return flowService.continueChangePassword(model);
		}
		
		// goto Change Password flow
		if (sessionHelper.isInChangePasswordFlowAndHasNotApprovedConditions() || sessionHelper.isInPasswordChangeFlow()) {
			sessionHelper.setInChangePasswordFlowAndHasNotApprovedConditions(false);

			// if the user is allowed to activate their NSIS account and have not currently done so,
			// we should prompt first since they will not set their NSIS password without activating first
			if (person.isNsisAllowed() && !person.hasActivatedNSISUser() && !person.isLockedPerson()) {
				return flowService.initiateActivateNSISAccount(model, true);
			}

			return flowService.continueChangePassword(model);
		}
		
		// Go to activate account
		if (person.isNsisAllowed() && !person.hasActivatedNSISUser() && !person.isLockedPerson()) {
			return flowService.initiateActivateNSISAccount(model);
		}
		
		// Go to choose password reset or unlock account page
		if (sessionHelper.isInChoosePasswordResetOrUnlockAccountFlow()) {
			return flowService.continueChoosePasswordResetOrUnlockAccount(model);
		}

		// Continue with the login
		return flowService.initiateFlowOrSendLoginResponse(model, httpServletResponse, httpServletRequest, person);
	}
}
