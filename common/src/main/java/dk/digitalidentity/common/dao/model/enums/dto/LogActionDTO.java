package dk.digitalidentity.common.dao.model.enums.dto;

import dk.digitalidentity.common.dao.model.enums.LogAction;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LogActionDTO {
	private LogAction logAction;
	private String message;
}
