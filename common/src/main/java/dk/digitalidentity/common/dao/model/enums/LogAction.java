package dk.digitalidentity.common.dao.model.enums;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.context.support.ResourceBundleMessageSource;

import dk.digitalidentity.common.dao.model.enums.dto.LogActionDTO;
import lombok.Getter;

@Getter
public enum LogAction {
	// for the small report (the one for 3 months, due to huge amounts of log-data)
	LOGIN("enum.logaction.login", ReportType.LOGIN_HISTORY),
	LOGOUT("enum.logaction.logout", ReportType.LOGIN_HISTORY),
	LOGOUT_IP_CHANGED("enum.logaction.logout.ip", ReportType.LOGIN_HISTORY),
	AUTHN_REQUEST("enum.logaction.authnRequest", ReportType.LOGIN_HISTORY),
	LOGOUT_REQUEST("enum.logaction.logoutRequest", ReportType.LOGIN_HISTORY),
	LOGOUT_RESPONSE("enum.logaction.logoutResponse", ReportType.LOGIN_HISTORY),
	TOO_MANY_ATTEMPTS("enum.logaction.tooManyAttempts", ReportType.LOGIN_HISTORY),
	WRONG_PASSWORD("enum.logaction.wrongPassword", ReportType.LOGIN_HISTORY),
	RIGHT_PASSWORD("enum.logaction.rightPassword", ReportType.LOGIN_HISTORY),
	ACCEPT_MFA("enum.logaction.acceptMFA", ReportType.LOGIN_HISTORY),
	REJECT_MFA("enum.logaction.rejectMFA", ReportType.LOGIN_HISTORY),
	ERROR_SENT_TO_SP("enum.logaction.errorsent", ReportType.LOGIN_HISTORY),
	SESSION_EXPIRED("enum.logaction.sessionExpired", ReportType.LOGIN_HISTORY),
	USED_NEMID("enum.logaction.usedNemID", ReportType.LOGIN_HISTORY),
	USED_NEMLOGIN("enum.logaction.usedNemLogin", ReportType.LOGIN_HISTORY),
	REJECTED_UNKNOWN_PERSON("enum.logaction.rejectedUnknownPerson", ReportType.LOGIN_HISTORY),
	DEACTIVATE_BY_PWD("enum.logaction.deactivateByPwd", ReportType.LOGIN_HISTORY),

	// relevant, but not for reports
	SESSION_KEY_ISSUED("enum.logaction.sessionKeyIssued", ReportType.NONE),
	RADIUS_LOGIN_REQUEST_RECEIVED("enum.logaction.radiusLoginRequestReceived", ReportType.NONE),
	RADIUS_LOGIN_REQUEST_ACCEPTED("enum.logaction.radiusLoginRequestAccepted", ReportType.NONE),
	RADIUS_LOGIN_REQUEST_REJECTED("enum.logaction.radiusLoginRequestRejected", ReportType.NONE),
	UPDATED_USER("enum.logaction.updatedUser", ReportType.NONE),
	CREATED_USER("enum.logaction.createdUser", ReportType.NONE),
	DELETED_USER("enum.logaction.deletedUser", ReportType.NONE),
	REACTIVATE_BY_PWD("enum.logaction.deletedUser", ReportType.NONE), // not used anymore, can be removed from code on 1/6-2023

	// for the large report, the 13 month version
	ACTIVATE("enum.logaction.activate", ReportType.GENERAL_HISTORY),
	ACCEPTED_TERMS("enum.logaction.acceptedTerms", ReportType.GENERAL_HISTORY),
	DEACTIVATE_BY_ADMIN("enum.logaction.deactivateByAdmin", ReportType.GENERAL_HISTORY),
	DEACTIVATE_BY_PERSON("enum.logaction.deactivateByPerson", ReportType.GENERAL_HISTORY),
	REACTIVATE_BY_ADMIN("enum.logaction.reactivateByAdmin", ReportType.GENERAL_HISTORY),
	REACTIVATE_BY_PERSON("enum.logaction.reactivateByPerson", ReportType.GENERAL_HISTORY),
	ADDED_TO_DATASET("enum.logaction.addedToDataset", ReportType.GENERAL_HISTORY),
	REMOVED_FROM_DATASET("enum.logaction.removedFromDataset", ReportType.GENERAL_HISTORY),
	CHANGED_NSIS_ALLOWED("enum.logaction.nsisAllowed", ReportType.GENERAL_HISTORY),
	CHANGE_PASSWORD("enum.logaction.changePassword", ReportType.GENERAL_HISTORY),
	CHANGE_PASSWORD_FAILED("enum.logaction.changePasswordFailed", ReportType.GENERAL_HISTORY),
	ASSOCIATE_MFA("enum.logaction.associateMfa", ReportType.GENERAL_HISTORY),
	ADDED_ROLE_BY_ADMIN("enum.logaction.addedRoleByAdmin", ReportType.GENERAL_HISTORY),
	REMOVED_ROLE_BY_ADMIN("enum.logaction.removedRoleByAdmin", ReportType.GENERAL_HISTORY),
	CHANGE_PASSWORD_SETTINGS("enum.logaction.changePasswordSettings", ReportType.GENERAL_HISTORY),
	CHANGE_SESSION_SETTINGS("enum.logaction.changeSessionSettings", ReportType.GENERAL_HISTORY),
	CHANGE_TERMS_AND_CONDITIONS("enum.logaction.changeTermsAndConditions", ReportType.GENERAL_HISTORY),
	CHANGE_PRIVACY_POLICY("enum.logaction.changePrivacyPolicy", ReportType.GENERAL_HISTORY),
	MANUAL_YUBIKEY_REGISTRATION("enum.logaction.manualYubikey", ReportType.GENERAL_HISTORY),
	REJECTED_BY_CONDITIONS("enum.logaction.conditions.rejected", ReportType.GENERAL_HISTORY),
	CPR_LOOKUP("enum.logaction.cprLookup", ReportType.GENERAL_HISTORY),
	DISABLED_DEAD("enum.logaction.disabledDead", ReportType.GENERAL_HISTORY),
	CHECK_DEAD("enum.logaction.checkDead", ReportType.GENERAL_HISTORY),
	UNLOCK_ACCOUNT("enum.logaction.unlockAccount", ReportType.GENERAL_HISTORY),
	DELETED_MFA_DEVICE("enum.logaction.deletedMFADevice", ReportType.GENERAL_HISTORY),
	UPDATE_FROM_CPR("enum.logaction.updateFromCPR", ReportType.GENERAL_HISTORY);

	private String message;
	private ReportType reportType;

	private LogAction(String message, ReportType reportType) {
		this.message = message;
		this.reportType = reportType;
	}
	
	public static List<LogActionDTO> getSorted(ResourceBundleMessageSource resourceBundle, Locale locale) {
		List<LogActionDTO> dtos = new ArrayList<>();

		for (LogAction logAction : LogAction.values()) {
			String newMessage = resourceBundle.getMessage(logAction.getMessage(), null, locale);
			LogActionDTO dto = new LogActionDTO();
			dto.setLogAction(logAction);
			dto.setMessage(newMessage);
			
			dtos.add(dto);
		}
		
		dtos.sort((a, b) -> a.getMessage().compareToIgnoreCase(b.getMessage()));

		return dtos;
	}
	
	public enum ReportType { NONE, GENERAL_HISTORY, LOGIN_HISTORY }
}
