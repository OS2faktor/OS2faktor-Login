package dk.digitalidentity.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;

import dk.digitalidentity.api.dto.PasswordRequest;
import dk.digitalidentity.api.dto.PasswordResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PasswordService {

	@Autowired
	private SocketHandler socketHandler;

	public PasswordResponse validatePassword(PasswordRequest request) {
		PasswordResponse response = new PasswordResponse();
		response.setValid(false);

		try {
			AsyncResult<PasswordResponse> result = socketHandler.validatePassword(request.getUserId(), request.getPassword(), request.getDomain());
			
			return result.get();
		}
		catch (Exception ex) {
			log.error("Failed to validate password", ex);
		}

		return response;
	}

	public PasswordResponse setPassword(PasswordRequest request) {
		PasswordResponse response = new PasswordResponse();
		response.setValid(false);

		try {
			AsyncResult<PasswordResponse> result = socketHandler.setPassword(request.getUserId(), request.getPassword(), request.getDomain());
			
			return result.get();
		}
		catch (Exception ex) {
			log.error("Failed to set password", ex);
		}

		return response;
	}
}
