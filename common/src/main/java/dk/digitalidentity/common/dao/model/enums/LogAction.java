package dk.digitalidentity.common.dao.model.enums;

import lombok.Getter;

@Getter
public enum LogAction {
	LOGIN("enum.logaction.login", 30 * 6),
	ACTIVATE("enum.logaction.activate", -1),
	DEACTIVATE_BY_PWD("enum.logaction.deactivateByPwd", -1),
	DEACTIVATE_BY_ADMIN("enum.logaction.deactivateByAdmin", -1),
	DEACTIVATE_BY_PERSON("enum.logaction.deactivateByPerson", -1),
	REACTIVATE_BY_PWD("enum.logaction.reactivateByPwd", -1),
	REACTIVATE_BY_ADMIN("enum.logaction.reactivateByAdmin", -1),
	REACTIVATE_BY_PERSON("enum.logaction.reactivateByPerson", -1),
	CHANGE_PASSWORD("enum.logaction.changePassword", 365 * 2),
	WRONG_PASSWORD("enum.logaction.wrongPassword", 30 * 6),
	ADDED_TO_DATASET("enum.logaction.addedToDataset", -1),
	REMOVED_FROM_DATASET("enum.logaction.removedFromDataset", -1),
	ADDED_ROLE_BY_ADMIN("enum.logaction.addedRoleByAdmin", -1),
	REMOVED_ROLE_BY_ADMIN("enum.logaction.removedRoleByAdmin", -1),
	CHANGE_PASSWORD_SETTINGS("enum.logaction.changePasswordSettings", -1),
	CHANGE_TERMS_AND_CONDITIONS("enum.logaction.changeTermsAndConditions", -1),
	UPDATED_USER("enum.logaction.updatedUser", -1),
	CREATED_USER("enum.logaction.createdUser", -1),
	DELETED_USER("enum.logaction.deletedUser", -1);
	
	private long storageTime;
	private String message;

	private LogAction(String message, long storageTime) {
		this.storageTime = storageTime;
		this.message = message;
	}
}
