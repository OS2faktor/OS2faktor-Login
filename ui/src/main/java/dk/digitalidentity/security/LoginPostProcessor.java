package dk.digitalidentity.security;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.samlmodule.model.SamlLoginPostProcessor;
import dk.digitalidentity.samlmodule.model.TokenUser;
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
