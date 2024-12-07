package dk.digitalidentity.common.dao.model;

import java.time.LocalDateTime;

import dk.digitalidentity.common.dao.model.enums.LoginAlarmType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "login_alarms")
@Setter
@Getter
public class LoginAlarm {

	@Id
	@Column
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;
	
	@Column
	private String country;
	
	@Column
	private String ipAddress;
	
	@Column
	@Enumerated(EnumType.STRING)
	private LoginAlarmType alarmType;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "person_id")
	private Person person;
	
	@Column
	private LocalDateTime tts;
}
