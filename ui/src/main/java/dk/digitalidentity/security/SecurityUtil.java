package dk.digitalidentity.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.Group;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.service.GroupService;
import dk.digitalidentity.common.service.PasswordSettingService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.config.Constants;
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.samlmodule.model.SamlGrantedAuthority;
import dk.digitalidentity.samlmodule.model.TokenUser;

@Component
public class SecurityUtil {
	private static final String LOCKED_BY_PERSON_ONLY = "LOCKED_BY_PERSON_ONLY";
	private static final String IS_WITHOUT_USER = "IS_WITHOUT_USER";
	private static final String IS_UNLOCKED = "IS_UNLOCKED";
	private static final String AUTHENTICATION_ASSURANCE_LEVEL = "https://data.gov.dk/concept/core/nsis/aal";

	@Autowired
	private HttpServletRequest request;

	@Autowired
	private OS2faktorConfiguration configuration;

	@Autowired
	private PersonService personService;

	@Autowired
	private PasswordSettingService passwordSettingService;
	
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
	
	public boolean hasRole(String role) {
		if (!isAuthenticated()) {
			return false;
		}

		return getTokenUser().getAuthorities().stream().anyMatch(ga -> Objects.equals(ga.getAuthority(), role));
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
	 * returns the ID of the currently logged in person
	 */
	public Long getPersonId() {
		if (!isAuthenticated()) {
			return null;
		}

		return Long.parseLong((String) SecurityContextHolder.getContext().getAuthentication().getPrincipal());
	}

	/**
	 * Returns the person object of the currently logged in person
	 * @return {@link dk.digitalidentity.common.dao.model.Person} object or null if no person is logged in or there was an error fetching person by id from database
	 */
	public Person getPerson() {
		Long personId = getPersonId();
		if (personId == null) {
			return null;
		}

		return personService.getById(personId);
	}

	public boolean hasSamAccountName() {
		Person person = getPerson();
		if (person == null) {
			return false;
		}
		
		return StringUtils.hasLength(person.getSamaccountName());
	}

	/**
	 * Returns the domain object of the currently logged in person
	 * @return {@link dk.digitalidentity.common.dao.model.Domain} object or null if no person is logged in or there was an error fetching person by id from database
	 */
	public Domain getDomain() {
		Person person = getPerson();
		if (person == null) {
			return null;
		}

		return person.getDomain();
	}
	
	/**
	 * Returns the topLevel domain object of the currently logged in person
	 * @return {@link dk.digitalidentity.common.dao.model.Domain} object or null if no person is logged in or there was an error fetching person by id from database
	 */
	public Domain getTopLevelDomain() {
		Person person = getPerson();
		if (person == null) {
			return null;
		}

		return person.getTopLevelDomain();
	}

	/**
	 * returns true if the currently logged in person is an admin
	 */
	public boolean hasAnyAdminRole() {
		if (!isAuthenticated()) {
			return false;
		}

		TokenUser tokenUser = (TokenUser) SecurityContextHolder.getContext().getAuthentication().getDetails();
		for (GrantedAuthority role : tokenUser.getAuthorities()) {
			if (role.getAuthority().equals(Constants.ROLE_ADMINISTRATOR) ||
				role.getAuthority().equals(Constants.ROLE_REGISTRANT) ||
				role.getAuthority().equals(Constants.ROLE_SERVICE_PROVIDER_ADMIN) ||
				role.getAuthority().equals(Constants.ROLE_SUPPORTER) ||
				role.getAuthority().equals(Constants.ROLE_USER_ADMIN)) {

				return true;
			}
		}

		return false;
	}

	/**
	 * returns true if the currently logged in person is an admin
	 */
	public boolean isAdmin() {
		if (!isAuthenticated()) {
			return false;
		}

		TokenUser tokenUser = (TokenUser) SecurityContextHolder.getContext().getAuthentication().getDetails();
		for (GrantedAuthority role : tokenUser.getAuthorities()) {
			if (role.getAuthority().equals(Constants.ROLE_ADMINISTRATOR)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Returns true if the currently logged in person has an NSIS user
	 */
	public boolean hasNsisUser() {
		Person person = getPerson();
		return person != null && person.hasNSISUser();
	}

	/**
	 * Update the tokenUser object on the session with the supplied Person
	 */
	public void updateTokenUser(Person person) {
		TokenUser tokenUser = getTokenUser();
		if (tokenUser != null) {
			updateTokenUser(person, tokenUser);
		}
	}

	// called during login, to grant access-roles to admin-portal
	public void updateTokenUser(Person person, TokenUser tokenUser) {
		Boolean hasActivatedNsisAccount = person.hasNSISUser();             // the has an active NSIS account
		Boolean isLocked = person.isLocked();                               // the is not locked
		Boolean lockedByPersonOnly = person.isLockedDataset() == false &&   // not locked by municipality
									 person.isLockedAdmin() == false &&     // not locked by admin
									 person.isLockedPassword() == false &&  // not time-locked due to too many wrong password attempts
									 person.isLockedDead() == false &&      // not locked because they are registered as dead in CPR
									 person.isLockedPerson() == true;       // but IS locked by a self-service action
		NSISLevel nsisLevel = getAuthenticationAssuranceLevel(tokenUser);   // currently logged-in NSIS authentication level

		tokenUser.getAttributes().put(LOCKED_BY_PERSON_ONLY, lockedByPersonOnly);
		tokenUser.getAttributes().put(IS_WITHOUT_USER, (hasActivatedNsisAccount == false));
		tokenUser.getAttributes().put(IS_UNLOCKED, (isLocked == false));

		List<SamlGrantedAuthority> authorities = new ArrayList<>();
		
		// if the user it not locked, and the user has an activated NSIS account, and login occurred at Substantial or better, allow admin access
		if (hasActivatedNsisAccount && isLocked == false && NSISLevel.SUBSTANTIAL.equalOrLesser(nsisLevel)) {
			if (person.isAdmin()) {
				authorities.add(new SamlGrantedAuthority(Constants.ROLE_ADMINISTRATOR));
				authorities.add(new SamlGrantedAuthority(Constants.ROLE_SUPPORTER));
				authorities.add(new SamlGrantedAuthority(Constants.ROLE_SERVICE_PROVIDER_ADMIN));
				authorities.add(new SamlGrantedAuthority(Constants.ROLE_USER_ADMIN));
	
				if (configuration.getCoreData().isEnabled()) {
					authorities.add(new SamlGrantedAuthority(Constants.ROLE_COREDATA_EDITOR));
				}
			}
			else if (person.isSupporter()) {
				authorities.add(new SamlGrantedAuthority(Constants.ROLE_SUPPORTER));
			}

			if (person.isRegistrant()) {
				authorities.add(new SamlGrantedAuthority(Constants.ROLE_REGISTRANT));
			}

			if (person.isServiceProviderAdmin()) {
				authorities.add(new SamlGrantedAuthority(Constants.ROLE_SERVICE_PROVIDER_ADMIN));
			}
			
			if (person.isUserAdmin()) {
				authorities.add(new SamlGrantedAuthority(Constants.ROLE_USER_ADMIN));
			}

			List<Group> passwordGroups = passwordSettingService.getSettingsByChangePasswordOnUsersEnabled().stream().map(s -> s.getChangePasswordOnUsersGroup()).collect(Collectors.toList());
			if (GroupService.memberOfGroup(person, passwordGroups)) {
				authorities.add(new SamlGrantedAuthority(Constants.ROLE_CHANGE_PASSWORD_ON_OTHERS));
			}
		}

		tokenUser.setAuthorities(authorities);
		
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

	/**
	 * returns true if the currently logged in person has a user account,
	 * and that user account is locked by the person, and it is not currently
	 * locked by admin or password-misuse
	 * 
	 * (so a person without a user will return false here...)
	 */
	public boolean isLockedByPersonOnly() {
		if (!isAuthenticated()) {
			return false;
		}
		
		TokenUser tokenUser = (TokenUser) SecurityContextHolder.getContext().getAuthentication().getDetails();
		
		Object value = tokenUser.getAttributes().get(LOCKED_BY_PERSON_ONLY);
		if (value != null && value instanceof Boolean) {
			return (Boolean) value;
		}
		
		return false;
	}
	
	/**
	 * returns true if the currently logged in person has a user account,
	 * and that user account is not locked by any of the lock-flags
	 * 
	 * note that a person without a user is locked (by convention)
	 */
	public boolean isUnlocked() {
		if (!isAuthenticated()) {
			return false;
		}

		TokenUser tokenUser = (TokenUser) SecurityContextHolder.getContext().getAuthentication().getDetails();

		Object value = tokenUser.getAttributes().get(IS_UNLOCKED);
		if (value != null && value instanceof Boolean) {
			return (Boolean) value;
		}

		return false;
	}

	public String getNemIdPid() {
		TokenUser tokenUser = (TokenUser) SecurityContextHolder.getContext().getAuthentication().getDetails();

		Map<String, Object> attributes = tokenUser.getAttributes();
		if (attributes != null) {
			String nemIDPid = (String) attributes.get("NemIDPid");
			if (StringUtils.hasLength(nemIDPid)) {
				return nemIDPid;
			}
		}
		return null;
	}

	public NSISLevel getAuthenticationAssuranceLevel() {
		return getAuthenticationAssuranceLevel(null);
	}
	
	public NSISLevel getAuthenticationAssuranceLevel(TokenUser tokenUser) {
		if (tokenUser == null) {
			tokenUser = (TokenUser) SecurityContextHolder.getContext().getAuthentication().getDetails();
		}

		Map<String, Object> attributes = tokenUser.getAttributes();
		if (attributes != null) {
			String aalString = (String) attributes.get(AUTHENTICATION_ASSURANCE_LEVEL);

			if (StringUtils.hasLength(aalString)) {
				return NSISLevel.valueOf(aalString);
			}
		}

		return NSISLevel.NONE;
	}
}
