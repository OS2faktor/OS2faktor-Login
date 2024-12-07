package dk.digitalidentity.common.dao.model;

import dk.digitalidentity.common.dao.model.enums.LogWatchSettingKey;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

@Entity(name = "log_watch_settings")
@Getter
@Setter
public class logWatchSetting {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "settingKey")
	private LogWatchSettingKey key;
	
	@Column(name = "settingValue")
	private String value;
}
