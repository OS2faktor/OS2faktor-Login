package dk.digitalidentity.common.dao.model;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import dk.digitalidentity.common.dao.model.enums.LoginAlarmType;
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
