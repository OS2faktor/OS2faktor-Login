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
import dk.digitalidentity.common.service.DomainService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.TemporaryClientSessionKeyService;
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.service.LoginService;
import dk.digitalidentity.service.SessionHelper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class ClientApiController {

    @Autowired
    private LoginService loginService;

    @Autowired
    private PersonService personService;

    @Autowired
    private DomainService domainService;

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
    public ResponseEntity<String> clientLogin(@RequestParam("domain") String domainName, @RequestParam("username") String username, @RequestParam("password") String password) {
        Domain domain = domainService.getByName(domainName);
        if (domain == null) {
            log.info("No domain matched incoming client request (Domain: " + domainName + ")");
            return ResponseEntity.badRequest().build();
        }

        List<Person> people = personService.getBySamaccountNameAndDomain(username, domain);
        if (people.size() != 1) {
            log.info(people.size() + " persons found that matched client request, has to be 1 (username: " + username + ")");
            return ResponseEntity.badRequest().build();
        }

        Person person = people.get(0);

        boolean validPassword = loginService.validPassword(password, person);
        if (validPassword) {
            sessionHelper.setPerson(person);
            NSISLevel loginState = sessionHelper.getLoginState();
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
        }
        else {
            return new ResponseEntity<>(HttpStatus.NOT_ACCEPTABLE);
        }
    }

    @PostMapping("/api/client/changePassword")
    @ResponseBody
    public ResponseEntity<String> clientChangePassword(@RequestParam("domain") String domainName, @RequestParam("username") String username, @RequestParam("oldPassword") String oldPassword, @RequestParam("newPassword") String newPassword) {
        Domain domain = domainService.getByName(domainName);
        if (domain == null) {
            log.info("No domain matched incoming client request (Domain: " + domainName + ")");
            return ResponseEntity.badRequest().build();
        }

        List<Person> people = personService.getBySamaccountNameAndDomain(username, domain);
        if (people.size() != 1) {
            log.info(people.size() + " persons found that matched client request, has to be 1 (username: " + username + ")");
            return ResponseEntity.badRequest().build();
        }

        Person person = people.get(0);

        boolean validPassword = loginService.validPassword(oldPassword, person);
        if (validPassword) {
            try {
                personService.changePassword(person, newPassword, true); // No reason to replicate to AD, we just came from there
            } catch (NoSuchPaddingException | InvalidKeyException | NoSuchAlgorithmException | IllegalBlockSizeException | BadPaddingException | UnsupportedEncodingException | InvalidAlgorithmParameterException ex) {
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
