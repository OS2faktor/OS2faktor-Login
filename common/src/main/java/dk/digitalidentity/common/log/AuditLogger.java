package dk.digitalidentity.common.log;

import java.time.LocalDateTime;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
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
import dk.digitalidentity.common.dao.model.TermsAndConditions;
import dk.digitalidentity.common.dao.model.enums.DetailType;
import dk.digitalidentity.common.dao.model.enums.LogAction;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class AuditLogger {

	@Autowired
	private AuditLogDao auditLogDao;
	
	public void toggleRoleByAdmin(Person person, Person admin, String role, boolean enabled) {
		AuditLog auditLog = new AuditLog();
		String roleMsg = Constants.ROLE_ADMIN.equals(role) ? "administrator" : "supporter";

		if (enabled) {
			auditLog.setLogAction(LogAction.ADDED_ROLE_BY_ADMIN);
			auditLog.setMessage("Tildelt " + roleMsg + " rollen af en administrator");
		}
		else {
			auditLog.setLogAction(LogAction.REMOVED_ROLE_BY_ADMIN);
			auditLog.setMessage("Frataget " + roleMsg + " rollen af en administrator");			
		}

		log(auditLog, person, admin);
	}
	
	public void removedFromDataset(Person person) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.REMOVED_FROM_DATASET);
		auditLog.setMessage("Erhvervsidentiteten er blevet spærret af kommunen");

		log(auditLog, person, null);
	}
	
	public void addedToDataset(Person person) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.ADDED_TO_DATASET);
		auditLog.setMessage("Erhvervsidentiteten er klar til udstedelse");

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

	public void activatedByPerson(Person person) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.ACTIVATE);
		auditLog.setMessage("Erhvervsidentiteten er blevet aktiveret af brugeren selv");
		
		log(auditLog, person, null);
	}

	public void deactivateByPerson(Person person) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.DEACTIVATE_BY_PERSON);
		auditLog.setMessage("Erhvervsidentiteten er blevet spærret af brugeren selv");

		log(auditLog, person, null);
	}

	public void reactivateByPerson(Person person) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.REACTIVATE_BY_PERSON);
		auditLog.setMessage("Erhvervsidentiteten er blevet re-aktiveret af brugeren selv");

		log(auditLog, person, null);
	}

	public void deactivateByAdmin(Person person, Person admin) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.DEACTIVATE_BY_ADMIN);
		auditLog.setMessage("Erhvervsidentiteten er blevet spærret af en administrator");

		log(auditLog, person, admin);
	}

	public void reactivateByAdmin(Person person, Person admin) {
		AuditLog auditLog = new AuditLog();
		auditLog.setLogAction(LogAction.REACTIVATE_BY_ADMIN);
		auditLog.setMessage("Spærringen af erhvervsidentiteten er blevet hævet af en administrator");

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
