package dk.digitalidentity.common.dao.model;

import java.time.LocalDateTime;

import dk.digitalidentity.common.dao.model.enums.NemloginAction;
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
