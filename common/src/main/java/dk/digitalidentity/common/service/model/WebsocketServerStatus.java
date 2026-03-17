package dk.digitalidentity.common.service.model;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WebsocketServerStatus {
	private String serverName;
	private boolean up;
	private LocalDateTime lastHealthy;

	@JsonIgnore
	private boolean alarmSent;

	@JsonIgnore
	private LocalDateTime downSince;
}
