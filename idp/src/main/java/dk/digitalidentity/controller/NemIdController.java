package dk.digitalidentity.controller;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
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
import lombok.extern.slf4j.Slf4j;

@Slf4j
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
	
	@PostMapping("/sso/saml/nemid")
	public ModelAndView loginPost(Model model, @RequestParam Map<String, String> map, HttpServletRequest request, HttpServletResponse response) throws Exception {
		String responseB64 = map.get("response");
		PidAndCprOrError result = nemIDService.verify(responseB64, request);

		AuthnRequest authnRequest = sessionHelper.getAuthnRequest();

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

		// Set authentication levels
		sessionHelper.setPerson(person);
		sessionHelper.setPasswordLevel(NSISLevel.SUBSTANTIAL);
		sessionHelper.setMFALevel(NSISLevel.SUBSTANTIAL);

		// User has now been verified with NemId

		// Go to password change
		if (sessionHelper.isInPasswordChangeFlow()) {
			return loginService.continueChangePassword(model);
		}

		// TODO this logic is now the exact same as in LoginService.continueNemdIDLogin() should probably be merged
		// Check if person has authenticated with their ad password. If they have, replicate the AD password to the nsis password field
		if (sessionHelper.isAuthenticatedWithADPassword() && person.hasNSISUser()) {
			Person adPerson = sessionHelper.getADPerson();
			if (adPerson != null && Objects.equals(adPerson.getId(), person.getId())) {
				if (!StringUtils.isEmpty(person.getAdPassword())) {
					try {
						personService.changePassword(person, person.getAdPassword(), false, true);
						sessionHelper.setAuthenticatedWithADPassword(false);
					} catch (NoSuchPaddingException | InvalidKeyException | NoSuchAlgorithmException | IllegalBlockSizeException | BadPaddingException | UnsupportedEncodingException e) {
						log.error("Kunne ikke kryptere password, password blev derfor ikke ændret", e);
						throw new ResponderException("Der opstod en fejl i skift kodeord");
					}
				}
			}
		}

		// If the SP requires NSIS LOW or above, extra checks required
		ServiceProvider serviceProvider = serviceProviderFactory.getServiceProvider(authnRequest);
		if (NSISLevel.LOW.equalOrLesser(serviceProvider.nsisLevelRequired(authnRequest))) {
			if (!person.isNsisAllowed()) {
				ResponderException e = new ResponderException("Login afbrudt, da brugeren ikke er godkendt til NSIS login");
				errorResponseService.sendError(response, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), StatusCode.REQUESTER, e);
				return null;
			}
		}

		// Has the user accepted the required conditions?
		if (!person.isApprovedConditions()) {
			return loginService.initiateApproveConditions(model);
		}

		// Is the user allowed to get a NSIS User and do the already have one
		if (person.isNsisAllowed() && !person.hasNSISUser()) {
			return loginService.initiateActivateNSISAccount(model, !sessionHelper.isInActivateAccountFlow());
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
