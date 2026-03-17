package dk.digitalidentity.common.service.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WebsocketConnectionStatus {
	private int currentConnections;
	private int maxConnections;

	@JsonIgnore
	private List<WebsocketServerStatus> sessions;

	@JsonIgnore
	private Map<String, WebsocketServerStatus> serverMap = new HashMap<>();
}
