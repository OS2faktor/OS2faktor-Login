package dk.digitalidentity.common.dao.model.enums;

import lombok.Getter;

@Getter
public enum SettingKey {
	TRACE_LOGGING("false"),
	FULL_SERVICE_IDP_MIGRATED("false"),
	MIGRATED_PRIVATE_MITID("false"),
	KOMBIT_DEFAULT_MFA("DEPENDS"),
	CERTIFICATE_ROLLOVER_TTS("2099-12-31T23:59");
	
	private String defaultValue;
	
	private SettingKey(String defaultValue) {
		this.defaultValue = defaultValue;
	}
}
