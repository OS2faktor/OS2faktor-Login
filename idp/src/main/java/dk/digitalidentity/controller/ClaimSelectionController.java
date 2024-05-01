package dk.digitalidentity.controller;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import dk.digitalidentity.controller.dto.LoginRequest;
import dk.digitalidentity.service.FlowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.controller.dto.ClaimValueDTO;
import dk.digitalidentity.service.ErrorHandlingService;
import dk.digitalidentity.service.ErrorResponseService;
import dk.digitalidentity.service.SessionHelper;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;

@Controller
public class ClaimSelectionController {

	@Autowired
	private ErrorHandlingService errorHandlingService;

	@Autowired
	private ErrorResponseService errorResponseService;

	@Autowired
	private SessionHelper sessionHelper;

	@Autowired
	private FlowService flowService;

	@PostMapping(value = "/sso/saml/claims/completed", consumes = { MediaType.APPLICATION_FORM_URLENCODED_VALUE })
	public ModelAndView mfaChallengePage(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Model model, @RequestParam Map<String, String> selectedParameters) throws ResponderException, RequesterException {

		if (sessionHelper.isInActivateAccountFlow()) {
			return handleError(httpServletRequest, httpServletResponse, model, "/sso/saml/claims/completed", "Bruger tilgik valg af claims men var ikke i det korrekte flow");
		}

		Person person = sessionHelper.getPerson();
		if (person == null) {
			return handleError(httpServletRequest, httpServletResponse, model, "/sso/saml/claims/completed", "Ingen bruger på sessionen");
		}

		// TODO: possibly find a better way to handle csrf
		selectedParameters.remove("_csrf"); // no need to keep this
		if (selectedParameters.isEmpty()) {
			return handleError(httpServletRequest, httpServletResponse, model, "/sso/saml/claims/completed", "Ingen claim valg modtaget");
		}

		// Sanity check that the users only returned decisions on claims that we gave them
		Map<String, ClaimValueDTO> selectableClaims = sessionHelper.getSelectableClaims();
		for (Map.Entry<String, String> claim : selectedParameters.entrySet()) {
			ClaimValueDTO claimValueDTO = selectableClaims.get(claim.getKey());

			if (claimValueDTO == null || claimValueDTO.getAcceptedValues() == null || !claimValueDTO.getAcceptedValues().contains(claim.getValue())) {
				return handleError(httpServletRequest, httpServletResponse, model, "/sso/saml/claims/completed", "Fandt claim valg der ikke er gyldig");
			}
		}

		sessionHelper.setSelectedClaims(selectedParameters);

		return flowService.initiateFlowOrSendLoginResponse(model, httpServletResponse, httpServletRequest, person);
	}

	private ModelAndView handleError(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Model model, String location, String message) throws ResponderException, RequesterException {
		LoginRequest loginRequest = sessionHelper.getLoginRequest();
		if (loginRequest != null) {
			errorResponseService.sendError(httpServletResponse, loginRequest, new RequesterException(message));
			return null;
		}
		else {
			ModelAndView destination = errorHandlingService.modelAndViewError(location, httpServletRequest, message, model);
			sessionHelper.invalidateSession();
			
			return destination;
		}
	}
}
