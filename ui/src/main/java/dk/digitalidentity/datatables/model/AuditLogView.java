package dk.digitalidentity.datatables.model;

import java.time.LocalDateTime;

import dk.digitalidentity.common.dao.model.enums.LogAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
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
	private Long personId;

	@Column
	private String cpr;

	@Column
	private String userId;
	
	@Column
	private String personName;

	@Column
	private String personDomain;

	@Column
	private String message;
	
	@Column
	@Enumerated(EnumType.STRING)
	private LogAction logAction;
}
