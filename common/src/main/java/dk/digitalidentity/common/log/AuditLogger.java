package dk.digitalidentity.common.log;

import dk.digitalidentity.common.dao.model.SessionSetting;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import dk.digitalidentity.common.config.Constants;
import dk.digitalidentity.common.dao.AuditLogDao;
import dk.digitalidentity.common.dao.model.AuditLog;
import dk.digitalidentity.common.dao.model.AuditLogDetail;
import dk.digitalidentity.common.dao.model.PasswordSetting;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.TermsAndConditions;
import dk.digitalidentity.common.dao.model.enums.DetailType;
import dk.digitalidentity.common.dao.model.enums.LogAction;
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

		log(auditLog, person, admin);
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

	public void login(Person person, String loginTo, String assertion) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.LOGIN);
		auditLog.setMessage("Login til " + loginTo);
		auditLog.setDetails(new AuditLogDetail());
		auditLog.getDetails().setDetailType(DetailType.XML);
		auditLog.getDetails().setDetailContent(assertion);

		log(auditLog, person, null);
	}

	public void badPassword(Person person) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.WRONG_PASSWORD);
		auditLog.setMessage("Forkert kodeord indtastet");

		log(auditLog, person, null);
	}

	public void changePasswordByPerson(Person person) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.CHANGE_PASSWORD);
		auditLog.setMessage("Kodeord skiftet");

		log(auditLog, person, null);
	}

	public void activatedByPerson(Person person, String nemIDPid) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.ACTIVATE);
		auditLog.setMessage("Brugerkontoen er blevet aktiveret af brugeren selv");

		// Add nemid pid to audit log
		AuditLogDetail detail = new AuditLogDetail();
		detail.setDetailType(DetailType.TEXT);
		detail.setDetailContent("NemIDPid: " + nemIDPid);
		auditLog.setDetails(detail);

		log(auditLog, person, null);
	}

	public void manualActivation(Object activationDetails, Person person, Person performedBy, boolean userHasSeenCredentials) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.ACTIVATE);
		auditLog.setMessage("Erhvervsidentitet aktiveret af administrator");

		auditLog.setDetails(new AuditLogDetail());
		auditLog.getDetails().setDetailType(DetailType.JSON);

		try {
			ObjectMapper mapper = new ObjectMapper();
			JsonNode jsonNode = mapper.readTree(mapper.writeValueAsString(activationDetails));
			((ObjectNode) jsonNode).put("adminSeenCredentials", userHasSeenCredentials);

			String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
			auditLog.getDetails().setDetailContent(json);
		}
		catch (JsonProcessingException ex) {
			log.error("Could not serialize ActivationDTO", ex);
		}

		log(auditLog, person, performedBy);
	}
	
	public void manualMfaAssociation(Object activationDetails, Person person, Person performedBy) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.ASSOCIATE_MFA);
		auditLog.setMessage("MFA klient tilknyttet af administrator");

		auditLog.setDetails(new AuditLogDetail());
		auditLog.getDetails().setDetailType(DetailType.JSON);

		try {
			ObjectMapper mapper = new ObjectMapper();
			JsonNode jsonNode = mapper.readTree(mapper.writeValueAsString(activationDetails));

			String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
			auditLog.getDetails().setDetailContent(json);
		}
		catch (JsonProcessingException ex) {
			log.error("Could not serialize ActivationDTO", ex);
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

	public void deactivateByAdmin(Person person, Person admin) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.DEACTIVATE_BY_ADMIN);
		auditLog.setMessage("Brugeren er blevet spærret af en administrator");

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
			log.error("Could not serialize PasswordSettings");
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
		auditLog.setMessage("Vilkår ændret");

		auditLog.setDetails(new AuditLogDetail());
		auditLog.getDetails().setDetailType(DetailType.TEXT);
		auditLog.getDetails().setDetailContent(termsAndConditions.getContent());

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

	private void log(AuditLog auditLog, Person person, Person admin) {
		auditLog.setCorrelationId(getCorrelationId());
		auditLog.setIpAddress(getIpAddress());
		auditLog.setPerson(person);
		auditLog.setPersonName(person.getName());
		auditLog.setPersonDomain(person.getDomain().getName());
		auditLog.setCpr(person.getCpr());

		if (admin != null) {
			auditLog.setPerformerId(admin.getId());
			auditLog.setPerformerName(admin.getName());
		}

		auditLogDao.save(auditLog);
	}

	private String getCorrelationId() {
		try {
			return RequestContextHolder.currentRequestAttributes().getSessionId();
		}
		catch (IllegalStateException ex) {
			// TODO: not super good
			return "SYSTEM-" + Long.toString(Thread.currentThread().getId());
		}
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
		List<AuditLog> all = auditLogDao.findAll();

		for (AuditLog auditLog : all) {
			long storageTime = auditLog.getLogAction().getStorageTime();

			if (storageTime == -1) {
				continue;
			}

			LocalDateTime tts = auditLog.getTts();

			LocalDateTime now = LocalDateTime.now();
			if (tts.plusDays(storageTime).isBefore(now)) {
				auditLogDao.delete(auditLog);
			}
		}
	}
}
