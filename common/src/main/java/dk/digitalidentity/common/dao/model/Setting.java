package dk.digitalidentity.common.dao.model;

import dk.digitalidentity.common.dao.model.enums.SettingKey;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

@Entity(name = "settings")
@Getter
@Setter
public class Setting {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "setting_key")
	private SettingKey key;
	
	@Column(name = "setting_value")
	private String value;
}
