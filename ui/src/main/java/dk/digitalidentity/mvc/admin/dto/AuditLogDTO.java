package dk.digitalidentity.mvc.admin.dto;

import java.time.LocalDateTime;

import org.springframework.util.StringUtils;

import dk.digitalidentity.common.dao.model.AuditLog;
import dk.digitalidentity.common.dao.model.enums.DetailType;
import dk.digitalidentity.common.dao.model.enums.LogAction;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuditLogDTO {
	private LocalDateTime tts;
	private String ipAddress;
	private String correlationId;
	private String personName;
	private String personDomain;
	private String cpr;
	private String performerName;
	private LogAction logAction;
	private String logTargetName;
	private String message;
	private DetailType detailType;
	private String detailContent;
	private String detailSupplement;

	public AuditLogDTO(AuditLog auditLog) {
		this.cpr = !StringUtils.hasLength(auditLog.getCpr()) ? "" : auditLog.getCpr().substring(0, 6) + "-XXXX";
		this.detailContent = auditLog.getDetails() != null ? auditLog.getDetails().getDetailContent() : null;
		this.detailSupplement = auditLog.getDetails() != null ? auditLog.getDetails().getDetailSupplement() : null;
		this.detailType = auditLog.getDetails() != null ? auditLog.getDetails().getDetailType() : DetailType.TEXT;
		this.ipAddress = auditLog.getIpAddress();
		this.correlationId = auditLog.getCorrelationId();
		this.logAction = auditLog.getLogAction();
		this.logTargetName = auditLog.getLogTargetName();
		this.message = auditLog.getMessage();
		this.performerName = auditLog.getPerformerName();
		this.personName = auditLog.getPersonName();
		this.personDomain = auditLog.getPersonDomain();
		this.tts = auditLog.getTts();
	}
}
