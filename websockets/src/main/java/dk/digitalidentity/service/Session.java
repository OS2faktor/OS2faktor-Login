package dk.digitalidentity.service;

import java.time.LocalDateTime;

import org.springframework.web.socket.WebSocketSession;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Session {
	private WebSocketSession session;
	private String domain;
	private LocalDateTime cleanupTimestamp;
	private boolean authenticated;

	public Session(WebSocketSession session) {
		this.session = session;
		this.domain = null;
		this.cleanupTimestamp = LocalDateTime.now().plusHours(2L);
		this.authenticated = false;
	}
}
