package dk.digitalidentity.datatables.model;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

import lombok.Getter;
import lombok.Setter;

@Entity(name = "view_audit_log")
@Getter
@Setter
public class AuditLogView {
	
	@Id
	@Column
	private long id;
	
	@Column
	private LocalDateTime tts;

	@Column
	private long personId;

	@Column
	private String cpr;

	@Column
	private String userId;

	@Column
	private String samaccountName;
	
	@Column
	private String personName;

	@Column
	private String personDomain;

	@Column
	private String message;
}
