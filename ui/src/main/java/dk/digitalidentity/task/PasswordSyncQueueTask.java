package dk.digitalidentity.task;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.service.PasswordSyncQueueService;
import dk.digitalidentity.config.OS2faktorConfiguration;
import lombok.RequiredArgsConstructor;

@Component
@EnableScheduling
@RequiredArgsConstructor
public class PasswordSyncQueueTask {
    private final OS2faktorConfiguration os2faktorConfiguration;
    private final PasswordSyncQueueService passwordSyncQueueService;

    @Scheduled(fixedDelayString = "${os2faktor.task.syncPassword.fixedDelay:1m}")
    public void run() {
		if (!os2faktorConfiguration.getScheduled().isEnabled()) {
			return;
	    }

		passwordSyncQueueService.processQueueItems();
    }

    @Scheduled(fixedDelayString = "${os2faktor.task.syncPassword.cleanInterval:15m}")
    public void cleanup() {
        if (!os2faktorConfiguration.getScheduled().isEnabled()) {
            return;
        }

        passwordSyncQueueService.cleanup();
    }
}
