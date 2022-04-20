package dk.digitalidentity.common.dao.model;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Table;

import dk.digitalidentity.common.dao.model.enums.DetailType;
import dk.digitalidentity.common.dao.model.enums.LogAction;
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
	private String logTargetId;
	
	@Column
	private String logTargetName;
	
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
