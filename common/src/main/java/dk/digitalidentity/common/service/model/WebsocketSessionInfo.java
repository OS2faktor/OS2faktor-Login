package dk.digitalidentity.common.service.model;

import lombok.Getter;
import lombok.Setter;

/**
 * Matches SessionInfoDTO in websockets project
 */
@Getter
@Setter
public class WebsocketSessionInfo {
	private long id;
	private String serverName;
}
