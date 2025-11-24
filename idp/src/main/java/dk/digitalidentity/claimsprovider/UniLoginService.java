package dk.digitalidentity.claimsprovider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.servlet.ModelAndView;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.controller.dto.LoginRequest;
import dk.digitalidentity.samlmodule.model.TokenUser;
import dk.digitalidentity.service.ErrorResponseService;
import dk.digitalidentity.service.FlowService;
import dk.digitalidentity.service.LoginService;
import dk.digitalidentity.service.SessionHelper;
import dk.digitalidentity.util.Constants;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
public class UniLoginService {

	@Autowired
	private HttpServletRequest request;

	@Autowired
	private AuditLogger auditLogger;

	@Autowired
	private SessionHelper sessionHelper;

	@Autowired
	private FlowService flowService;

	@Autowired
	private LoginService loginService;

	@Autowired
	private ErrorResponseService errorResponseService;
	
	@Autowired
	private PersonService personService;

	public ModelAndView uniLoginComplete(Model model, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, TokenUser tokenUser) throws RequesterException, ResponderException {
		
		// get UniID
		String uniID = null;
		Map<String, Object> attributes = tokenUser.getAttributes();
		if (!attributes.containsKey(Constants.STIL_UNID)) {
			return handleUniLoginErrors(httpServletResponse, "Brugertoken fra STIL UniLogin indeholder ikke et UniID");
		}
		else {
			uniID = (String) attributes.get(Constants.STIL_UNID);
		}

		String cpr = null;
		List<Person> availablePeople = personService.getByUniID(uniID);
		if (availablePeople != null && availablePeople.size() > 0) {
			for (Person person : availablePeople) {
				if (cpr == null) {
					cpr = person.getCpr();
				}
				else {
					if (!cpr.equals(person.getCpr())) {
						return handleUniLoginErrors(httpServletResponse, "UniID fra STIL UniLogin matchede flere forskellige personer");						
					}
				}
			}
		}

		String rawToken = tokenUser.getAndClearRawToken();

		// Spring Authorization Server does not play well with a full authenticated Authentication object, and since we do
		// not actually need it from this point on, we can just wipe it and replace it with an AnonymousAuthenticationToken
		if (SecurityContextHolder.getContext() != null &&
			SecurityContextHolder.getContext().getAuthentication() != null) {

			SecurityContext securityContext = SecurityContextHolder.getContext();
			Object principal = securityContext.getAuthentication().getPrincipal();

			ArrayList<GrantedAuthority> authorities = new ArrayList<GrantedAuthority>();
			authorities.add(new SimpleGrantedAuthority("USER"));
			Authentication authentication = new AnonymousAuthenticationToken(tokenUser.getUsername(), principal, authorities);
			SecurityContextHolder.getContext().setAuthentication(authentication);
			
			if (request != null) {
				HttpSession session = request.getSession(true);
				session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
			}
		}

		auditLogger.usedUniLogin(sessionHelper.getPerson(), uniID, rawToken);

		// check if we have a person on the session, if we do we will only work with this person
		Person person = sessionHelper.getPerson();

		// if no existing person is found on the session: treat as fresh login
		if (person == null) {
			// persons locked by 3rd party (municipality, admin or cpr) are filtered out
			availablePeople = availablePeople.stream()
					.filter(p -> !(p.isLockedAdmin() || p.isLockedCivilState() || p.isLockedDataset()))
					.collect(Collectors.toList());

			// if no active user can be found that matches UniID, we error out
			if (availablePeople.isEmpty()) {
				auditLogger.rejectedUnknownPerson(null, null, uniID);
				return new ModelAndView("error-unknown-user");
			}

			// handle multiple user accounts
			if (availablePeople.size() != 1) {
				return flowService.initiateUserSelect(model, availablePeople, NSISLevel.NONE, sessionHelper.getLoginRequest(), httpServletRequest, httpServletResponse);
			}
			else {
				// if we only have one person use that one
				person = availablePeople.get(0);
			}
		}

		// the specific person has now been determined.
		return loginService.continueLoginWithMitIdOrNemId(person, NSISLevel.NONE, sessionHelper.getLoginRequest(), httpServletRequest, httpServletResponse, model);
	}

	private ModelAndView invalidateSessionAndSendRedirect() {
		log.warn("No authnRequest found on session, redirecting to index page");

		return new ModelAndView("redirect:/");
	}

	private ModelAndView handleUniLoginErrors(HttpServletResponse httpServletResponse, String errMsg) throws ResponderException, RequesterException {
		LoginRequest loginRequest = sessionHelper.getLoginRequest();
		if (loginRequest == null) {
			log.warn(errMsg);
			return invalidateSessionAndSendRedirect();
		}

		errorResponseService.sendError(httpServletResponse, loginRequest, new ResponderException(errMsg));
		return null;
	}
}
