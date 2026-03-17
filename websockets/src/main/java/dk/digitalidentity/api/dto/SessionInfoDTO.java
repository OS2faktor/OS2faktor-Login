package dk.digitalidentity.api.dto;

import dk.digitalidentity.service.Session;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SessionInfoDTO {
	private long id;
	private String serverName;

	public SessionInfoDTO(Session session) {
		this.id = session.getId();
		this.serverName = session.getServerName();
	}
}
