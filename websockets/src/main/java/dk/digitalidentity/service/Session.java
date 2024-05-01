package dk.digitalidentity.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.web.socket.WebSocketSession;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@Setter
public class Session {
	private static long idMax = 0;
	private long id;
	private WebSocketSession session;
	private String domain;
	private LocalDateTime cleanupTimestamp;
	private boolean authenticated;
	private String version;
	private boolean badState = false;
	
	class RequestTracker {
		int count;
		long totalTime;
	}
	
	// tracking
	private long requestCount = 0;
	private Map<String, RequestTracker> requestMap = new HashMap<>();

	public Session(WebSocketSession session) {
		this.session = session;
		this.domain = null;
		this.cleanupTimestamp = LocalDateTime.now().plusHours(2L);
		this.authenticated = false;
		this.id = Session.getNextId();
	}
	
	public synchronized void logRequest(String command, long timeToComplete) {
		requestCount++;
		
		RequestTracker tracker = requestMap.get(command);
		if (tracker == null) {
			tracker = new RequestTracker();
			tracker.count = 1;
			tracker.totalTime = timeToComplete;
			
			requestMap.put(command, tracker);
		}
		else {
			tracker.count++;
			tracker.totalTime += timeToComplete;
		}		
	}
	
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		
		builder.append("id: ").append(id).append(", ");
		builder.append("domain: ").append(domain).append(", ");
		builder.append("totalRequest: ").append(requestCount).append(", ");
		
		for (String command : requestMap.keySet()) {
			RequestTracker tracker = requestMap.get(command);
			
			builder.append(command).append(": ").append(tracker.count).append(" request(s) with average requestTime ").append(tracker.totalTime / tracker.count).append(" ms, ");
		}
		
		builder.append(" clientVersion: ").append(version);
		
		return builder.toString();
	}
	
	private static synchronized long getNextId() {
		return (++idMax);
	}
	
	public String getIdentifier() {
		return "(" + id + " / " + version + " / " + domain + ")";				
	}
	
	public boolean supportsIsAliveCheck() {
		if (version == null) {
			return false;
		}
		
		try {
			String tokens[] = version.split("\\.");
			if (tokens.length >= 2) {
				// supported since 1.8.0
				
				if (Integer.parseInt(tokens[0]) > 1) {
					return true;
				}
				
				if (Integer.parseInt(tokens[1]) >= 8) {
					return true;
				}
			}
		}
		catch (Exception ex) {
			log.error("Invalid client version: " + version, ex);
		}

		return false;
	}

	public boolean isStale() {
		if (cleanupTimestamp.isBefore(LocalDateTime.now()) || isBadState()) {
			return true;
		}
		
		return false;
	}
}
