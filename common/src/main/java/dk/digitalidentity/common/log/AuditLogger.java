package dk.digitalidentity.common.log;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.config.Constants;
import dk.digitalidentity.common.dao.AuditLogDao;
import dk.digitalidentity.common.dao.model.AuditLog;
import dk.digitalidentity.common.dao.model.AuditLogDetail;
import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.PasswordSetting;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.PrivacyPolicy;
import dk.digitalidentity.common.dao.model.SessionSetting;
import dk.digitalidentity.common.dao.model.TUTermsAndConditions;
import dk.digitalidentity.common.dao.model.TemporaryClientSessionKey;
import dk.digitalidentity.common.dao.model.TermsAndConditions;
import dk.digitalidentity.common.dao.model.enums.DetailType;
import dk.digitalidentity.common.dao.model.enums.ForceMFARequired;
import dk.digitalidentity.common.dao.model.enums.LogAction;
import dk.digitalidentity.common.dao.model.enums.LogWatchSettingKey;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.dao.model.enums.RequirementCheckResult;
import dk.digitalidentity.common.dao.model.enums.SettingKey;
import dk.digitalidentity.common.service.EmailService;
import dk.digitalidentity.common.service.LogWatchSettingService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.PersonStatisticsService;
import dk.digitalidentity.common.service.SettingService;
import dk.digitalidentity.common.service.mfa.model.MfaClient;
import dk.digitalidentity.util.ZipUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class AuditLogger {

	@Autowired
	private AuditLogDao auditLogDao;
	
	@Autowired
	private LogWatchSettingService logWatchSettingService;
	
	@Autowired
	private EmailService emailService;
	
	@Autowired
	private MessageSource messageSource;

	@Autowired
	private SettingService settingService;
	
	@Autowired
	private CommonConfiguration commonConfiguration;

	@Autowired
	private PersonStatisticsService personStatisticsService;

	public void errorSentToSP(Person person, ErrorLogDto errorDetail) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.ERROR_SENT_TO_SP);
		auditLog.setMessage(errorDetail.getMessage());

		AuditLogDetail detail = new AuditLogDetail();
		auditLog.setDetails(detail);

		detail.setDetailType(DetailType.JSON);
		try {
			ObjectMapper mapper = new ObjectMapper();
			mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

			auditLog.getDetails().setDetailContent(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(errorDetail));
		}
		catch (JsonProcessingException e) {
			log.error("Could not serialize ErrorDetail");
		}

		log(auditLog, person, null);
	}

	public void failedClaimEvaluation(Person person, String spName, String claimName, String message) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.TRACE_LOG);
		auditLog.setMessage("Kunne ikke danne claim '" + claimName + "' til " + spName);

		if (StringUtils.hasLength(message)) {
			AuditLogDetail detail = new AuditLogDetail();
			detail.setDetailContent(message);
			detail.setDetailType(DetailType.TEXT);
			auditLog.setDetails(detail);
		}

		log(auditLog, person, null);
	}
	
	public void exchangeTemporaryTokenForSession(TemporaryClientSessionKey temporaryClient, String info, String userAgent, String version) {
		if (settingService.getBoolean(SettingKey.TRACE_LOGGING)) {
			AuditLog auditLog = new AuditLog();
			auditLog.setLogAction(LogAction.TRACE_LOG);
			auditLog.setMessage("Midlertidig token udstedt " + temporaryClient.getTts() + " vekslet til blivende session");
	
			StringBuilder sb = new StringBuilder();
	
			if (StringUtils.hasLength(userAgent)) {
				sb.append("User-Agent: ").append(userAgent).append("\n");
			}
	
			if (StringUtils.hasLength(info)) {
				sb.append("Trace info: ").append(info).append("\n");
			}

			if (StringUtils.hasLength(version)) {
				sb.append("WCP version: ").append(version).append("\n");
			}
	
			String details = sb.toString();
			if (StringUtils.hasLength(details)) {
				AuditLogDetail detail = new AuditLogDetail();
				detail.setDetailContent(details);
				detail.setDetailType(DetailType.TEXT);
				auditLog.setDetails(detail);
			}
	
			log(auditLog, temporaryClient.getPerson(), null);
		}
	}

	public void sessionNotIssuedIPChanged(TemporaryClientSessionKey temporaryClient, String info) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.SESSION_NOT_ISSUED_IP_CHANGED);
		auditLog.setMessage("IP adresse ændret midt i etablering af session via Windows");

		if (StringUtils.hasLength(info)) {
			AuditLogDetail detail = new AuditLogDetail();
			detail.setDetailContent("Trace info: " + info);
			detail.setDetailType(DetailType.TEXT);
			auditLog.setDetails(detail);
		}

		log(auditLog, temporaryClient.getPerson(), null);
	}
	
	public void toggleRoleByAdmin(Person person, Person admin, String role, boolean enabled) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(enabled ? LogAction.ADDED_ROLE_BY_ADMIN : LogAction.REMOVED_ROLE_BY_ADMIN);

		String roleStr = "";
		switch (role) {
			case Constants.ROLE_ADMIN:
				roleStr = "administrator rollen";
				break;
			case Constants.ROLE_SUPPORTER:
				roleStr = "supporter rollen";

				// Log supporter domain
				if (enabled && person.getSupporter() != null) {
					AuditLogDetail detail = new AuditLogDetail();
					detail.setDetailType(DetailType.TEXT);
					detail.setDetailContent("domain: " + (person.getSupporter().getDomain() == null ? "Alle domæner" : person.getSupporter().getDomain().getName()));
					auditLog.setDetails(detail);
				}
				break;
			case Constants.ROLE_REGISTRANT:
				roleStr = "registrant rollen";
				break;
			case Constants.ROLE_SERVICE_PROVIDER_ADMIN:
				roleStr = "tjenesteudbyderadministrator rollen";
				break;
			case Constants.ROLE_USER_ADMIN:
				roleStr = "brugeradministrator rollen";
				break;
			case Constants.ROLE_KODEVISER_ADMIN:
				roleStr = "kodeviseradministrator rollen";
				break;
			case Constants.ROLE_PASSWORD_RESET_ADMIN:
				roleStr = "kodeordsadministrator rollen";
				break;
			case Constants.ROLE_INSTITUTION_STUDENT_PASSWORD_ADMIN:
				roleStr = "elev-kodeordsadministrator rollen";
				break;
			default:
				roleStr = role;
				log.error("Unknown role: " + role);
				break;
		}
		auditLog.setMessage((enabled ? "Tildelt " : "Frataget ") + roleStr + " af en administrator");

		String emails = logWatchSettingService.getAlarmEmailRecipients();
		if (logWatchSettingService.getBooleanWithDefaultFalse(LogWatchSettingKey.LOG_WATCH_ENABLED) && StringUtils.hasLength(emails)) {
			String subject = "(OS2faktor " + commonConfiguration.getEmail().getFromName() + ") Overvågning af logs: Rolle ændret";

			// $NAVN ($UUID) har fået tildelt/frataget $ROLLE
			// Tildelt af: $NAVN ($UUID)
			// I system: $System
			StringBuilder sb = new StringBuilder();
			sb.append(person.getName()).append(" (").append(person.getSamaccountName()).append(") har fået ").append(enabled ? "tildelt " : "frataget ").append(roleStr).append("<br>");
			sb.append("Ændret af: ").append(admin.getName()).append(" (").append(admin.getSamaccountName()).append(")<br>").append("I systemet: OS2faktor").append("<br>");

			if (auditLog.getDetails() != null && StringUtils.hasLength(auditLog.getDetails().getDetailContent())) {
				sb.append("<pre>" + auditLog.getDetails().getDetailContent() + "</pre>");
			}

			emailService.sendMessage(emails, subject, sb.toString(), null);
		}

		log(auditLog, person, admin);
	}

	public void sessionKeyIssued(TemporaryClientSessionKey saved) {
		if (settingService.getBoolean(SettingKey.TRACE_LOGGING)) {
			AuditLog auditLog = new AuditLog();
			auditLog.setLogAction(LogAction.TRACE_LOG);
			auditLog.setMessage("Session etableret via Windows login");
	
			// Add details of which passwords has been changed
			AuditLogDetail detail = new AuditLogDetail();
			detail.setDetailType(DetailType.TEXT);
	
			String detailMsg = "NSIS Level: " + saved.getNsisLevel();
			detail.setDetailContent(detailMsg);
			auditLog.setDetails(detail);
	
			log(auditLog, saved.getPerson(), null);
		}
	}
	
	public void deleteFromDataset(List<Person> persons) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.DELETED_FROM_DATASET);
		auditLog.setMessage("Stamdata på " + persons.size() + " person(er) er blevet slettet fra databasen");
		auditLog.setCorrelationId(getCorrelationId());
		auditLog.setIpAddress(getIpAddress());
		
		StringBuilder builder = new StringBuilder();
		for (Person person : persons) {
			builder.append(person.getName() + " (" + person.getId() + ")\n");
		}
		
		AuditLogDetail detail = new AuditLogDetail();
		detail.setDetailType(DetailType.TEXT);
		detail.setDetailContent(builder.toString());
		auditLog.setDetails(detail);

		log(auditLog, null, null);
	}

	public void removedAllFromDataset(List<Person> persons) {
		ArrayList<AuditLog> logs = new ArrayList<>();
		for (Person person : persons) {
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
		
		String correlationId = getCorrelationId();
		String ipAddress = getIpAddress();
		
		for (Person person : people) {
			AuditLog auditLog = new AuditLog();
			auditLog.setLogAction(LogAction.ADDED_TO_DATASET);
			auditLog.setMessage("Stamdata for bruger indlæst");

			auditLog.setCorrelationId(correlationId);
			auditLog.setIpAddress(ipAddress);
			auditLog.setPerson(person);
			auditLog.setPersonName(person.getName());
			auditLog.setPersonDomain(person.getDomain().getName());
			auditLog.setCpr(person.getCpr());

			logs.add(auditLog);
			
			// only called for bulk-create, and here we need to ensure that this field is auditlogged as well
			if (person.isTransferToNemlogin()) {
				auditLog = new AuditLog();
				auditLog.setLogAction(LogAction.CHANGED_TRANSFER_TO_NEMLOGIN);
				auditLog.setMessage("Bruger skal overføres til MitID Erhverv");

				auditLog.setCorrelationId(correlationId);
				auditLog.setIpAddress(ipAddress);
				auditLog.setPerson(person);
				auditLog.setPersonName(person.getName());
				auditLog.setPersonDomain(person.getDomain().getName());
				auditLog.setCpr(person.getCpr());

				logs.add(auditLog);
			}

			// only called for bulk-create, and here we need to ensure that this field is auditlogged as well
			if (person.isNsisAllowed()) {
				auditLog = new AuditLog();
				auditLog.setLogAction(LogAction.CHANGED_NSIS_ALLOWED);
				auditLog.setMessage("Bruger godkendt til NSIS erhvervsidentitet");

				auditLog.setCorrelationId(correlationId);
				auditLog.setIpAddress(ipAddress);
				auditLog.setPerson(person);
				auditLog.setPersonName(person.getName());
				auditLog.setPersonDomain(person.getDomain().getName());
				auditLog.setCpr(person.getCpr());

				logs.add(auditLog);
			}
			
			// only called for bulk-create, and here we need to ensure that this field is auditlogged as well
			if (person.isPrivateMitId()) {
				auditLog = new AuditLog();
				auditLog.setLogAction(LogAction.CHANGED_ALLOW_PRIVATE_MITID);
				auditLog.setMessage("Bruger må anvende privat MitID til NemLog-in");

				auditLog.setCorrelationId(correlationId);
				auditLog.setIpAddress(ipAddress);
				auditLog.setPerson(person);
				auditLog.setPersonName(person.getName());
				auditLog.setPersonDomain(person.getDomain().getName());
				auditLog.setCpr(person.getCpr());

				logs.add(auditLog);
			}

			// only called for bulk-create, and here we need to ensure that this field is auditlogged as well
			if (person.isQualifiedSignature()) {
				auditLog = new AuditLog();
				auditLog.setLogAction(LogAction.CHANGED_ALLOW_QUALIFIED_SIGNATURE);
				auditLog.setMessage("Bruger må udføre kvalificeret underskrift");

				auditLog.setCorrelationId(correlationId);
				auditLog.setIpAddress(ipAddress);
				auditLog.setPerson(person);
				auditLog.setPersonName(person.getName());
				auditLog.setPersonDomain(person.getDomain().getName());
				auditLog.setCpr(person.getCpr());

				logs.add(auditLog);
			}
		}

		auditLogDao.saveAll(logs);
	}

	public void loginStudentPasswordChange(String parentCpr) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.LOGIN);
		auditLog.setMessage("Login billet modtaget til kodeskift for elever");
		
		// Add details of which passwords has been changed
		AuditLogDetail detail = new AuditLogDetail();
		detail.setDetailType(DetailType.TEXT);
		detail.setDetailContent("Forældre/værge personnummer: " + (PersonService.maskCpr(parentCpr)));
		auditLog.setDetails(detail);

		log(auditLog, null, null);
	}

	public void loginSelfService(Person person, String assertion) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.LOGIN_SELFSERVICE);
		auditLog.setMessage("Login billet modtaget i selvbetjening");
		auditLog.setDetails(new AuditLogDetail());
		auditLog.getDetails().setDetailType(DetailType.XML);
		auditLog.getDetails().setDetailContent(assertion);

		log(auditLog, person, null);

		personStatisticsService.setLastSelfServiceLogin(person);
	}
	
	public void login(Person person, String loginTo, String assertion, String userAgent) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.LOGIN);
		auditLog.setMessage("Login til " + loginTo);
		auditLog.setDetails(new AuditLogDetail());
		auditLog.getDetails().setDetailType(DetailType.XML);
		auditLog.getDetails().setDetailContent(assertion);
		auditLog.getDetails().setDetailSupplement(userAgent);

		log(auditLog, person, null);

		if (person != null) {
			personStatisticsService.setLastLogin(person);
		}
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

	public void badPassword(Person person, boolean isWCP) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.WRONG_PASSWORD);
		auditLog.setMessage("Forkert kodeord indtastet" + (isWCP ? " (Windows Logon)" : ""));

		log(auditLog, person, null);
	}
	
	public void tooManyBadPasswordAttempts(Person person, long minutesLocked) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.TOO_MANY_ATTEMPTS);
		auditLog.setMessage("Kontoen er spærret de næste " + minutesLocked + " minutter grundet for mange forkerte kodeord i træk");
		
		log(auditLog, person, null);
	}

	public void goodPassword(Person person, boolean authenticatedWithADPassword, boolean expired) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.RIGHT_PASSWORD);
		auditLog.setMessage("Kodeord anvendt");
		
		// Add details of what the password was validated against
		AuditLogDetail detail = new AuditLogDetail();
		detail.setDetailType(DetailType.TEXT);
		detail.setDetailContent(
				"Kodeordet er valideret mod: " + (authenticatedWithADPassword ? "AD" : "bruger-databasen") +
				"\nKodeordet er udløbet: " + (expired ? "Ja" : "Nej"));
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

		personStatisticsService.setLastMFAUse(person);
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

	public void passwordFilterValidationFailed(Person person, String reason) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.PASSWORD_FILTER_VALIDATION_FAILED);
		auditLog.setMessage("Kontrol af valideringsregler på kodeord resulterede i afvisning til AD");

		AuditLogDetail detail = new AuditLogDetail();
		detail.setDetailType(DetailType.TEXT);
		detail.setDetailContent(reason);
		auditLog.setDetails(detail);

		log(auditLog, person, null);
	}

	public void changePasswordByPerson(Person person, boolean replicateToAD) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.CHANGE_PASSWORD);
		auditLog.setMessage("Kodeord skiftet af personen selv");

		// Add details of which passwords has been changed
		AuditLogDetail detail = new AuditLogDetail();
		detail.setDetailType(DetailType.TEXT);
		detail.setDetailContent("Replikering til AD: " + (replicateToAD ? "Ja" : "Nej"));
		auditLog.setDetails(detail);

		log(auditLog, person, null);

		personStatisticsService.setLastPasswordChange(person);
	}

	public void changePasswordByParent(Person child, String parentCpr) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.CHANGE_PASSWORD);
		auditLog.setMessage("Kodeord skiftet af forældre/værge");

		// Add details of which passwords has been changed
		AuditLogDetail detail = new AuditLogDetail();
		detail.setDetailType(DetailType.TEXT);
		detail.setDetailContent("Forældre/værge personnummer: " + (PersonService.maskCpr(parentCpr)));
		auditLog.setDetails(detail);

		log(auditLog, child, null);
	}
	
	public void changePasswordByAdmin(Person admin, Person person, boolean replicateToAD) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.CHANGE_PASSWORD);
		auditLog.setMessage("Kodeord skiftet af administrator - " + (person.isNsisAllowed() ? "på bruger med erhvervsidentitet" : "på bruger uden erhvervsidentitet"));

		// Add details of which passwords has been changed
		AuditLogDetail detail = new AuditLogDetail();
		detail.setDetailType(DetailType.TEXT);
		detail.setDetailContent("Replikering til AD: " + (replicateToAD ? "Ja" : "Nej") + "\nBruger har erhvervsidentitet: " + (person.isNsisAllowed() ? "Ja" : "Nej"));
		auditLog.setDetails(detail);

		log(auditLog, person, admin);

		personStatisticsService.setLastPasswordChange(person);
	}
	
	public void unlockAccountByPerson(Person person) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.UNLOCK_ACCOUNT);
		auditLog.setMessage("AD konto låst op af personen selv");

		log(auditLog, person, null);

		personStatisticsService.setLastUnlock(person);
	}

	public void activatedByPerson(Person person, String mitIdNameId) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.ACTIVATE);
		auditLog.setMessage("Brugerkontoen er blevet aktiveret af brugeren selv");

		AuditLogDetail detail = new AuditLogDetail();
		detail.setDetailType(DetailType.TEXT);

		StringBuilder sb = new StringBuilder();
		if (StringUtils.hasLength(mitIdNameId)) {
			sb.append("MitID NameID: ").append(mitIdNameId);
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
			mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

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
			mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

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
			mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

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
			mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

			String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(details);
			auditLog.getDetails().setDetailContent(json);
		}
		catch (JsonProcessingException ex) {
			log.error("Could not serialize IdentificationDetails", ex);
		}

		log(auditLog, person, performedBy);

		personStatisticsService.setLastPasswordChange(person);
	}

	public void manualYubiKeyInitalization(IdentificationDetails details, Person person, Person performedBy) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.MANUAL_YUBIKEY_REGISTRATION);
		auditLog.setMessage("Yubikey tilknyttet af administrator");

		auditLog.setDetails(new AuditLogDetail());
		auditLog.getDetails().setDetailType(DetailType.JSON);

		try {
			ObjectMapper mapper = new ObjectMapper();
			mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

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
	
	public void deactivateByAdmin(Person person, Person admin, String reason) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.DEACTIVATE_BY_ADMIN);
		auditLog.setMessage("Brugeren er blevet spærret af en administrator");
		AuditLogDetail detail = new AuditLogDetail();
		detail.setDetailContent("Begrundelse for spærring: " + (reason != null ? reason : ""));
		detail.setDetailType(DetailType.TEXT);
		auditLog.setDetails(detail);

		String emails = logWatchSettingService.getAlarmEmailRecipients();
		if (logWatchSettingService.getBooleanWithDefaultFalse(LogWatchSettingKey.LOG_WATCH_ENABLED) && StringUtils.hasLength(emails)) {
			String subject = "(OS2faktor " + commonConfiguration.getEmail().getFromName() + ") Overvågning af logs: brugerkonto spærret af administrator";
			String message =
					"Brugerkonto: " + person.getName() + " (" + person.getSamaccountName() + ")<br>" +
					"Spærret af: " + admin.getName() + " (" + admin.getSamaccountName() + ")<br>" +
					"Detaljer: <br>" + auditLog.getDetails().getDetailContent();

			emailService.sendMessage(emails, subject, message, null);
		}

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
		auditLog.setMessage("Kodeordsregler ændret"  + ((admin == null ) ? " af systemet" : ""));

		auditLog.setDetails(new AuditLogDetail());
		auditLog.getDetails().setDetailType(DetailType.JSON);

		try {
			ObjectMapper mapper = new ObjectMapper();
			mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

			auditLog.getDetails().setDetailContent(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(passwordSettings));
		}
		catch (JsonProcessingException e) {
			log.error("Could not serialize PasswordSettings", e);
		}

		String emails = logWatchSettingService.getAlarmEmailRecipients();
		if (logWatchSettingService.getBooleanWithDefaultFalse(LogWatchSettingKey.LOG_WATCH_ENABLED) && StringUtils.hasLength(emails)) {
			String subject = "(OS2faktor " + commonConfiguration.getEmail().getFromName() + ") Overvågning af logs: Kodeordsregler ændret";
			String message = (admin != null)
					?
							("Ændret af: " + admin.getName() + " (" + admin.getSamaccountName() + ")<br>" +
							"Kodeordsregler: <pre>" + auditLog.getDetails().getDetailContent() + "</pre>")
					:
							("Ændret af: System-migrering af kodeordsregler<br>" +
							"Kodeordsregler: <pre>" + auditLog.getDetails().getDetailContent() + "</pre>");

			emailService.sendMessage(emails, subject, message, null);
		}

		log(auditLog, admin, admin, passwordSettings.getDomain());
	}

	public void resetHardwareToken(String serialNumber, Person admin) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.KODEVISER_RESET);
		auditLog.setMessage("Kodeviser nulstillet: " + serialNumber);

		log(auditLog, admin, admin);
	}
	
	public void changeSessionSettings(SessionSetting sessionSettings, Person admin) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.CHANGE_SESSION_SETTINGS);
		auditLog.setMessage("Sessionindstillinger ændret");

		auditLog.setDetails(new AuditLogDetail());
		auditLog.getDetails().setDetailType(DetailType.JSON);

		try {
			ObjectMapper mapper = new ObjectMapper();
			mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

			auditLog.getDetails().setDetailContent(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(sessionSettings));
		} catch (JsonProcessingException e) {
			log.error("Could not serialize SessionSettings");
		}

		String emails = logWatchSettingService.getAlarmEmailRecipients();
		if (logWatchSettingService.getBooleanWithDefaultFalse(LogWatchSettingKey.LOG_WATCH_ENABLED) && StringUtils.hasLength(emails)) {
			String subject = "(OS2faktor " + commonConfiguration.getEmail().getFromName() + ") Overvågning af logs: Sessionsudløb ændret";
			String message =
					"Ændret af: " + admin.getName() + " (" + admin.getSamaccountName() + ")<br>" +
					"Sessionsudløb: <pre>" + auditLog.getDetails().getDetailContent() + "</pre>";

			emailService.sendMessage(emails, subject, message, null);
		}

		log(auditLog, admin, admin, sessionSettings.getDomain());
	}
	
	public void changeKombitMfaSettings(Person admin, String entityId, ForceMFARequired forceMfaRequired) {
		String ruleTxt = messageSource.getMessage(forceMfaRequired.getMessage(), null, null);

		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.CHANGE_KOMBIT_MFA);
		auditLog.setMessage("MFA regler for " + entityId + " ændret");

		auditLog.setDetails(new AuditLogDetail());
		auditLog.getDetails().setDetailType(DetailType.TEXT);
		auditLog.getDetails().setDetailContent("Reglen for MFA på it-systemet " + entityId + " ændret til " + ruleTxt);

		String emails = logWatchSettingService.getAlarmEmailRecipients();
		if (logWatchSettingService.getBooleanWithDefaultFalse(LogWatchSettingKey.LOG_WATCH_ENABLED) && StringUtils.hasLength(emails)) {
			String subject = "(OS2faktor " + commonConfiguration.getEmail().getFromName() + ") Overvågning af logs: MFA regler for KOMBIT fagsystem ændret";
			String message =
					"Ændret af: " + admin.getName() + " (" + admin.getSamaccountName() + ")<br>" +
					"It-system: " + entityId + "<br>" +
					"MFA regel: " + ruleTxt;

			emailService.sendMessage(emails, subject, message, null);
		}

		log(auditLog, admin, admin);
	}

	public void changeTUTerms(TUTermsAndConditions terms, Person admin) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.CHANGE_TU_TERMS_AND_CONDITIONS);
		auditLog.setMessage("Vilkår opdateret");

		auditLog.setDetails(new AuditLogDetail());
		auditLog.getDetails().setDetailType(DetailType.TEXT);
		auditLog.getDetails().setDetailContent(terms.getContent());

		log(auditLog, admin, admin);
	}
	
	public void changeTerms(TermsAndConditions termsAndConditions, Person admin,  boolean requireReapproval) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.CHANGE_TERMS_AND_CONDITIONS);
		auditLog.setMessage("Vilkår opdateret" + (requireReapproval ? " (kræver gen-accept)" : ""));

		auditLog.setDetails(new AuditLogDetail());
		auditLog.getDetails().setDetailType(DetailType.TEXT);
		auditLog.getDetails().setDetailContent(termsAndConditions.getContent());

		log(auditLog, admin, admin);
	}

	public void sentEBoks(Person person, String title)  {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.SENT_EBOKS);
		auditLog.setMessage("Besked sendt via Digital Post");

		auditLog.setDetails(new AuditLogDetail());
		auditLog.getDetails().setDetailType(DetailType.TEXT);
		auditLog.getDetails().setDetailContent("Besked: " + title);

		log(auditLog, person, null);
	}

	public void sentEmail(Person person, String title)  {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.SENT_MAIL);
		auditLog.setMessage("Besked sendt via e-mail");

		auditLog.setDetails(new AuditLogDetail());
		auditLog.getDetails().setDetailType(DetailType.TEXT);
		auditLog.getDetails().setDetailContent("Besked: " + title);

		log(auditLog, person, null);
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

	public void deletedUser(Person person, Person admin) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.DELETED_USER);
		auditLog.setMessage("Stamdata for person slettet");

		log(auditLog, person, admin, person.getDomain());
	}

	public void authnRequest(Person person, String authnRequest, String sentBy) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.AUTHN_REQUEST);
		auditLog.setMessage("SAML 2.0 Login forespørgsel fra " + sentBy);

		auditLog.setDetails(new AuditLogDetail());
		auditLog.getDetails().setDetailType(DetailType.XML);
		auditLog.getDetails().setDetailContent(authnRequest);

		log(auditLog, person, null);
	}

	public void oidcAuthorizationRequest(Person person, String authorizationRequest, String sentBy) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.AUTHN_REQUEST);
		auditLog.setMessage("OpenID Connect login forespørgsel fra " + sentBy);

		auditLog.setDetails(new AuditLogDetail());
		auditLog.getDetails().setDetailType(DetailType.JSON);
		auditLog.getDetails().setDetailContent(authorizationRequest);

		log(auditLog, person, null);
	}

	public void wsFederationLoginRequest(Person person, String sentBy, String loginRequestParameters) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.AUTHN_REQUEST);
		auditLog.setMessage("WS-Federation login forespørgsel fra " + sentBy);

		auditLog.setDetails(new AuditLogDetail());
		auditLog.getDetails().setDetailType(DetailType.TEXT);
		auditLog.getDetails().setDetailContent(loginRequestParameters);

		log(auditLog, person, null);
	}

	public void entraMfaLoginRequest(Person person, String upn, String userAgent) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.AUTHN_REQUEST);
		auditLog.setMessage("EntraID MFA login forespørgsel for " + upn);

		auditLog.setDetails(new AuditLogDetail());
		auditLog.getDetails().setDetailType(DetailType.TEXT);
		auditLog.getDetails().setDetailContent(userAgent);

		log(auditLog, person, null);
	}
	
	public void wsFederationLogin(Person person, String sentBy, String response) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.LOGIN);
		auditLog.setMessage("Login til " + sentBy);

		auditLog.setDetails(new AuditLogDetail());
		auditLog.getDetails().setDetailType(DetailType.XML);
		auditLog.getDetails().setDetailContent(response);

		log(auditLog, person, null);

		if (person != null) {
			personStatisticsService.setLastLogin(person);
		}
	}

	public void oidcAuthorizationRequestResponse(Person person, String sentBy) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.LOGIN);
		auditLog.setMessage("Login til " + sentBy);

		log(auditLog, person, null);

		if (person != null) {
			personStatisticsService.setLastLogin(person);
		}
	}

	public void sentJWTIdToken(String idToken, Person person) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.OIDC_JWT_ID_TOKEN);
		auditLog.setMessage("OpenID Connect token udstedt");

		ObjectMapper objectMapper = new ObjectMapper();
		try {
			objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
			JsonNode jsonNode = objectMapper.readTree(idToken);
			idToken = objectMapper.writeValueAsString(jsonNode);
		}
		catch (Exception ignored) {
			;
		}

		auditLog.setDetails(new AuditLogDetail());
		auditLog.getDetails().setDetailType(DetailType.JSON);
		auditLog.getDetails().setDetailContent(idToken);

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
		auditLog.setMessage(nsisAllowed ? "Bruger godkendt til erhvervsidentitet" : "Bruger frataget godkendelse af deres erhvervsidentitet");

		log(auditLog, person, null);
	}

	public void nsisAllowedChangedByAdmin(Person admin, Person person, boolean nsisAllowed, String reasonText) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.CHANGED_NSIS_ALLOWED);
		auditLog.setMessage(nsisAllowed ? "Bruger godkendt til erhvervsidentitet af administrator" : "Bruger frataget godkendelse af deres erhvervsidentitet af administrator");

		if (StringUtils.hasLength(reasonText)) {
			auditLog.setDetails(new AuditLogDetail());
			auditLog.getDetails().setDetailType(DetailType.TEXT);
			auditLog.getDetails().setDetailContent(reasonText);
		}

		log(auditLog, person, admin);
	}

	public void allowPrivateMitIdChanged(Person person, boolean allowPrivateMitID) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.CHANGED_ALLOW_PRIVATE_MITID);
		auditLog.setMessage(allowPrivateMitID ? "Bruger må anvende privat MitID til NemLog-in" : "Bruger må ikke længere anvende privat MitID til NemLog-in");

		log(auditLog, person, null);
	}

	public void allowQualifiedSignatureChanged(Person person, boolean allowQualifiedSignature) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.CHANGED_ALLOW_QUALIFIED_SIGNATURE);
		auditLog.setMessage(allowQualifiedSignature ? "Bruger må udføre kvalificeret underskrift" : "Bruger må ikke længere udføre kvalificeret underskrift");

		log(auditLog, person, null);
	}

	public void transferToNemloginChanged(Person person, boolean transferToNemlogin) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.CHANGED_TRANSFER_TO_NEMLOGIN);
		auditLog.setMessage(transferToNemlogin ? "Bruger skal overføres til MitID Erhverv" : "Bruger skal fjernes fra MitID Erhverv");

		log(auditLog, person, null);
	}

	// TODO: shouldn't we DO something with username here?
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
	
	public void rejectedUnknownPerson(String identifier, String cpr) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.REJECTED_UNKNOWN_PERSON);
		AuditLogDetail detail = new AuditLogDetail();
		StringBuilder sb = new StringBuilder();
		if (StringUtils.hasLength(identifier)) {
			sb.append("PID: " + identifier).append("\n");
		}
		if (StringUtils.hasLength(cpr)) {
			sb.append("CPR: " + PersonService.maskCpr(cpr));
		}
		detail.setDetailContent(sb.toString());
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
		auditLog.setMessage("Brugerkonto spærret da personen er angivet med ugyldig tilstand i CPR registeret");

		log(auditLog, person, null);
		
		String emails = logWatchSettingService.getAlarmEmailRecipients();
		if (logWatchSettingService.getBooleanWithDefaultFalse(LogWatchSettingKey.LOG_WATCH_ENABLED) && logWatchSettingService.getBooleanWithDefaultFalse(LogWatchSettingKey.PERSON_DEAD_OR_DISENFRANCHISED_ENABLED) && StringUtils.hasLength(emails)) {
			log.warn("CPR says that person with uuid " + person.getUuid() + " is dead");
			String subject = "(OS2faktor " + commonConfiguration.getEmail().getFromName() + ") Overvågning af logs: Person erklæret død eller bortkommet af cpr registeret";
			String message = "Personen " + person.getName() + "(" + person.getSamaccountName() + ") er erklæret død eller bortkommet af cpr registeret";

			emailService.sendMessage(emails, subject, message, null);
		}
	}

	public void personDisenfranchised(Person person) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.DISABLED_DISENFRANCHISED);
		auditLog.setMessage("Brugerkonto spærret da personen er angivet som umyndig i CPR registeret");

		log(auditLog, person, null);
		
		String emails = logWatchSettingService.getAlarmEmailRecipients();
		if (logWatchSettingService.getBooleanWithDefaultFalse(LogWatchSettingKey.LOG_WATCH_ENABLED) && logWatchSettingService.getBooleanWithDefaultFalse(LogWatchSettingKey.PERSON_DEAD_OR_DISENFRANCHISED_ENABLED) && StringUtils.hasLength(emails)) {
			log.warn("CPR says that person with uuid " + person.getUuid() + " is disenfranchised");
			String subject = "(OS2faktor " + commonConfiguration.getEmail().getFromName() + ") Overvågning af logs: Person erklæret umyndiggjort af cpr registeret";
			String message = "Personen " + person.getName() + "(" + person.getSamaccountName() + ") er erklæret umyndiggjort af cpr registeret";

			emailService.sendMessage(emails, subject, message, null);
		}
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

	public void traceLog(Person person, String message, String details) {
		if (settingService.getBoolean(SettingKey.TRACE_LOGGING)) {
			AuditLog auditLog = new AuditLog();
			auditLog.setLogAction(LogAction.TRACE_LOG);
			auditLog.setMessage(message);
			AuditLogDetail detail = new AuditLogDetail();
			detail.setDetailContent(details);
			detail.setDetailType(DetailType.TEXT);
			auditLog.setDetails(detail);

			log(auditLog, person, null);
		}
	}

	public void createdNemLoginUser(Person person) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.MITID_ERVHERV_ACTION);
		auditLog.setMessage("Oprettet i MitID Erhverv med uuid " + person.getNemloginUserUuid());

		log(auditLog, null, null);
	}

	public void reactivatedNemLoginUser(Person person) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.MITID_ERVHERV_ACTION);
		auditLog.setMessage("Reaktiveret i MitID Erhverv med uuid " + person.getNemloginUserUuid());

		log(auditLog, null, null);
	}

	public void suspendedNemLoginUser(Person person) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.MITID_ERVHERV_ACTION);
		auditLog.setMessage("Spærret i MitID Erhverv for uuid " + person.getNemloginUserUuid());

		log(auditLog, null, null);
	}

	private void log(AuditLog auditLog, Person person, Person admin) {
		log(auditLog, person, admin, null);
	}

	private void log(AuditLog auditLog, Person person, Person admin, Domain domain) {
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

		if (domain != null && StringUtils.hasLength(auditLog.getMessage())) {
			auditLog.setMessage(auditLog.getMessage() + " (" + domain.getName() + ")");
		}

		// compress xml
		if (auditLog.getDetails() != null && auditLog.getDetails().getDetailContent() != null && auditLog.getDetails().getDetailType() == DetailType.XML) {
			try {
				byte[] compressedBytes = ZipUtil.compress(auditLog.getDetails().getDetailContent().getBytes(StandardCharsets.UTF_8));
				auditLog.getDetails().setDetailContent(Base64.getEncoder().encodeToString(compressedBytes));
				auditLog.getDetails().setDetailType(DetailType.XML_ZIP);
			} catch (Exception e) {
				log.warn("AudiLogger: Error occured while tying to compress xml log details.", e);
			}
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
	public void cleanupLoginLogs() {
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime tts = now.plusMonths(-3);
	
		auditLogDao.deleteLoginsByTtsBefore(tts);
	}

	@Transactional(rollbackFor = Exception.class)
	public void cleanupLogs() {
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime tts = now.plusMonths(-13);
	
		auditLogDao.deleteByTtsBefore(tts);
	}

	@Transactional(rollbackFor = Exception.class)
	public void cleanupTraceLogs() {
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime tts = now.minusDays(14);

		auditLogDao.deleteTraceLogsByTtsBefore(tts);
	}
	
	@Transactional(rollbackFor = Exception.class)
	public void deleteUnreferencedAuditlogDetails() {
		auditLogDao.deleteUnreferencedAuditlogDetails();
	}
}
