package dk.digitalidentity.common.dao.model;

import dk.digitalidentity.common.dao.model.enums.SettingsKey;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

@Entity(name = "settings")
@Getter
@Setter
public class Setting {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "setting_key")
	private SettingsKey key;
	
	@Column(name = "setting_value")
	private String value;
}
