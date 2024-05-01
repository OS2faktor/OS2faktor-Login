package dk.digitalidentity.mvc.admin.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NemLoginQueueRetryDTO {
	private long queueId;
	private String ridAction;
	private String rid;	
}
