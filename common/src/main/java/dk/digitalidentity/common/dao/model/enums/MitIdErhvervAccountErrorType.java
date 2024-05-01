package dk.digitalidentity.common.dao.model.enums;

import lombok.Getter;

@Getter
public enum MitIdErhvervAccountErrorType {
	ACCOUNT_DELETED_IN_MITID_ERHVERV("enum.mitiderror.deleted"),
	ACCOUNT_DISABLED_IN_MITID_ERHVERV("enum.mitiderror.disabled"),
	UNASSOCIATED_ACCOUNT_IN_MITID_ERHVERV("enum.mitiderror.unassociated");
	
	private String message;
	
	private MitIdErhvervAccountErrorType(String message) {
		this.message = message;
	}
}
