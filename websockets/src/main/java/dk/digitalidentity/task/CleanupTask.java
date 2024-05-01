package dk.digitalidentity.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.service.SocketHandler;

@EnableScheduling
@Component
public class CleanupTask {

	@Autowired
	private SocketHandler socketHandler;
	
	@Scheduled(fixedRate = 5 * 60 * 1000)
	public void cleanupRequestResponseAndSendIsAliveRequests() {
		socketHandler.cleanupRequestResponse();
		socketHandler.sendIsAlive();
	}
	
	@Scheduled(fixedRate = 5 * 1000)
	public void cleanupStateSessions() {
		socketHandler.closeStaleSessions();
	}
}
