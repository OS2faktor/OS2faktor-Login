package dk.digitalidentity.controller;

import java.time.LocalDateTime;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.StatusCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.ModelAndView;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.service.AuthnRequestHelper;
import dk.digitalidentity.service.ErrorResponseService;
import dk.digitalidentity.service.LoginService;
import dk.digitalidentity.service.SessionHelper;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;

@Controller
public class ApproveConditionsController {
	
	@Autowired
	private PersonService personService;
	
	@Autowired
	private AuditLogger auditLogger;

	@Autowired
	private SessionHelper sessionHelper;

	@Autowired
	private ErrorResponseService errorResponseService;

	@Autowired
	private AuthnRequestHelper authnRequestHelper;

	@Autowired
	private LoginService loginService;

	@PostMapping("/vilkaar/godkendt")
	public ModelAndView approvedConditions(Model model, HttpServletResponse httpServletResponse, HttpServletRequest httpServletRequest) throws ResponderException, RequesterException {
		if (!sessionHelper.isInApproveConditionsFlow()) {
			// Access not allowed
			AuthnRequest authnRequest = sessionHelper.getAuthnRequest();
			errorResponseService.sendError(httpServletResponse, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.REQUESTER, new RequesterException("Bruger tilgik accepter vilkår uden at være sendt til dette endpoint af backend"));
			return null;
		}

		// Get person
		Person person = sessionHelper.getPerson();
		if (person == null) {
			AuthnRequest authnRequest = sessionHelper.getAuthnRequest();
			errorResponseService.sendError(httpServletResponse, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.REQUESTER, new RequesterException("Kunne ikke acceptere vilkår, da ingen person var associeret til login sessionen"));
			return null;
		}

		// Save and log Approved conditions
		person.setApprovedConditions(true);
		person.setApprovedConditionsTts(LocalDateTime.now());
		auditLogger.acceptedTermsByPerson(person);

		personService.save(person);

		// After successfully approving conditions revoke access to endpoint so it only happens once
		sessionHelper.setInApproveConditionsFlow(false);

		// Go to activate account
		if (person.isNsisAllowed() && !person.hasNSISUser()) {
			return loginService.initiateActivateNSISAccount(model);
		}

		// Continue with the login
		return loginService.initiateFlowOrCreateAssertion(model, httpServletResponse, httpServletRequest, person);
	}
}
