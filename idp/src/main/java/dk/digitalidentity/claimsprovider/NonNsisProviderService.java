package dk.digitalidentity.claimsprovider;

import java.util.ArrayList;
import java.util.List;
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
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.ModelAndView;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.controller.dto.LoginRequest;
import dk.digitalidentity.samlmodule.model.TokenUser;
import dk.digitalidentity.samlmodule.util.exceptions.ExternalException;
import dk.digitalidentity.service.FlowService;
import dk.digitalidentity.service.SessionHelper;
import dk.digitalidentity.service.serviceprovider.SelfServiceServiceProvider;
import dk.digitalidentity.service.serviceprovider.ServiceProvider;
import dk.digitalidentity.service.serviceprovider.ServiceProviderFactory;
import dk.digitalidentity.util.IdPFlowException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
public class NonNsisProviderService {

	@Autowired
	private AuditLogger auditLogger;

	@Autowired
	private SessionHelper sessionHelper;

	@Autowired
	private FlowService flowService;

	@Autowired
	private PersonService personService;
	
	@Autowired
	private ServiceProviderFactory serviceProviderFactory;

	public ModelAndView nonNsisLoginComplete(Model model, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, TokenUser tokenUser) throws IdPFlowException, ExternalException {
		LoginRequest loginRequest = sessionHelper.getLoginRequest();
		if (loginRequest == null) {
			throw new ExternalException("Intet loginRequest på sessionen");
		}
		
        ServiceProvider serviceProvider = serviceProviderFactory.getServiceProvider(loginRequest);
        if (serviceProvider == null) {
        	throw new ExternalException("Ingen tjenesteudbyder der matcher loginRequestet på sessionen");
        }

        if (serviceProvider.nsisLevelRequired(loginRequest).isGreater(NSISLevel.NONE) || serviceProvider instanceof SelfServiceServiceProvider) {
        	throw new ExternalException("Login til denne tjeneste er ikke muligt med denne loginmetode");
        }
		
		String userId = tokenUser.getUsername();
		if (!StringUtils.hasLength(userId)) {
			throw new ExternalException("Intet NameID i modtaget token");
		}

		List<Person> availablePeople = personService.getBySamaccountName(userId);
		if (availablePeople == null || availablePeople.size() == 0) {
			throw new ExternalException("Ingen personer matcher userId=" + userId);
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
			
			if (httpServletRequest != null) {
				HttpSession session = httpServletRequest.getSession(true);
				session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
			}
		}

		// persons locked by 3rd party (municipality, admin or cpr) are filtered out
		availablePeople = availablePeople.stream()
				.filter(p -> !(p.isLockedAdmin() || p.isLockedCivilState() || p.isLockedDataset()))
				.collect(Collectors.toList());

		// if no active user can be found that matches UniID, we error out
		if (availablePeople.isEmpty()) {
			auditLogger.rejectedUnknownPerson(null, null, userId);

			throw new ExternalException("Ingen ikke-låste personer matcher userId=" + userId);
		}
		
		sessionHelper.setPasswordLevel(NSISLevel.NONE);
		sessionHelper.setAuthenticatedWithADPassword(true);

		// handle multiple user accounts
		if (availablePeople.size() != 1) {
			sessionHelper.setInNonNsisIdPLoginFlow(true);
			return flowService.initiateUserSelect(model, availablePeople);
		}

		Person person = availablePeople.get(0);
		sessionHelper.setPerson(person);

		auditLogger.externalIdPUsed(person, rawToken);

        return flowService.initiateFlowOrSendLoginResponse(model, httpServletResponse, httpServletRequest, person);
	}
}
