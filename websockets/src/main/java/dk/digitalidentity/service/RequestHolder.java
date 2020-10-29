package dk.digitalidentity.service;

import java.time.LocalDateTime;

import dk.digitalidentity.service.model.Request;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RequestHolder {
	private Request request;
	private LocalDateTime tts;
	
	public RequestHolder(Request request) {
		this.request = request;
		this.tts = LocalDateTime.now().plusMinutes(5L);
	}
}
