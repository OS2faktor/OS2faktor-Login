package dk.digitalidentity.common.dao.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

import com.fasterxml.jackson.annotation.JsonIgnore;

import dk.digitalidentity.common.dao.model.enums.LogAction;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "auditlogs")
@Setter
@Getter
public class AuditLog {

	@Id
	@Column
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;
	
	@CreationTimestamp
	@Column(updatable = false)
	private LocalDateTime tts;
		
	@Column(updatable = false)
	private String ipAddress;
	
	@Column
	private String location;
	
	@Column(updatable = false)
	private String correlationId;

	// referenced person which data was used

	// has to be EAGER because of IGNORE below
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "person_id")
	@JsonIgnore
	@NotFound(action = NotFoundAction.IGNORE)
	private Person person;

	// TODO: this was added in 2026r2 (juli 2026) - after 13 months, so roughly august 2027, we should
	//       expect most logs to be updated to contain personUserId, so what we CAN do, is remove the
	//       person-reference in the code, and just keep the person_id field, which we will set manually,
	//       and then update all the code to use getPersonUserId() where it would normally reference this
	//       from the getPerson() entity - this reduces SQL overhead, and will resolve the issue where
	//       a person is NULL'ed (allowing us to keep the userId in the table)
	//
	//       after updating, we need to also resolve the VIEW/Datatable stuff that shows this information in the UI
	@Column(updatable = false)
	private String personUserId;

	@Column(updatable = false)
	private String personName;

	@Column(updatable = false)
	private String personDomain;
	
	@Column(updatable = false)
	private String cpr;
	
	// referenced entity that performed the action (null if the performer was the data-owner)

	@Column(updatable = false)
	private Long performerId;
	
	@Column(updatable = false)
	private String performerName;
	
	// action performed

	@Column(updatable = false)
	@Enumerated(EnumType.STRING)
	private LogAction logAction;

	@Column(updatable = false)
	private String message;
	
	@OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
	@JoinColumn(name = "auditlogs_details_id")
	@JsonIgnore
	private AuditLogDetail details;
}
