package dk.digitalidentity.common.dao.model;

import java.time.LocalDateTime;

import dk.digitalidentity.common.dao.model.enums.DetailType;
import dk.digitalidentity.common.dao.model.enums.LogAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "view_audit_log_api")
@Setter
@Getter
public class AuditLogApiView {

	@Id
	@Column
	private long id;
	
	@Column
	private LocalDateTime tts;
		
	@Column
	private String ipAddress;
	
	@Column
	private String correlationId;

	@Column
	private Long personId;
	
	@Column
	private String personName;
	
	@Column
	private String cpr;
	
	@Column
	private Long performerId;
	
	@Column
	private String performerName;
	
	@Column
	@Enumerated(EnumType.STRING)
	private LogAction logAction;

	@Column
	private String message;

	@Column
	private String personDomain;

	@Column
	private String samaccountName;
	
	@Enumerated(EnumType.STRING)
	@Column
	private DetailType detailType;
	
	@Column
	private String detailContent;
	
	@Column
	private String detailSupplement;

}
