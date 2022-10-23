package dk.digitalidentity.common.dao.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

import dk.digitalidentity.common.dao.model.enums.LogWatchSettingKey;
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
