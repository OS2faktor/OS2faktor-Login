package dk.digitalidentity.common.log;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dk.digitalidentity.common.config.Constants;
import dk.digitalidentity.common.dao.AuditLogDao;
import dk.digitalidentity.common.dao.model.AuditLog;
import dk.digitalidentity.common.dao.model.AuditLogDetail;
import dk.digitalidentity.common.dao.model.PasswordSetting;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.PrivacyPolicy;
import dk.digitalidentity.common.dao.model.SessionSetting;
import dk.digitalidentity.common.dao.model.TemporaryClientSessionKey;
import dk.digitalidentity.common.dao.model.TermsAndConditions;
import dk.digitalidentity.common.dao.model.enums.DetailType;
import dk.digitalidentity.common.dao.model.enums.LogAction;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.dao.model.enums.RequirementCheckResult;
import dk.digitalidentity.common.service.mfa.model.MfaClient;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class AuditLogger {

	@Autowired
	private AuditLogDao auditLogDao;

	public void errorSentToSP(Person person, ErrorLogDto errorDetail) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.ERROR_SENT_TO_SP);
		auditLog.setMessage(errorDetail.getMessage());

		AuditLogDetail detail = new AuditLogDetail();
		auditLog.setDetails(detail);

		detail.setDetailType(DetailType.JSON);
		try {
			auditLog.getDetails().setDetailContent(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(errorDetail));
		}
		catch (JsonProcessingException e) {
			log.error("Could not serialize ErrorDetail");
		}

		log(auditLog, person, null);
	}

	public void toggleRoleByAdmin(Person person, Person admin, String role, boolean enabled) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(enabled ? LogAction.ADDED_ROLE_BY_ADMIN : LogAction.REMOVED_ROLE_BY_ADMIN);
		String message = enabled ? "Tildelt " : "Frataget ";

		if (Constants.ROLE_ADMIN.equals(role)) {
			auditLog.setMessage(message + "administrator rollen af en administrator");
		}
		else if (Constants.ROLE_SUPPORTER.equals(role)) {
			auditLog.setMessage(message + "supporter rollen af en administrator");

			// Log supporter domain
			if (enabled && person.getSupporter() != null) {
				AuditLogDetail detail = new AuditLogDetail();
				detail.setDetailType(DetailType.TEXT);
				detail.setDetailContent("domain: " + person.getSupporter().getDomain().getName());
				auditLog.setDetails(detail);
			}
		}
		else if (Constants.ROLE_REGISTRANT.equals(role)) {
			auditLog.setMessage(message + "registrant rollen af en administrator");
		}
		else if (Constants.ROLE_SERVICE_PROVIDER_ADMIN.equals(role)) {
			auditLog.setMessage(message + "tjenesteudbyder-admin rollen af en administrator");
		}
		else if (Constants.ROLE_USER_ADMIN.equals(role)) {
			auditLog.setMessage(message + "brugeradministrator rollen af en administrator");
		}
		else {
			auditLog.setMessage(message + role);
			log.error("Unknown role: " + role);
		}

		log(auditLog, person, admin);
	}


	public void sessionKeyIssued(TemporaryClientSessionKey saved) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.SESSION_KEY_ISSUED);
		auditLog.setMessage("Session etableret via Windows login");

		// Add details of which passwords has been changed
		AuditLogDetail detail = new AuditLogDetail();
		detail.setDetailType(DetailType.TEXT);

		String detailMsg = "NSIS Level: " + saved.getNsisLevel();
		detail.setDetailContent(detailMsg);
		auditLog.setDetails(detail);

		log(auditLog, saved.getPerson(), null);
	}

	public void removedAllFromDataset(List<Person> people) {
		ArrayList<AuditLog> logs = new ArrayList<>();
		for (Person person : people) {
			AuditLog auditLog = new AuditLog();
			auditLog.setLogAction(LogAction.REMOVED_FROM_DATASET);
			auditLog.setMessage("Bruger er blevet spærret af kommunen");

			auditLog.setCorrelationId(getCorrelationId());
			auditLog.setIpAddress(getIpAddress());
			auditLog.setPerson(person);
			auditLog.setPersonName(person.getName());
			auditLog.setPersonDomain(person.getDomain().getName());
			auditLog.setCpr(person.getCpr());
			
			logs.add(auditLog);
		}

		auditLogDao.saveAll(logs);
	}
	
	public void addedToDataset(Person person) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.ADDED_TO_DATASET);
		auditLog.setMessage("Stamdata for bruger indlæst");

		log(auditLog, person, null);
	}

	public void addedAllToDataset(List<Person> people) {
		ArrayList<AuditLog> logs = new ArrayList<>();
		for (Person person : people) {
			AuditLog auditLog = new AuditLog();
			auditLog.setLogAction(LogAction.ADDED_TO_DATASET);
			auditLog.setMessage("Stamdata for bruger indlæst");

			auditLog.setCorrelationId(getCorrelationId());
			auditLog.setIpAddress(getIpAddress());
			auditLog.setPerson(person);
			auditLog.setPersonName(person.getName());
			auditLog.setPersonDomain(person.getDomain().getName());
			auditLog.setCpr(person.getCpr());

			logs.add(auditLog);
		}

		auditLogDao.saveAll(logs);
	}

	public void loginSelfService(Person person, String assertion) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.LOGIN);
		auditLog.setMessage("Login billet modtaget i selvbetjening");
		auditLog.setDetails(new AuditLogDetail());
		auditLog.getDetails().setDetailType(DetailType.XML);
		auditLog.getDetails().setDetailContent(assertion);

		log(auditLog, person, null);
	}
	
	public void login(Person person, String loginTo, String assertion) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.LOGIN);
		auditLog.setMessage("Login til " + loginTo);
		auditLog.setDetails(new AuditLogDetail());
		auditLog.getDetails().setDetailType(DetailType.XML);
		auditLog.getDetails().setDetailContent(assertion);

		log(auditLog, person, null);
	}

	public void logout(Person person) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.LOGOUT);
		auditLog.setMessage("Logud gennemført");

		log(auditLog, person, null);
	}

	public void logoutCausedByIPChange(Person person) {
		if (person != null) {
			AuditLog auditLog = new AuditLog();
			auditLog.setLogAction(LogAction.LOGOUT_IP_CHANGED);
			auditLog.setMessage("Brugeren logget ud grundet ændret ip adresse");
	
			log(auditLog, person, null);
		}
	}

	public void badPassword(Person person) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.WRONG_PASSWORD);
		auditLog.setMessage("Forkert kodeord indtastet");

		log(auditLog, person, null);
	}
	
	public void tooManyBadPasswordAttempts(Person person) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.TOO_MANY_ATTEMPTS);
		auditLog.setMessage("Kontoen er spærret de næste 5 minutter grundet for mange forkerte kodeord i træk");
		
		log(auditLog, person, null);
	}

	public void goodPassword(Person person, boolean authenticatedWithADPassword) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.RIGHT_PASSWORD);
		auditLog.setMessage("Kodeord anvendt");
		
		// Add details of what the password was validated against
		AuditLogDetail detail = new AuditLogDetail();
		detail.setDetailType(DetailType.TEXT);
		detail.setDetailContent("Kodeordet er valideret mod: " + (authenticatedWithADPassword ? "AD" : "bruger-databasen"));
		auditLog.setDetails(detail);

		log(auditLog, person, null);
	}
	
	public void acceptedMFA(Person person, MfaClient client) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.ACCEPT_MFA);
		auditLog.setMessage("2-faktor login godkendt");
		AuditLogDetail detail = new AuditLogDetail();
		detail.setDetailType(DetailType.JSON);
		detail.setDetailContent("{ \"deviceId\": \"" + client.getDeviceId() + "\", \"type\": \"" + client.getType().toString() + "\" }");
		auditLog.setDetails(detail);

		log(auditLog, person, null);
	}
	
	public void rejectedMFA(Person person) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.REJECT_MFA);
		auditLog.setMessage("2-faktor login afvist");

		log(auditLog, person, null);
	}

	public void loginRejectedByConditions(Person person, RequirementCheckResult result) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.REJECTED_BY_CONDITIONS);
		auditLog.setMessage("Login afvist da brugeren ikke opfylder kravene opsat på tjenesteudbyderen");
		AuditLogDetail detail = new AuditLogDetail();
		detail.setDetailType(DetailType.TEXT);

		switch (result) {
			case FAILED:
				detail.setDetailContent("Generel fejl");
				break;
			case FAILED_DOMAIN:
				detail.setDetailContent("Brugeren fejlede krav til domæne tilhørsforhold");
				break;
			case FAILED_GROUP:
				detail.setDetailContent("Brugeren fejlede krav til gruppe medlemsskab");
				break;
			case OK:
				break;
		}

		log(auditLog, person, null);
	}

	public void changePasswordFailed(Person person, String reason) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.CHANGE_PASSWORD_FAILED);
		auditLog.setMessage("Kodeordsskifte afvist");

		// Add details of which passwords has been changed
		AuditLogDetail detail = new AuditLogDetail();
		detail.setDetailType(DetailType.TEXT);
		detail.setDetailContent(reason);
		auditLog.setDetails(detail);

		log(auditLog, person, null);
	}

	public void changePasswordByPerson(Person person, boolean nsisPasswordChanged, boolean replicateToAD) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.CHANGE_PASSWORD);
		auditLog.setMessage("Kodeord skiftet af personen selv");

		// Add details of which passwords has been changed
		AuditLogDetail detail = new AuditLogDetail();
		detail.setDetailType(DetailType.TEXT);
		detail.setDetailContent("NSIS Kodeord: " + (nsisPasswordChanged ? "Skiftet" : "Uændret") + "\nReplikering til AD: " + (replicateToAD ? "Ja" : "Nej"));
		auditLog.setDetails(detail);

		log(auditLog, person, null);
	}
	
	public void changePasswordByAdmin(Person admin, Person person, boolean replicateToAD) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.CHANGE_PASSWORD);
		auditLog.setMessage("Kodeord skiftet af administrator");

		// Add details of which passwords has been changed
		AuditLogDetail detail = new AuditLogDetail();
		detail.setDetailType(DetailType.TEXT);
		detail.setDetailContent("Replikering til AD: " + (replicateToAD ? "Ja" : "Nej"));
		auditLog.setDetails(detail);

		log(auditLog, person, admin);
	}
	
	public void unlockAccountByPerson(Person person) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.UNLOCK_ACCOUNT);
		auditLog.setMessage("AD konto låst op af personen selv");

		log(auditLog, person, null);
	}

	public void activatedByPerson(Person person, String nemIDPid, String mitIdNameId) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.ACTIVATE);
		auditLog.setMessage("Brugerkontoen er blevet aktiveret af brugeren selv");

		AuditLogDetail detail = new AuditLogDetail();
		detail.setDetailType(DetailType.TEXT);

		StringBuilder sb = new StringBuilder();
		if (StringUtils.hasLength(mitIdNameId)) {
			sb.append("MitID NameID: ").append(mitIdNameId);
		}
		else if (StringUtils.hasLength(nemIDPid)) {
			sb.append("NemID PID: ").append(nemIDPid);
		}

		detail.setDetailContent(sb.toString());

		auditLog.setDetails(detail);

		log(auditLog, person, null);
	}

	public void manualActivation(IdentificationDetails details, Person person, Person performedBy) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.ACTIVATE);
		auditLog.setMessage("Brugerkontoen er blevet aktiveret af administrator");

		auditLog.setDetails(new AuditLogDetail());
		auditLog.getDetails().setDetailType(DetailType.JSON);

		try {
			ObjectMapper mapper = new ObjectMapper();
			String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(details);
			auditLog.getDetails().setDetailContent(json);
		}
		catch (JsonProcessingException ex) {
			log.error("Could not serialize IdentificationDetails", ex);
		}

		log(auditLog, person, performedBy);
	}
	
	public void manualMfaAssociation(IdentificationDetails details, Person person, Person performedBy) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.ASSOCIATE_MFA);
		auditLog.setMessage("2-faktor enhed tilknyttet af administrator");

		auditLog.setDetails(new AuditLogDetail());
		auditLog.getDetails().setDetailType(DetailType.JSON);

		try {
			ObjectMapper mapper = new ObjectMapper();
			String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(details);
			auditLog.getDetails().setDetailContent(json);
		}
		catch (JsonProcessingException ex) {
			log.error("Could not serialize IdentificationDetails", ex);
		}

		log(auditLog, person, performedBy);
	}

	public void manualMfaAssociation(IdentificationDetails details, Person person) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.ASSOCIATE_MFA);
		auditLog.setMessage("2-faktor enhed tilknyttet af bruger selv");

		auditLog.setDetails(new AuditLogDetail());
		auditLog.getDetails().setDetailType(DetailType.JSON);

		try {
			ObjectMapper mapper = new ObjectMapper();
			String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(details);
			auditLog.getDetails().setDetailContent(json);
		}
		catch (JsonProcessingException ex) {
			log.error("Could not serialize IdentificationDetails", ex);
		}

		log(auditLog, person, null);
	}

	public void manualPasswordChange(IdentificationDetails details, Person person, Person performedBy) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.CHANGE_PASSWORD);
		auditLog.setMessage("Kodeord skiftet af administrator");

		auditLog.setDetails(new AuditLogDetail());
		auditLog.getDetails().setDetailType(DetailType.JSON);

		try {
			ObjectMapper mapper = new ObjectMapper();
			String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(details);
			auditLog.getDetails().setDetailContent(json);
		}
		catch (JsonProcessingException ex) {
			log.error("Could not serialize IdentificationDetails", ex);
		}

		log(auditLog, person, performedBy);
	}

	public void manualYubiKeyInitalization(IdentificationDetails details, Person person, Person performedBy) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.MANUAL_YUBIKEY_REGISTRATION);
		auditLog.setMessage("Yubikey tilknyttet af administrator");

		auditLog.setDetails(new AuditLogDetail());
		auditLog.getDetails().setDetailType(DetailType.JSON);

		try {
			ObjectMapper mapper = new ObjectMapper();
			String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(details);
			auditLog.getDetails().setDetailContent(json);
		}
		catch (JsonProcessingException ex) {
			log.error("Could not serialize IdentificationDetails", ex);
		}

		log(auditLog, person, performedBy);
	}

	public void acceptedTermsByPerson(Person person) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.ACCEPTED_TERMS);
		auditLog.setMessage("Brugeren har accepteret vilkår");
		
		log(auditLog, person, null);
	}

	public void deactivateByPerson(Person person) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.DEACTIVATE_BY_PERSON);
		auditLog.setMessage("Brugeren er blevet spærret af brugeren selv");

		log(auditLog, person, null);
	}

	public void reactivateByPerson(Person person) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.REACTIVATE_BY_PERSON);
		auditLog.setMessage("Brugeren er blevet re-aktiveret af brugeren selv");

		log(auditLog, person, null);
	}

	// TODO: bør nok flippe de begreber rundt, så suspend er midlertidig
	public void deactivateByAdmin(Person person, Person admin, boolean suspend) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.DEACTIVATE_BY_ADMIN);
		auditLog.setMessage("Brugeren er blevet spærret af en administrator");
		AuditLogDetail detail = new AuditLogDetail();
		detail.setDetailContent(suspend ? "Permanent spærring" : "Midlertidig spærring");
		detail.setDetailType(DetailType.TEXT);
		auditLog.setDetails(detail);

		log(auditLog, person, admin);
	}

	public void reactivateByAdmin(Person person, Person admin) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.REACTIVATE_BY_ADMIN);
		auditLog.setMessage("Spærringen af brugeren er blevet hævet af en administrator");

		log(auditLog, person, admin);
	}

	public void changePasswordSettings(PasswordSetting passwordSettings, Person admin) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.CHANGE_PASSWORD_SETTINGS);
		auditLog.setMessage("Passwordregler ændret");

		auditLog.setDetails(new AuditLogDetail());
		auditLog.getDetails().setDetailType(DetailType.JSON);

		try {
			auditLog.getDetails().setDetailContent(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(passwordSettings));
		} catch (JsonProcessingException e) {
			log.error("Could not serialize PasswordSettings", e);
		}

		log(auditLog, admin, admin);
	}

	public void changeSessionSettings(SessionSetting sessionSettings, Person admin) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.CHANGE_SESSION_SETTINGS);
		auditLog.setMessage("Sessionindstillinger ændret");

		auditLog.setDetails(new AuditLogDetail());
		auditLog.getDetails().setDetailType(DetailType.JSON);

		try {
			auditLog.getDetails().setDetailContent(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(sessionSettings));
		} catch (JsonProcessingException e) {
			log.error("Could not serialize SessionSettings");
		}

		log(auditLog, admin, admin);
	}

	public void changeTerms(TermsAndConditions termsAndConditions, Person admin) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.CHANGE_TERMS_AND_CONDITIONS);
		auditLog.setMessage("Vilkår opdateret");

		auditLog.setDetails(new AuditLogDetail());
		auditLog.getDetails().setDetailType(DetailType.TEXT);
		auditLog.getDetails().setDetailContent(termsAndConditions.getContent());

		log(auditLog, admin, admin);
	}

	public void changePrivacyPolicy(PrivacyPolicy privacyPolicy, Person admin) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.CHANGE_PRIVACY_POLICY);
		auditLog.setMessage("Privatlivspolitik opdateret");

		auditLog.setDetails(new AuditLogDetail());
		auditLog.getDetails().setDetailType(DetailType.TEXT);
		auditLog.getDetails().setDetailContent(privacyPolicy.getContent());

		log(auditLog, admin, admin);
	}
	
	public void editedUser(Person person, Person admin) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.UPDATED_USER);
		auditLog.setMessage("Stamdata for person ændret");

		log(auditLog, person, admin);
	}
	
	public void createdUser(Person person, Person admin) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.CREATED_USER);
		auditLog.setMessage("Stamdata for person oprettet");

		log(auditLog, person, admin);
	}
	
	public void deletedUser(Person person, Person admin) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.DELETED_USER);
		auditLog.setMessage("Stamdata for person slettet");

		log(auditLog, person, admin);
	}

	public void authnRequest(Person person, String authnRequest, String sentBy) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.AUTHN_REQUEST);
		auditLog.setMessage("Login forespørgsel fra " + sentBy);

		auditLog.setDetails(new AuditLogDetail());
		auditLog.getDetails().setDetailType(DetailType.XML);
		auditLog.getDetails().setDetailContent(authnRequest);

		log(auditLog, person, null);
	}

	public void logoutRequest(Person person, String logoutRequest, boolean outgoing, String sp) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.LOGOUT_REQUEST);
		auditLog.setMessage(outgoing ? "Logud forespørgsel sendt til " + sp : "Logud forespørgsel modtaget fra " + sp);

		auditLog.setDetails(new AuditLogDetail());
		auditLog.getDetails().setDetailType(DetailType.XML);
		auditLog.getDetails().setDetailContent(logoutRequest);

		log(auditLog, person, null);
	}

	public void logoutResponse(Person person, String logoutResponse, boolean outgoing, String sp) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.LOGOUT_RESPONSE);
		auditLog.setMessage(outgoing ? "Svar på logud forespørgsel sendt til " + sp : "Svar på logud forespørgsel modtaget fra " + sp);

		auditLog.setDetails(new AuditLogDetail());
		auditLog.getDetails().setDetailType(DetailType.XML);
		auditLog.getDetails().setDetailContent(logoutResponse);

		log(auditLog, person, null);
	}


	public void nsisAllowedChanged(Person person, boolean nsisAllowed) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.CHANGED_NSIS_ALLOWED);
		auditLog.setMessage(nsisAllowed ? "Bruger tildelt en NSIS ervhervsidentitet" : "Bruger frataget deres NSIS erhvervsidentitet");

		log(auditLog, person, null);
	}
	
	public void radiusLoginRequestReceived(String username, String radiusClient) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.RADIUS_LOGIN_REQUEST_RECEIVED);
		auditLog.setMessage("Login forespørgsel fra RADIUS klient: " + radiusClient);

		log(auditLog, null, null);
	}
	
	public void radiusLoginRequestAccepted(Person person) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.RADIUS_LOGIN_REQUEST_ACCEPTED);
		auditLog.setMessage("Login forespørgsel godkendt");

		log(auditLog, person, null);
	}
	
	public void radiusLoginRequestRejected(Person person, String reasonText) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.RADIUS_LOGIN_REQUEST_REJECTED);
		auditLog.setMessage("Login forespørgsel afvist");
		
		auditLog.setDetails(new AuditLogDetail());
		auditLog.getDetails().setDetailType(DetailType.TEXT);
		auditLog.getDetails().setDetailContent(reasonText);

		log(auditLog, person, null);
	}
	
	public void cprLookupByAdmin(Person admin, String cpr) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.CPR_LOOKUP);
		auditLog.setMessage("CPR-opslag på " + cpr +  " foretaget af en administrator");

		log(auditLog, null, admin);
	}
	
	public void usedNemID(String PID, Person person) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.USED_NEMID);
		AuditLogDetail detail = new AuditLogDetail();
		detail.setDetailContent(PID);
		detail.setDetailType(DetailType.TEXT);
		auditLog.setDetails(detail);
		auditLog.setMessage("NemID anvendt");

		log(auditLog, person, null);
	}
	
	public void rejectedUnknownPerson(String identifier) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.REJECTED_UNKNOWN_PERSON);
		AuditLogDetail detail = new AuditLogDetail();
		detail.setDetailContent(identifier);
		detail.setDetailType(DetailType.TEXT);
		auditLog.setDetails(detail);
		auditLog.setMessage("Login afvist, personens personnummer er ikke kendt af systemet");

		log(auditLog, null, null);
	}
	
	public void usedNemLogin(Person person, NSISLevel nsisLevel, String rawToken) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.USED_NEMLOGIN);
		AuditLogDetail detail = new AuditLogDetail();
		detail.setDetailContent(rawToken);
		detail.setDetailType(DetailType.XML);
		auditLog.setDetails(detail);
		auditLog.setMessage("NemLog-in anvendt (sikringsniveau: " + nsisLevel.toClaimValue() + ")");
		
		log(auditLog, person, null);
	}

	public void personDead(Person person) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.DISABLED_DEAD);
		auditLog.setMessage("Brugerkonto spærret da personen er angivet som død i CPR registeret");

		log(auditLog, person, null);
	}
	
	public void checkPersonIsDead(Person person, Boolean dead) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.CHECK_DEAD);
		auditLog.setMessage("Kontrolopslag i CPR registeret for at afgøre om personen er død");
		AuditLogDetail detail = new AuditLogDetail();
		detail.setDetailContent("Civilstand: " + ((dead != null) ? (dead == true ? "død" : "levende") :  "ukendt"));
		detail.setDetailType(DetailType.TEXT);
		auditLog.setDetails(detail);

		log(auditLog, person, null);
	}
	
	public void deletedMFADevice(Person person, String name, String deviceId, String type) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.DELETED_MFA_DEVICE);
		auditLog.setMessage("2-faktor enhed slettet af personen selv");
		AuditLogDetail detail = new AuditLogDetail();
		detail.setDetailContent("Navn: " + name + "\nDeviceId: " + deviceId + "\nType: " + type);
		detail.setDetailType(DetailType.TEXT);
		auditLog.setDetails(detail);

		log(auditLog, person, null);
	}
	
	public void updateFromCprJob() {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.UPDATE_FROM_CPR);
		auditLog.setMessage("Ajourføring af alle personers data fra CPR registeret");

		log(auditLog, null, null);
	}

	public void updateNameFromCpr(Person person) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.UPDATE_FROM_CPR);
		auditLog.setMessage("Ajourføring af navn fra CPR registeret");
		AuditLogDetail detail = new AuditLogDetail();
		detail.setDetailContent("Navn opdateret");
		detail.setDetailType(DetailType.TEXT);
		auditLog.setDetails(detail);

		log(auditLog, person, null);
	}
	
	public void sessionExpired(Person person) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.SESSION_EXPIRED);
		auditLog.setMessage("Session udløbet");

		log(auditLog, person, null);
	}

	private void log(AuditLog auditLog, Person person, Person admin) {
		auditLog.setCorrelationId(getCorrelationId());
		auditLog.setIpAddress(getIpAddress());

		if (person != null) {
			auditLog.setPerson(person);
			auditLog.setPersonName(person.getName());
			auditLog.setPersonDomain(person.getDomain().getName());
			auditLog.setCpr(person.getCpr());
		}

		if (admin != null) {
			auditLog.setPerformerId(admin.getId());
			auditLog.setPerformerName(admin.getName());
		}
		
		auditLogDao.save(auditLog);
	}

	private String getCorrelationId() {
		try {
			String sessionID = RequestContextHolder.currentRequestAttributes().getSessionId();
			
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] encodedHash = digest.digest(sessionID.getBytes(Charset.forName("UTF-8")));

			return bytesToHex(encodedHash);
		}
		catch (Exception ex) {
			return "SYSTEM-" + UUID.randomUUID().toString();
		}
	}

	private static String bytesToHex(byte[] hash) {
		StringBuilder hexString = new StringBuilder(2 * hash.length);

		for (int i = 0; i < hash.length; i++) {
			String hex = Integer.toHexString(0xff & hash[i]);

			if (hex.length() == 1) {
				hexString.append('0');
			}

			hexString.append(hex);
		}

		return hexString.toString();
	}

	private static String getIpAddress() {
		String remoteAddr = "";

		HttpServletRequest request = getRequest();
		if (request != null) {
			remoteAddr = request.getHeader("X-FORWARDED-FOR");
			if (remoteAddr == null || "".equals(remoteAddr)) {
				remoteAddr = request.getRemoteAddr();
			}
		}

		return remoteAddr;
	}

	private static HttpServletRequest getRequest() {
		try {
			return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
		}
		catch (IllegalStateException ex) {
			return null;
		}
	}

	@Transactional(rollbackFor = Exception.class)
	public void cleanupLogs() {
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime tts = now.plusMonths(-13);
	
		auditLogDao.deleteByTtsBefore(tts);
	}
}
