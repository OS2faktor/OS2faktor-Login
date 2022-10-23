package dk.digitalidentity.rest;

import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.TemporaryClientSessionKey;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.TemporaryClientSessionKeyService;
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.filter.ClientApiSecurityFilter;
import dk.digitalidentity.service.LoginService;
import dk.digitalidentity.service.SessionHelper;
import dk.digitalidentity.service.model.enums.PasswordValidationResult;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class ClientApiController {

    @Autowired
    private LoginService loginService;

    @Autowired
    private PersonService personService;

    @Autowired
    private SessionHelper sessionHelper;

    @Autowired
    private TemporaryClientSessionKeyService tempSessionKeyService;

    @Autowired
    private OS2faktorConfiguration configuration;

    @Autowired
    private AuditLogger auditLogger;

    @PostMapping("/api/client/login")
    @ResponseBody
    public ResponseEntity<String> clientLogin(@RequestParam("username") String username, @RequestParam("password") String password) {
    	Domain domain = ClientApiSecurityFilter.domainHolder.get();
        if (domain == null) {
            log.info("No domain matched incoming client request");
            return ResponseEntity.badRequest().build();
        }

        List<Person> people = personService.getBySamaccountNameAndDomain(username, domain);
        if (people.size() != 1) {
            log.info(people.size() + " persons found that matched client request, has to be 1 (username: " + username + ")");
            return ResponseEntity.badRequest().build();
        }

        Person person = people.get(0);

        // we do not want to validate against AD when checking clients
        // this is because the Windows client will always validate against AD locally
        // so in case of a wrong password we would query the ad with a wrong password twice
        // locking out the users at double speed
        switch (loginService.validatePassword(password, person, false)) {
        	case VALID:
				personService.correctPasswordAttempt(person, false, false);
				
	            sessionHelper.setPerson(person);
	            NSISLevel loginState = sessionHelper.getPasswordLevel();
	            if (loginState == null) {
	                log.info(people.size() + " loginState was null after password validation");
	                return ResponseEntity.badRequest().build();
	            }
	
	            // Save the token in DB for later verification
	            TemporaryClientSessionKey temporaryClientSessionKey = new TemporaryClientSessionKey(person, loginState);
	            TemporaryClientSessionKey saved = tempSessionKeyService.save(temporaryClientSessionKey);
	            auditLogger.sessionKeyIssued(saved);
	
	            // Create url for establishing session and send it to the requester
	            String url = configuration.getBaseUrl();
	            if (!url.endsWith("/")) {
	                url += "/";
	            }
	            url += "sso/saml/client/login?sessionKey=" + temporaryClientSessionKey.getSessionKey();
	
	            return ResponseEntity.ok(url);
        	case INVALID:
				break;
			case VALID_EXPIRED:
				personService.correctPasswordAttempt(person, sessionHelper.isAuthenticatedWithADPassword(), true);
				break;

        }

        return new ResponseEntity<>(HttpStatus.NOT_ACCEPTABLE);
    }

    @PostMapping("/api/client/changePassword")
    @ResponseBody
    public ResponseEntity<String> clientChangePassword(@RequestParam("username") String username, @RequestParam("oldPassword") String oldPassword, @RequestParam("newPassword") String newPassword) {
    	Domain domain = ClientApiSecurityFilter.domainHolder.get();
        if (domain == null) {
            log.info("No domain matched incoming client request");
            return ResponseEntity.badRequest().build();
        }

        List<Person> people = personService.getBySamaccountNameAndDomain(username, domain);
        if (people.size() != 1) {
            log.info(people.size() + " persons found that matched client request, has to be 1 (username: " + username + ")");
            return ResponseEntity.badRequest().build();
        }

        Person person = people.get(0);

        if (!PasswordValidationResult.INVALID.equals(loginService.validatePassword(oldPassword, person))) {
            try {
                personService.changePassword(person, newPassword, true); // No reason to replicate to AD, we just came from there
            }
            catch (NoSuchPaddingException | InvalidKeyException | NoSuchAlgorithmException | IllegalBlockSizeException | BadPaddingException | UnsupportedEncodingException | InvalidAlgorithmParameterException ex) {
                // This should never happen
                log.error("Kunne ikke skifte kodeord (Windows Client)", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            return ResponseEntity.ok().build();
        }
        else {
            return new ResponseEntity<>(HttpStatus.NOT_ACCEPTABLE);
        }
    }
}
