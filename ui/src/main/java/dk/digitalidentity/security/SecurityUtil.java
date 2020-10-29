package dk.digitalidentity.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.config.Constants;
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.saml.model.TokenUser;

@Component
public class SecurityUtil {
	private static final String LOCKED_BY_PERSON_ONLY = "LOCKED_BY_PERSON_ONLY";
	private static final String IS_WITHOUT_USER = "IS_WITHOUT_USER";
	private static final String IS_UNLOCKED = "IS_UNLOCKED";

	@Autowired
	private HttpServletRequest request;

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
	 * Update the tokenUser object on the session with the supplied Person
	 */
	public void updateTokenUser(Person person) {
		TokenUser tokenUser = getTokenUser();
		if (tokenUser != null) {
			updateTokenUser(person, tokenUser);
		}
	}

	/**
	 * Update the tokenUser object on the session with the supplied Person
	 */
	public void updateTokenUser(Person person, TokenUser tokenUser) {
		Boolean lockedByPersonOnly = false;
		Boolean isWithoutUser = false;
		Boolean isUnlocked = false;

		if (person.hasNSISUser()) {
			lockedByPersonOnly = person.isLockedDataset() == false &&
								 person.isLockedAdmin() == false &&
							 	 person.isLockedPassword() == false &&
							 	 person.isLockedPerson() == true;

			isUnlocked = person.isLocked() == false;
		}
		else {
			isWithoutUser = true;
		}
		
		tokenUser.getAttributes().put(LOCKED_BY_PERSON_ONLY, lockedByPersonOnly);
		tokenUser.getAttributes().put(IS_WITHOUT_USER, isWithoutUser);
		tokenUser.getAttributes().put(IS_UNLOCKED, isUnlocked);

		List<GrantedAuthority> authorities = new ArrayList<>();
		if (person.isAdmin()) {
			authorities.add(new SimpleGrantedAuthority(Constants.ROLE_ADMINISTRATOR));
			authorities.add(new SimpleGrantedAuthority(Constants.ROLE_SUPPORTER));

			if (configuration.getCoreData().isEnabled()) {
				authorities.add(new SimpleGrantedAuthority(Constants.ROLE_COREDATA_EDITOR));
			}
		}
		else if (person.isSupporter()) {
			authorities.add(new SimpleGrantedAuthority(Constants.ROLE_SUPPORTER));
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
			if (!StringUtils.isEmpty(nemIDPid)) {
				return nemIDPid;
			}
		}
		return null;
	}
}
