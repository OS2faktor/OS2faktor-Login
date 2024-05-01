package dk.digitalidentity.common.service.dto;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class AuditLogFailedLoginDTO {
	private long attempts;
	private long personId;
	private String ipAddress;
}
