package dk.digitalidentity.nemlogin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.config.Constants;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.samlmodule.model.TokenUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class NemLoginUtil {

	@Autowired
	private HttpServletRequest request;

	@Autowired
	private PersonService personService;

	@Autowired
	private CommonConfiguration commonConfiguration;

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
			SecurityContextHolder.getContext().getAuthentication() instanceof AbstractAuthenticationToken) {
			
			SecurityContext securityContext = SecurityContextHolder.getContext();
			AbstractAuthenticationToken authentication = (AbstractAuthenticationToken) securityContext.getAuthentication();
			
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

	public String getCorporateNemLoginUuid() {
		if (!isAuthenticated()) {
			return null;
		}

		TokenUser tokenUser = (TokenUser) SecurityContextHolder.getContext().getAuthentication().getDetails();

		Map<String, Object> attributes = tokenUser.getAttributes();
		if (attributes == null || attributes.isEmpty()) {
			return null;
		}

		Object uuidObj = attributes.get("https://data.gov.dk/model/core/eid/professional/uuid/persistent");
		if (uuidObj == null || !(uuidObj instanceof String)) {
			return null;
		}

		return (String) uuidObj;
	}

	public List<Person> getAvailablePeople() {
		ArrayList<Person> people = new ArrayList<>();
		if (!isAuthenticated()) {
			return people;
		}

		String cpr = getCpr();

		// first find by CPR
		if (StringUtils.hasText(cpr)) {
			List<Person> persons = personService.getByCpr(cpr);
			people.addAll(persons);
		}

		// then, if enabled, find by NemLog-in UUID
		if (commonConfiguration.getMitIdErhverv().isEnabled()) {
			String uuid = getCorporateNemLoginUuid();
			if (StringUtils.hasText(uuid)) {
				List<Person> corporatePersons = personService.getByExternalNemloginUserUuid(uuid);
				
				for (Person corporatePerson : corporatePersons) {
					// if we get a cpr - and none is stored on the person, we should update
					if (StringUtils.hasText(cpr) && Constants.NO_CPR_VALUE.equals(corporatePerson.getCpr())) {
						corporatePerson.setCpr(cpr);
						corporatePerson.setMitIdNameId(uuid);
						personService.save(corporatePerson);
					}
					
					// if we get a CPR from NL3, and this CPR does not match the person, we log a warn and skip,
					// as this indicates bad data
					if (StringUtils.hasText(cpr) && !cpr.equals(corporatePerson.getCpr())) {
						log.warn("Got a CPR from NL3 that does not match the CPR on person " + corporatePerson.getId() + " with userId " + corporatePerson.getSamaccountName());
						continue;
					}

					people.add(corporatePerson);
				}
			}
		}

		return people;
	}
}
