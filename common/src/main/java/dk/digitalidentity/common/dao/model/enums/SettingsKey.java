package dk.digitalidentity.common.dao.model.enums;

import lombok.Getter;

@Getter
public enum SettingsKey {
	TRACE_LOGGING("false"),
	CERTIFICATE_ROLLOVER_TTS("2099-12-31T23:59");
	
	private String defaultValue;
	
	private SettingsKey(String defaultValue) {
		this.defaultValue = defaultValue;
	}
}
