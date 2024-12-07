package dk.digitalidentity.rest;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.TemporaryClientSessionKey;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.dao.model.mapping.TemporaryClientSessionMapping;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.TemporaryClientSessionKeyService;
import dk.digitalidentity.common.service.TemporaryClientSessionMappingService;
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.filter.ClientApiSecurityFilter;
import dk.digitalidentity.rest.model.UsernameAndPassword;
import dk.digitalidentity.rest.model.UsernameAndPasswordChange;
import dk.digitalidentity.service.OtherSessionHelper;
import dk.digitalidentity.service.PasswordService;
import dk.digitalidentity.service.SessionHelper;
import dk.digitalidentity.service.model.enums.PasswordValidationResult;
import dk.digitalidentity.util.Constants;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class ClientApiController {

    @Autowired
    private PasswordService passwordService;

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

	@Autowired
	private TemporaryClientSessionMappingService temporaryClientSessionMappingService;

	@Autowired
	private OtherSessionHelper otherSessionHelper;

    @PostMapping("/api/client/loginWithBody")
    @ResponseBody
    public ResponseEntity<String> clientLoginWithPost(@RequestBody UsernameAndPassword usernameAndPassword, HttpServletRequest request) {
		return clientLoginPerform(usernameAndPassword.getUsername(), usernameAndPassword.getPassword(), usernameAndPassword.getPreviousToken(), tempSessionKeyService.getIpAddressFromRequest(request), usernameAndPassword.getVersion(), usernameAndPassword.isBase64());
    }

    @PostMapping("/api/client/login")
    @ResponseBody
    public ResponseEntity<String> clientLoginGet(HttpServletRequest request) {
		return clientLoginPerform(getParameter("username", request), getParameter("password", request), getParameter("previousToken", request), tempSessionKeyService.getIpAddressFromRequest(request), getParameter("version", request), false);
    }

    @PostMapping("/api/client/changePasswordWithBody")
    @ResponseBody
    public ResponseEntity<String> clientChangePasswordPost(@RequestBody UsernameAndPasswordChange usernameAndPasswordChange) {
		return clientChangePasswordPerform(usernameAndPasswordChange.getUsername(), usernameAndPasswordChange.getOldPassword(), usernameAndPasswordChange.getNewPassword(), usernameAndPasswordChange.getVersion());
    }

    @PostMapping("/api/client/changePassword")
    @ResponseBody
    public ResponseEntity<String> clientChangePasswordGet(HttpServletRequest request) {
		return clientChangePasswordPerform(getParameter("username", request), getParameter("oldPassword", request), getParameter("newPassword", request), getParameter("version", request));
    }

    private ResponseEntity<String> clientLoginPerform(String username, String password, String previousToken, String ipAddress, String version, boolean base64) {
    	Domain domain = ClientApiSecurityFilter.domainHolder.get();
        if (domain == null) {
            log.info("No domain matched incoming client request");
            return ResponseEntity.badRequest().build();
        }

		// Strip domain from username if provided
		if (StringUtils.hasLength(username) && username.contains("@")) {
			String[] split = username.split("@");
			username = split[0];
		}

		if (!StringUtils.hasLength(username) || !StringUtils.hasLength(password)) {
			log.warn("Missing username/password parameters");
			return ResponseEntity.badRequest().build();
		}
		
		if (domain.getParent() != null) {
			domain = domain.getParent();
		}
		
		List<Domain> domains = new ArrayList<>();
		domains.add(domain);
		if (domain.getChildDomains() != null) {
			for (Domain d : domain.getChildDomains()) {
				domains.add(d);
			}
		}
		
		// attempt to base64 decode password
		if (base64) {
			try {
				byte[] bPassword = Base64.getDecoder().decode(password);
				password = new String(bPassword);
			}
			catch (Exception ex) {
				// continue with non-decoded password
				log.warn("Failed to base64 decode password", ex);
			}
		}

        List<Person> people = personService.getBySamaccountNameAndDomains(username, domains);
        if (people.size() != 1) {
            log.info(people.size() + " persons found that matched client request, has to be 1 (username: " + username + ")");
            return ResponseEntity.badRequest().build();
        }

        Person person = people.get(0);

		if (person.isLocked()) {
			log.warn("Person (" + person.getId() + ") was locked when trying to fetch token for SSO from Windows, so no session will be established");
			return ResponseEntity.badRequest().build();
		}

		boolean hasPassword = StringUtils.hasLength(person.getPassword());
		if (!hasPassword) {
			return ResponseEntity.badRequest().build();
		}

		// perform local password validation - no fallback to AD as these calls originate from AD, and we do not want
		// to trigger a double wrong password result
		PasswordValidationResult passwordValidationResult = passwordService.validatePasswordFromWcp(password, person);
        switch (passwordValidationResult) {
        	case VALID:
        	case VALID_BUT_BAD_PASWORD: // will allow this as the grace period is still in-play
				personService.correctPasswordAttempt(person, sessionHelper.isAuthenticatedWithADPassword(), false);

				// Validation
	            sessionHelper.setPerson(person);
	            NSISLevel loginState = sessionHelper.getPasswordLevel();
	            if (loginState == null) {
	                log.info(people.size() + " loginState was null after password validation");
	                return ResponseEntity.badRequest().build();
	            }

				// Fetch TemporaryClientSessionKey and refresh old sessions, if any.
				TemporaryClientSessionKey temporaryClientSessionKey = getTemporaryClientSessionKey(previousToken, person, loginState, ipAddress);

				log.debug("Issued token " + temporaryClientSessionKey.getSessionKey() + " to (Person: " + person.getId() + ")");
				
	            // Create url for establishing session and send it to the requester
	            String url = configuration.getBaseUrl();
	            if (!url.endsWith("/")) {
	                url += "/";
	            }
	            url += "sso/saml/client/login?sessionKey=" + temporaryClientSessionKey.getSessionKey();
				if (StringUtils.hasLength(version)) {
					url += "&version=" + version; // Send version number back
				}
				url += "&os2faktorAutoClose=true";

	            return ResponseEntity.ok(url);
			case INVALID:
			case INVALID_BAD_PASSWORD:
			case LOCKED:
			case TECHNICAL_ERROR:
			case INSUFFICIENT_PERMISSION:
				break;
			case VALID_EXPIRED:
				personService.correctPasswordAttempt(person, false, true);
				break;
		}

        return new ResponseEntity<>(HttpStatus.NOT_ACCEPTABLE);
    }

	public Map<String, String> splitQuery(URL url) throws UnsupportedEncodingException {
		Map<String, String> query_pairs = new LinkedHashMap<String, String>();
		String query = url.getQuery();
		String[] pairs = query.split("&");
		for (String pair : pairs) {
			int idx = pair.indexOf("=");
			query_pairs.put(URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8), URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8));
		}
		return query_pairs;
	}

	private TemporaryClientSessionKey getTemporaryClientSessionKey(String previousToken, Person person, NSISLevel loginState, String ipAddress) {
		if (StringUtils.hasLength(previousToken)) {
			log.debug("previous token was provided, trying to refresh existing sessions (Person: " + person.getId() + ")");

			try {
				URI uri = URI.create(previousToken);
				Map<String, String> params = splitQuery(uri.toURL());
				previousToken = params.get("sessionKey");
			}
			catch (Exception ex) {
				log.debug("Could not parse sessionKey from token", ex);
				return issueNewTemporaryClientSessionKey(person, loginState, ipAddress);
			}

			if (StringUtils.hasLength(previousToken)) {
				TemporaryClientSessionKey oldSessionKey = tempSessionKeyService.getBySessionKey(previousToken);
				if (oldSessionKey == null) {
					log.debug("Could not find old TemporaryClientSessionKey by the provided previous token (Token: " + previousToken + "), (Person: " + person.getId() + ")");
					return issueNewTemporaryClientSessionKey(person, loginState, ipAddress);
				}

				boolean successfullyRefreshedSessions = refreshPasswordSessionOnOtherAssociatedSessions(oldSessionKey, person);
				log.debug("Previous session(s) successfully refreshed = " + successfullyRefreshedSessions + " (Person: " + person.getId() + ")");

				return successfullyRefreshedSessions ? oldSessionKey : issueNewTemporaryClientSessionKey(person, loginState, ipAddress);
			}
			else {
				log.debug("no sessionKey was provided");
				return issueNewTemporaryClientSessionKey(person, loginState, ipAddress);
			}
		}
		else {
			log.debug("no previous token was provided");
			return issueNewTemporaryClientSessionKey(person, loginState, ipAddress);
		}
	}

	private TemporaryClientSessionKey issueNewTemporaryClientSessionKey(Person person, NSISLevel loginState, String ipAddress) {
		TemporaryClientSessionKey temporaryClientSessionKey = new TemporaryClientSessionKey(person, loginState, ipAddress);
		TemporaryClientSessionKey saved = tempSessionKeyService.save(temporaryClientSessionKey);
		auditLogger.sessionKeyIssued(saved);
		return saved;
	}

	private boolean refreshPasswordSessionOnOtherAssociatedSessions(TemporaryClientSessionKey previousToken, Person person) {
		List<TemporaryClientSessionMapping> previousTokenExchanges = temporaryClientSessionMappingService.getByTemporaryClientSessionKey(previousToken);
		if (log.isDebugEnabled()) {
			log.debug("Found " + previousTokenExchanges.size() + " sessions with a matching token");
		}

		int count = 0;
		for (TemporaryClientSessionMapping previousTokenExchange : previousTokenExchanges) {
			String sessionId = previousTokenExchange.getSessionId();
			Person otherSessionPerson = otherSessionHelper.getPerson(sessionId);
			if (otherSessionPerson == null) {
				log.warn("No person found on other session with matching token (Person: " + person.getId() + ")");
				continue; // No person found on session
			}

			if (!Objects.equals(person.getId(), otherSessionPerson.getId())) {
				log.warn("Another person found on other session with matching token (Requesting person: " + person.getId() + ", Found person: " + otherSessionPerson.getId() + ")" );
				continue; // Not the same person
			}

			// Same person on both sessions, continue
			log.debug("Refreshing passwordsession timestamp (Person: " + person.getId() + ") for token " + previousToken.getSessionKey());

			LocalDateTime passwordLevelTimestamp = otherSessionHelper.getLocalDateTime(sessionId, Constants.PASSWORD_AUTHENTIFICATION_LEVEL_TIMESTAMP);

			Long passwordExpiry = sessionHelper.getSessionLifetimePassword(person);

			boolean passwordSessionStillValid = passwordLevelTimestamp != null && !LocalDateTime.now().minusMinutes(passwordExpiry).isAfter(passwordLevelTimestamp);
			if (passwordSessionStillValid) {
				otherSessionHelper.set(sessionId, Constants.PASSWORD_AUTHENTIFICATION_LEVEL_TIMESTAMP, LocalDateTime.now());
				count++;
			}
			else {
				log.debug("Tried to extend session but other sessions passwordTimestamp was expired (Person: " + person.getId() + ")");
			}
		}
		
		if (count > 0) {
			log.debug("Refreshed passwordLevelTimestamp on " + count + " sessions to successful WCP login (Person: " + person.getId() + ")");
			return true;
		}

		return false;
	}

	private ResponseEntity<String> clientChangePasswordPerform(String username, String oldPassword, String newPassword, String version) {
    	Domain domain = ClientApiSecurityFilter.domainHolder.get();
        if (domain == null) {
            log.info("No domain matched incoming client request");
            return ResponseEntity.badRequest().build();
        }

		if (!StringUtils.hasLength(username) || !StringUtils.hasLength(newPassword) || !StringUtils.hasLength(oldPassword)) {
			log.warn("Missing username, new/old password parameters");
			return ResponseEntity.badRequest().build();
		}

        List<Person> people = personService.getBySamaccountNameAndDomain(username, domain);
        if (people.size() != 1) {
            log.info(people.size() + " persons found that matched client request, has to be 1 (username: " + username + ")");
            return ResponseEntity.badRequest().build();
        }

        Person person = people.get(0);

		// In the case of a NSIS Allowed person we ONLY check the NSIS password,
		// otherwise a correct Windows password (that is not NSIS approved) would be able to change the NSIS password we have
		try {
			if (person.isNsisAllowed()) {
				if (passwordService.validatePasswordNoAD(oldPassword, person).isNoErrors()) {
					personService.changePassword(person, newPassword, true, null, null, false); // No reason to replicate to AD, we just came from there
					return ResponseEntity.ok().build();
				}
			}
			else {
				if (passwordService.validatePassword(oldPassword, person).isNoErrors() || passwordService.validatePassword(newPassword, person).isNoErrors()) {
					// Since the person is not nsis allowed AND we bypass replication, the password is never actually set anywhere
					// The Following actions still happen though:
					// * A PasswordChange queue row is added with DO_NOT_REPLICATE
					// * DailyPasswordChangeCounter increments
					// * The password change is audit logged
					personService.changePassword(person, newPassword, true, null, null, false);
					return ResponseEntity.ok().build();
				}
			}
        	return new ResponseEntity<>(HttpStatus.NOT_ACCEPTABLE);
		}
		catch (NoSuchPaddingException | InvalidKeyException | NoSuchAlgorithmException | IllegalBlockSizeException | BadPaddingException | UnsupportedEncodingException | InvalidAlgorithmParameterException ex) {
			// This should never happen
			log.error("Kunne ikke skifte kodeord (Windows Client)", ex);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
    }
    
    // temporary method to use with GET operations from WCP (replaced in WCP since 1/11/2022)
	private String getParameter(String parameter, HttpServletRequest request) {
		String value = request.getParameter(parameter);
		if (value != null) {
			value = value.replace(' ', '+');
		}

		return value;
	}
}
