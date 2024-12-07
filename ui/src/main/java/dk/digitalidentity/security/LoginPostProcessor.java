package dk.digitalidentity.security;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.config.Constants;
import dk.digitalidentity.samlmodule.model.SamlGrantedAuthority;
import dk.digitalidentity.samlmodule.model.SamlLoginPostProcessor;
import dk.digitalidentity.samlmodule.model.TokenUser;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Transactional
public class LoginPostProcessor implements SamlLoginPostProcessor {

	@Autowired
	private PersonService personService;

	@Autowired
	private SecurityUtil securityUtil;

	@Autowired
	private AuditLogger auditLogger;

	@Override
	public void process(TokenUser tokenUser) {
		Person person = null;

		// We only get CPR claim when brokering to MitID. Normal SelfServiceServiceProvider does not issue CPR claim.
		if (tokenUser.getAttributes().containsKey(Constants.CPR_ATTRIBUTE_KEY)) {
			List<SamlGrantedAuthority> authorities = new ArrayList<>();
			authorities.add(new SamlGrantedAuthority(Constants.ROLE_PARENT));
			
			tokenUser.setAuthorities(authorities);
			
			securityUtil.updateCache(tokenUser, authorities);
			auditLogger.loginStudentPasswordChange((String) tokenUser.getAttributes().get(Constants.CPR_ATTRIBUTE_KEY));
		}
		else {
			String username = tokenUser.getUsername();
			Long personId = username != null ? Long.parseLong(username) : null;
					
			if (personId != null) {
				person = personService.getById(personId);
			}
			
			if (person == null) {
				log.warn("Could not find person with ID: " + personId);
				throw new UsernameNotFoundException("Der findes ikke nogen erhvervsmæssig tilknytning til den valgte identitet!");
			}
	
			securityUtil.updateTokenUser(person, tokenUser);
			
			String token = tokenUser.getAndClearRawToken();
			
			auditLogger.loginSelfService(person, token);
		}
	}
}
