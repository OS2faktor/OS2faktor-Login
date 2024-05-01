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

import dk.digitalidentity.common.dao.model.enums.NemloginAction;
import lombok.Getter;
import lombok.Setter;

@Entity(name = "nemlogin_queue")
@Getter
@Setter
public class NemloginQueue {

	public NemloginQueue() {}
	
	public NemloginQueue(Person person, NemloginAction action) {
		if (NemloginAction.DELETE.equals(action)) {
			this.nemloginUserUuid = person.getNemloginUserUuid();
		}
		else {
			this.person = person;
		}

		this.action = action;
		this.tts = LocalDateTime.now();
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;
	
	@Column
	private String nemloginUserUuid;
	
	@Enumerated(EnumType.STRING)
	@Column
	private NemloginAction action;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "person_id")
	private Person person;
	
	@Column
	private boolean failed;
	
	@Column
	private String failureReason;
	
	@Column
	private LocalDateTime tts;

}
