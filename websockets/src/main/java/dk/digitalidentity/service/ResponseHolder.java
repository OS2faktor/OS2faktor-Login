package dk.digitalidentity.service;

import java.time.LocalDateTime;

import dk.digitalidentity.api.dto.PasswordResponse;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResponseHolder {
	private PasswordResponse response;
	private LocalDateTime tts;
	
	public ResponseHolder(PasswordResponse response) {
		this.response = response;
		this.tts = LocalDateTime.now().plusMinutes(5L);
	}
}
