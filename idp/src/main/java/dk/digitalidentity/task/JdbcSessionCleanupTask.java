package dk.digitalidentity.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class JdbcSessionCleanupTask {

    @Autowired
    private JdbcIndexedSessionRepository sessionRepository;
    
    @Scheduled(cron = "#{new java.util.Random().nextInt(60)} #{new java.util.Random().nextInt(10)}/10 * * * ?")
    public void cleanupJdbcSessions() {
    	sessionRepository.cleanUpExpiredSessions();
    }
}
