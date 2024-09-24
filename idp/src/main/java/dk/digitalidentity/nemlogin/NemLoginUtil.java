package dk.digitalidentity.nemlogin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.samlmodule.model.TokenUser;

@Component
public class NemLoginUtil {

	@Autowired
	private HttpServletRequest request;

	@Autowired
	private PersonService personService;

	@Autowired
	private OS2faktorConfiguration configuration;

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
		if (cprObj == null || !(cprObj instanceof String)) {

			// if pure MitID UUID match is enabled, then use that find to find the cpr, otherwise the cpr attribute IS required
			if (configuration.getMitid().isAllowCachedUuidLookup()) {
				String uuid = getPersonUuid();

				if (uuid != null) {
					List<Person> persons = personService.getByMitIdNameId(uuid);
					if (persons != null && persons.size() > 0) {
						return persons.get(0).getCpr();
					}
				}
			}

			return null;
		}
		
		return (String) cprObj;
	}

	public String getPersonUuid() {
		if (!isAuthenticated()) {
			return null;
		}

		TokenUser tokenUser = (TokenUser) SecurityContextHolder.getContext().getAuthentication().getDetails();

		String mitIdName = tokenUser.getUsername();
		if (mitIdName.startsWith("https://data.gov.dk/model/core/eid/person/uuid/")) {
			mitIdName = mitIdName.substring("https://data.gov.dk/model/core/eid/person/uuid/".length());
		}

		return mitIdName;
	}
	
	public List<Person> getAvailablePeople() {
		ArrayList<Person> people = new ArrayList<>();
		if (!isAuthenticated()) {
			return people;
		}

		String cpr = getCpr();

		return personService.getByCpr(cpr);
	}
}
