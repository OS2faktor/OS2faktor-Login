package dk.digitalidentity.nemlogin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.samlmodule.model.TokenUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import dk.digitalidentity.common.dao.model.Person;

@Component
public class NemLoginUtil {

	@Autowired
	private HttpServletRequest request;

	@Autowired
	private PersonService personService;
	
	/**
	 * returns true if a person is currently logged in
	 */
	public boolean isAuthenticated() {
		if (SecurityContextHolder.getContext().getAuthentication() != null &&
			SecurityContextHolder.getContext().getAuthentication().getDetails() != null &&
			SecurityContextHolder.getContext().getAuthentication().getDetails() instanceof TokenUser) {
			return true;
		}

		return false;
	}
	
	/**
	 * returns the actual TokenUser object stored on the session for the currently logged in person
	 */
	public TokenUser getTokenUser() {
		if (!isAuthenticated()) {
			return null;
		}
		
		return (TokenUser) SecurityContextHolder.getContext().getAuthentication().getDetails();
	}
	
	/**
	 * Update the tokenUser object on the session with the supplied people
	 */
	public void updateTokenUser(TokenUser tokenUser) {
		// so spring really likes caching this object, so we need to make sure Spring knows about the new version
		if (SecurityContextHolder.getContext() != null &&
			SecurityContextHolder.getContext().getAuthentication() != null &&
			SecurityContextHolder.getContext().getAuthentication() instanceof UsernamePasswordAuthenticationToken) {

			SecurityContext securityContext = SecurityContextHolder.getContext();
			UsernamePasswordAuthenticationToken authentication = (UsernamePasswordAuthenticationToken) securityContext.getAuthentication();
			
			authentication.setDetails(tokenUser);
			SecurityContextHolder.getContext().setAuthentication(authentication);
			
			if (request != null) {
				HttpSession session = request.getSession(true);
				session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
			}
		}
	}

	public String getCpr() {
		if (!isAuthenticated()) {
			return null;
		}

		TokenUser tokenUser = (TokenUser) SecurityContextHolder.getContext().getAuthentication().getDetails();

		Map<String, Object> attributes = tokenUser.getAttributes();
		if (attributes == null || attributes.isEmpty()) {
			return null;
		}

		Object cprObj = attributes.get("https://data.gov.dk/model/core/eid/cprNumber");
		if (!(cprObj instanceof String)) {
			return null;
		}
		return (String) cprObj;
	}

	public List<Person> getAvailablePeople() {
		ArrayList<Person> people = new ArrayList<>();
		if (!isAuthenticated()) {
			return people;
		}

		String cpr = getCpr();
		if (!StringUtils.hasLength(cpr)) {
			return people;
		}

		return personService.getByCpr(cpr);
	}
}
