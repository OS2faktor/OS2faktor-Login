package dk.digitalidentity.common.service.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class AuditLogLocationDto {
	private long personId;
	private String location;
}
