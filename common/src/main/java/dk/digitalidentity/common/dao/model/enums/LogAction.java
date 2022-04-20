package dk.digitalidentity.common.dao.model.enums;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.context.support.ResourceBundleMessageSource;

import dk.digitalidentity.common.dao.model.enums.dto.LogActionDTO;
import lombok.Getter;

@Getter
public enum LogAction {
	LOGIN("enum.logaction.login"),
	LOGOUT("enum.logaction.logout"),
	LOGOUT_IP_CHANGED("enum.logaction.logout.ip"),
	ACTIVATE("enum.logaction.activate"),
	AUTHN_REQUEST("enum.logaction.authnRequest"),
	LOGOUT_REQUEST("enum.logaction.logoutRequest"),
	LOGOUT_RESPONSE("enum.logaction.logoutResponse"),
	ACCEPTED_TERMS("enum.logaction.acceptedTerms"),
	DEACTIVATE_BY_PWD("enum.logaction.deactivateByPwd"),
	DEACTIVATE_BY_ADMIN("enum.logaction.deactivateByAdmin"),
	DEACTIVATE_BY_PERSON("enum.logaction.deactivateByPerson"),
	REACTIVATE_BY_PWD("enum.logaction.reactivateByPwd"),
	REACTIVATE_BY_ADMIN("enum.logaction.reactivateByAdmin"),
	REACTIVATE_BY_PERSON("enum.logaction.reactivateByPerson"),
	CHANGE_PASSWORD("enum.logaction.changePassword"),
	CHANGE_PASSWORD_FAILED("enum.logaction.changePasswordFailed"),
	TOO_MANY_ATTEMPTS("enum.logaction.tooManyAttempts"),
	WRONG_PASSWORD("enum.logaction.wrongPassword"),
	RIGHT_PASSWORD("enum.logaction.rightPassword"),
	ACCEPT_MFA("enum.logaction.acceptMFA"),
	REJECT_MFA("enum.logaction.rejectMFA"),
	ADDED_TO_DATASET("enum.logaction.addedToDataset"),
	REMOVED_FROM_DATASET("enum.logaction.removedFromDataset"),
	CHANGED_NSIS_ALLOWED("enum.logaction.nsisAllowed"),
	ADDED_ROLE_BY_ADMIN("enum.logaction.addedRoleByAdmin"),
	REMOVED_ROLE_BY_ADMIN("enum.logaction.removedRoleByAdmin"),
	CHANGE_PASSWORD_SETTINGS("enum.logaction.changePasswordSettings"),
	CHANGE_SESSION_SETTINGS("enum.logaction.changeSessionSettings"),
	CHANGE_TERMS_AND_CONDITIONS("enum.logaction.changeTermsAndConditions"),
	CHANGE_PRIVACY_POLICY("enum.logaction.changePrivacyPolicy"),
	UPDATED_USER("enum.logaction.updatedUser"),
	CREATED_USER("enum.logaction.createdUser"),
	DELETED_USER("enum.logaction.deletedUser"),
	ERROR_SENT_TO_SP("enum.logaction.errorsent"),
	ASSOCIATE_MFA("enum.logaction.associateMfa"),
	SESSION_KEY_ISSUED("enum.logaction.sessionKeyIssued"),
	MANUAL_YUBIKEY_REGISTRATION("enum.logaction.manualYubikey"),
	RADIUS_LOGIN_REQUEST_RECEIVED("enum.logaction.radiusLoginRequestReceived"),
	RADIUS_LOGIN_REQUEST_ACCEPTED("enum.logaction.radiusLoginRequestAccepted"),
	RADIUS_LOGIN_REQUEST_REJECTED("enum.logaction.radiusLoginRequestRejected"),
	REJECTED_BY_CONDITIONS("enum.logaction.conditions.rejected"),
	CPR_LOOKUP("enum.logaction.cprLookup"),
	USED_NEMID("enum.logaction.usedNemID"),
	USED_NEMLOGIN("enum.logaction.usedNemLogin"),
	DISABLED_DEAD("enum.logaction.disabledDead"),
	CHECK_DEAD("enum.logaction.checkDead"),
	UNLOCK_ACCOUNT("enum.logaction.unlockAccount"),
	DELETED_MFA_DEVICE("enum.logaction.deletedMFADevice"),
	UPDATE_FROM_CPR("enum.logaction.updateFromCPR"),
	SESSION_EXPIRED("enum.logaction.sessionExpired"),
	REJECTED_UNKNOWN_PERSON("enum.logaction.rejectedUnknownPerson");

	private String message;

	private LogAction(String message) {
		this.message = message;
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
}
