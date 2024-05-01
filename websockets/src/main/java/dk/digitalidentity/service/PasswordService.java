package dk.digitalidentity.service;

import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;

import dk.digitalidentity.api.dto.PasswordRequest;
import dk.digitalidentity.api.dto.PasswordResponse;
import dk.digitalidentity.api.dto.PasswordResponse.PasswordStatus;
import dk.digitalidentity.api.dto.UnlockRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PasswordService {

	@Autowired
	private SocketHandler socketHandler;

	public PasswordResponse validatePassword(PasswordRequest request) {
		PasswordResponse response = new PasswordResponse();
		response.setStatus(PasswordStatus.FAILURE);

		try {
			AsyncResult<PasswordResponse> result = socketHandler.validatePassword(request.getUserName(), request.getPassword(), request.getDomain(), true);

			return result.get();
		}
		catch (Exception ex) {
			log.error("Failed to validate password", ex);

			response.setMessage(ex.getMessage());
		}

		return response;
	}

	public PasswordResponse setPassword(PasswordRequest request) {
		PasswordResponse response = new PasswordResponse();
		response.setStatus(PasswordStatus.FAILURE);

		try {
			AsyncResult<PasswordResponse> result = socketHandler.setPassword(request.getUserName(), request.getPassword(), request.getDomain(), true);

			// convert technical error to insufficent permissions if needed
			PasswordResponse res = result.get();
			if (res.getMessage() != null && res.getMessage().contains("E_ACCESSDENIED")) {
				res.setStatus(PasswordStatus.INSUFFICIENT_PERMISSION);
			}

			return result.get();
		}
		catch (Exception ex) {
			log.error("Failed to set password", ex);
			
			response.setMessage(ex.getMessage());
		}

		return response;
	}
	
	public PasswordResponse setPasswordWithForcedChange(PasswordRequest request) {
		PasswordResponse response = new PasswordResponse();
		response.setStatus(PasswordStatus.FAILURE);

		try {
			AsyncResult<PasswordResponse> result = socketHandler.setPasswordWithForcedChange(request.getUserName(), request.getPassword(), request.getDomain(), true);

			// convert technical error to insufficent permissions if needed
			PasswordResponse res = result.get();
			if (res.getMessage() != null && res.getMessage().contains("E_ACCESSDENIED")) {
				res.setStatus(PasswordStatus.INSUFFICIENT_PERMISSION);
			}

			return result.get();
		}
		catch (Exception ex) {
			log.error("Failed to set password", ex);
			
			response.setMessage(ex.getMessage());
		}

		return response;
	}
	
	public PasswordResponse unlockAccount(UnlockRequest request) {
		PasswordResponse response = new PasswordResponse();
		response.setStatus(PasswordStatus.FAILURE);

		try {
			AsyncResult<PasswordResponse> result = socketHandler.unlockAccount(request.getUserName(), request.getDomain(), true);

			// convert technical error to insufficent permissions if needed
			PasswordResponse res = result.get();
			if (res.getMessage() != null && res.getMessage().contains("E_ACCESSDENIED")) {
				res.setStatus(PasswordStatus.INSUFFICIENT_PERMISSION);
			}
			
			return res;
		}
		catch (Exception ex) {
			log.error("Failed to unlock account", ex);
			
			response.setMessage(ex.getMessage());
		}

		return response;
	}

	public PasswordResponse passwordExpires(UnlockRequest request) {
		PasswordResponse response = new PasswordResponse();
		response.setStatus(PasswordStatus.FAILURE);

		try {
			AsyncResult<PasswordResponse> result = socketHandler.passwordExpires(request.getUserName(), request.getDomain(), true);

			return result.get();
		}
		catch (Exception ex) {
			log.error("Failed to run password expires soon script", ex);

			response.setMessage(ex.getMessage());
		}

		return response;
	}

	public int activeSessions(String domain) {
		List<Session> sessions = socketHandler.getSessions();
		
		if (domain != null) {
			return (int) sessions.stream().filter(s -> Objects.equals(domain, s.getDomain())).count();
		}
		
		return sessions.size();
	}
}
