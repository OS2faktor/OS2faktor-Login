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

	// these used to be part of the above report, but only the login are actually interesting
	LOGIN_SELFSERVICE("enum.logaction.loginSelfService", ReportType.NONE),
	LOGOUT("enum.logaction.logout", ReportType.NONE),
	LOGOUT_IP_CHANGED("enum.logaction.logout.ip", ReportType.NONE),
	AUTHN_REQUEST("enum.logaction.authnRequest", ReportType.NONE),
	OIDC_JWT_ID_TOKEN("enum.logaction.jwt.idtoken", ReportType.NONE),
	LOGOUT_REQUEST("enum.logaction.logoutRequest", ReportType.NONE),
	LOGOUT_RESPONSE("enum.logaction.logoutResponse", ReportType.NONE),
	TOO_MANY_ATTEMPTS("enum.logaction.tooManyAttempts", ReportType.NONE),
	WRONG_PASSWORD("enum.logaction.wrongPassword", ReportType.NONE),
	RIGHT_PASSWORD("enum.logaction.rightPassword", ReportType.NONE),
	ACCEPT_MFA("enum.logaction.acceptMFA", ReportType.NONE),
	REJECT_MFA("enum.logaction.rejectMFA", ReportType.NONE),
	ERROR_SENT_TO_SP("enum.logaction.errorsent", ReportType.NONE),
	SESSION_EXPIRED("enum.logaction.sessionExpired", ReportType.NONE),
	USED_NEMID("enum.logaction.usedNemID", ReportType.NONE),
	USED_NEMLOGIN("enum.logaction.usedNemLogin", ReportType.NONE),
	REJECTED_UNKNOWN_PERSON("enum.logaction.rejectedUnknownPerson", ReportType.NONE),
	DEACTIVATE_BY_PWD("enum.logaction.deactivateByPwd", ReportType.NONE),

	// relevant, but not for reports
	RADIUS_LOGIN_REQUEST_RECEIVED("enum.logaction.radiusLoginRequestReceived", ReportType.NONE),
	RADIUS_LOGIN_REQUEST_ACCEPTED("enum.logaction.radiusLoginRequestAccepted", ReportType.NONE),
	RADIUS_LOGIN_REQUEST_REJECTED("enum.logaction.radiusLoginRequestRejected", ReportType.NONE),
	DELETED_USER("enum.logaction.deletedUser", ReportType.NONE),
	REACTIVATE_BY_PWD("enum.logaction.deletedUser", ReportType.NONE), // not used anymore, can be removed from code on 1/6-2023

	// for the large report, the 13 month version
	ACTIVATE("enum.logaction.activate", ReportType.GENERAL_HISTORY),
	ACCEPTED_TERMS("enum.logaction.acceptedTerms", ReportType.GENERAL_HISTORY),
	DEACTIVATE_BY_ADMIN("enum.logaction.deactivateByAdmin", ReportType.GENERAL_HISTORY, ReportType.ADMIN_ACTION),
	DEACTIVATE_BY_PERSON("enum.logaction.deactivateByPerson", ReportType.GENERAL_HISTORY),
	REACTIVATE_BY_ADMIN("enum.logaction.reactivateByAdmin", ReportType.GENERAL_HISTORY, ReportType.ADMIN_ACTION),
	REACTIVATE_BY_PERSON("enum.logaction.reactivateByPerson", ReportType.GENERAL_HISTORY),
	RANDOM_PASSWORD_SET_BY_ADMIN("enum.logaction.randomPasswordSetByAdmin", ReportType.GENERAL_HISTORY, ReportType.ADMIN_ACTION),
	ADDED_TO_DATASET("enum.logaction.addedToDataset", ReportType.GENERAL_HISTORY),
	REMOVED_FROM_DATASET("enum.logaction.removedFromDataset", ReportType.GENERAL_HISTORY),
	DELETED_FROM_DATASET("enum.logaction.deletedFromDataset", ReportType.GENERAL_HISTORY),
	CHANGED_NSIS_ALLOWED("enum.logaction.nsisAllowed", ReportType.GENERAL_HISTORY),
	CHANGED_TRANSFER_TO_NEMLOGIN("enum.logaction.transferToNemlogin", ReportType.GENERAL_HISTORY),
	CHANGED_ALLOW_PRIVATE_MITID("enum.logaction.allowPrivateMitID", ReportType.GENERAL_HISTORY),
	CHANGED_ALLOW_QUALIFIED_SIGNATURE("enum.logaction.allowQualifiedSignature", ReportType.GENERAL_HISTORY),
	CHANGE_PASSWORD("enum.logaction.changePassword", ReportType.GENERAL_HISTORY),
	CHANGE_PASSWORD_FAILED("enum.logaction.changePasswordFailed", ReportType.GENERAL_HISTORY),
	PASSWORD_FILTER_VALIDATION_FAILED("enum.logaction.passwordFilterValidationFailed", ReportType.GENERAL_HISTORY),
	ASSOCIATE_MFA("enum.logaction.associateMfa", ReportType.GENERAL_HISTORY),
	ADDED_ROLE_BY_ADMIN("enum.logaction.addedRoleByAdmin", ReportType.GENERAL_HISTORY, ReportType.ADMIN_ACTION),
	REMOVED_ROLE_BY_ADMIN("enum.logaction.removedRoleByAdmin", ReportType.GENERAL_HISTORY, ReportType.ADMIN_ACTION),
	CHANGE_PASSWORD_SETTINGS("enum.logaction.changePasswordSettings", ReportType.GENERAL_HISTORY, ReportType.ADMIN_ACTION),
	CHANGE_SESSION_SETTINGS("enum.logaction.changeSessionSettings", ReportType.GENERAL_HISTORY, ReportType.ADMIN_ACTION),
	CHANGE_KOMBIT_MFA("enum.logaction.changeKombitMfa", ReportType.GENERAL_HISTORY, ReportType.ADMIN_ACTION),
	CHANGE_TERMS_AND_CONDITIONS("enum.logaction.changeTermsAndConditions", ReportType.GENERAL_HISTORY, ReportType.ADMIN_ACTION),
	CHANGE_TU_TERMS_AND_CONDITIONS("enum.logaction.changeTUTermsAndConditions", ReportType.GENERAL_HISTORY, ReportType.ADMIN_ACTION),
	CHANGE_PRIVACY_POLICY("enum.logaction.changePrivacyPolicy", ReportType.GENERAL_HISTORY, ReportType.ADMIN_ACTION),
	MANUAL_YUBIKEY_REGISTRATION("enum.logaction.manualYubikey", ReportType.GENERAL_HISTORY),
	REJECTED_BY_CONDITIONS("enum.logaction.conditions.rejected", ReportType.GENERAL_HISTORY),
	CPR_LOOKUP("enum.logaction.cprLookup", ReportType.GENERAL_HISTORY),
	DISABLED_DEAD("enum.logaction.disabledCivilState", ReportType.GENERAL_HISTORY),
	DISABLED_DISENFRANCHISED("enum.logaction.disabledDisenfranchised", ReportType.GENERAL_HISTORY),
	CHECK_DEAD("enum.logaction.checkCivilState", ReportType.GENERAL_HISTORY),
	UNLOCK_ACCOUNT("enum.logaction.unlockAccount", ReportType.GENERAL_HISTORY),
	DELETED_MFA_DEVICE("enum.logaction.deletedMFADevice", ReportType.GENERAL_HISTORY),
	SESSION_NOT_ISSUED_IP_CHANGED("enum.logaction.session.ip.changed", ReportType.GENERAL_HISTORY),
	UPDATE_FROM_CPR("enum.logaction.updateFromCPR", ReportType.GENERAL_HISTORY),
	KODEVISER_RESET("enum.logaction.kodeviserReset", ReportType.GENERAL_HISTORY, ReportType.ADMIN_ACTION),
	SENT_EBOKS("enum.logaction.sentEboks", ReportType.GENERAL_HISTORY),
	SENT_MAIL("enum.logaction.sentEmail", ReportType.GENERAL_HISTORY),
	TRACE_LOG("enum.logaction.tracelogging", ReportType.NONE),
	MITID_ERVHERV_ACTION("enum.logaction.mitiderhverv.action", ReportType.GENERAL_HISTORY),
	MUST_CHANGE_PASSWORD("enum.logaction.must.change.password", ReportType.GENERAL_HISTORY),

	// can be removed at some future point in time, when we know that NONE are present in the DB
	SESSION_KEY_ISSUED("enum.logaction.sessionKeyIssued", false, ReportType.NONE),
	SESSION_KEY_EXCHANGED("enum.logaction.sessionKeyExchanged", false, ReportType.NONE),
	OIDC_AUTHORIZATION_CODE_REQUEST_RESPONSE("enum.logaction.authorizationCode.response", ReportType.LOGIN_HISTORY),
	OIDC_AUTHORIZATION_CODE_REQUEST("enum.logaction.authorizationCode", ReportType.LOGIN_HISTORY);

	private String message;
	private ReportType[] reportTypes;
	private boolean includeInList = true;

	private LogAction(String message, ReportType... reportTypes) {
		this.message = message;
		this.reportTypes = reportTypes;
	}

	private LogAction(String message, boolean includeInList, ReportType... reportTypes) {
		this.message = message;
		this.reportTypes = reportTypes;
		this.includeInList = includeInList;
	}
	
	public static List<LogActionDTO> getSorted(ResourceBundleMessageSource resourceBundle, Locale locale) {
		List<LogActionDTO> dtos = new ArrayList<>();

		for (LogAction logAction : LogAction.values()) {
			if (logAction.isIncludeInList()) {
				String newMessage = resourceBundle.getMessage(logAction.getMessage(), null, locale);
				LogActionDTO dto = new LogActionDTO();
				dto.setLogAction(logAction);
				dto.setMessage(newMessage);

				dtos.add(dto);
			}
		}
		
		dtos.sort((a, b) -> a.getMessage().compareToIgnoreCase(b.getMessage()));

		return dtos;
	}
	
	public enum ReportType { ALL, NONE, GENERAL_HISTORY, LOGIN_HISTORY, ADMIN_ACTION }
}
