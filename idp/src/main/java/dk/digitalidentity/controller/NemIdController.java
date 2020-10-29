package dk.digitalidentity.controller;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.StatusCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.service.AuthnRequestHelper;
import dk.digitalidentity.service.ErrorResponseService;
import dk.digitalidentity.service.LoginService;
import dk.digitalidentity.service.NemIDService;
import dk.digitalidentity.service.SessionHelper;
import dk.digitalidentity.service.model.PidAndCprOrError;
import dk.digitalidentity.service.serviceprovider.SelfServiceServiceProvider;
import dk.digitalidentity.service.serviceprovider.ServiceProvider;
import dk.digitalidentity.service.serviceprovider.ServiceProviderFactory;
import dk.digitalidentity.util.ResponderException;

@Controller
public class NemIdController {

	@Autowired
	private NemIDService nemIDService;

	@Autowired
	private SessionHelper sessionHelper;

	@Autowired
	private ErrorResponseService errorResponseService;

	@Autowired
	private PersonService personService;

	@Autowired
	private AuthnRequestHelper authnRequestHelper;

	@Autowired
	private ServiceProviderFactory serviceProviderFactory;

	@Autowired
	private LoginService loginService;

	//TODO this logic is now almost the exact same as in LoginService.continueNemdIDLogin() should probably be merged
	@PostMapping("/sso/saml/nemid")
	public ModelAndView loginPost(Model model, @RequestParam Map<String, String> map, HttpServletRequest request, HttpServletResponse response) throws Exception {
		String responseB64 = map.get("response");
		PidAndCprOrError result = nemIDService.verify(responseB64, request);

		AuthnRequest authnRequest = sessionHelper.getAuthnRequest();
		ServiceProvider serviceProvider = serviceProviderFactory.getServiceProvider(authnRequest);

		// Check if NemID was successful
		if (result.hasError()) {
			errorResponseService.sendError(response, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.RESPONDER, new ResponderException(result.getError()));

			return null;
		}
		sessionHelper.setNemIDPid(result.getPid());

		// Check if we have a person on the session, if we do we will only work with this person
		Person person = sessionHelper.getPerson();

		// If null treat as fresh login
		if (person == null) {
			List<Person> people = personService.getByCpr(result.getCpr());
			if (people == null || people.size() == 0) {
				return new ModelAndView("error-person-not-in-system");
			}
			else if (people.size() != 1) {
				return loginService.initiateUserSelect(model, people, true);
			}
			else {
				// We only have one person, and none on the session
				person = people.get(0);
			}
		}

		// We already have a person on the session, we only accept the nemid login if the cpr matches what we have on session
		if (result.getCpr() == null || !Objects.equals(result.getCpr(), person.getCpr())) {
			sessionHelper.clearSession();
			errorResponseService.sendError(response, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.RESPONDER,
					new ResponderException("Bruger foretog NemId login med et andet CPR end der allerede var gemt på sessionen"));
			return null;
		}

		// Check if person has authenticated with their ad password. If they have, replicate the AD password to the nsis password field
		if (sessionHelper.isAuthenticatedWithADPassword() && person.hasNSISUser()) {
			Person adPerson = sessionHelper.getADPerson();
			if (adPerson != null && Objects.equals(adPerson.getId(), person.getId())) {
				if (!StringUtils.isEmpty(person.getAdPassword())) {
					person.setNsisPassword(person.getAdPassword());
					personService.save(person);

					sessionHelper.setAuthenticatedWithADPassword(false);
				}
			}
		}

		// Set authentication levels
		sessionHelper.setPerson(person);
		sessionHelper.setPasswordLevel(NSISLevel.SUBSTANTIAL);
		sessionHelper.setMFALevel(NSISLevel.SUBSTANTIAL);

		// Check confirmed conditions
		if (!person.isApprovedConditions() && NSISLevel.LOW.equalOrLesser(serviceProvider.nsisLevelRequired(authnRequest))) {
			return loginService.initiateAcceptTerms(model);
		}

		// If trying to login to anything else than selfservice check if person is locked
		if (!(serviceProvider instanceof SelfServiceServiceProvider)) {
			if (person.isLocked()) {
				return new ModelAndView("error-locked-account");
			}
		}

		return loginService.initiateFlowOrCreateAssertion(model, response, request, person);
	}
}
