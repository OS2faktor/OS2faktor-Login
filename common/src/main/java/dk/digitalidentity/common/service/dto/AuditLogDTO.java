package dk.digitalidentity.common.service.dto;

import dk.digitalidentity.common.dao.model.enums.LogAction;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AuditLogDTO {
	private String tts;
	private String ipAddress;
	private String correlationId;
	private LogAction logAction;
	private String message;
	private String cpr;
	private String personName;
	private String performerName;
}
