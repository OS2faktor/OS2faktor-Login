package dk.digitalidentity.config;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.session.jdbc.config.annotation.web.http.JdbcHttpSessionConfiguration;

@Configuration
@EnableJdbcHttpSession
public class JdbcSessionConfiguration {

    @Bean
    public JdbcHttpSessionConfiguration jdbcHttpSessionConfiguration() {
        JdbcHttpSessionConfiguration config = new JdbcHttpSessionConfiguration();
        config.setMaxInactiveInterval(Duration.ofSeconds(64800));

        int randomSecond = ThreadLocalRandom.current().nextInt(60);
        int randomMinute = ThreadLocalRandom.current().nextInt(10);
        
        String cron = randomSecond + " " + randomMinute + "/10 * * * *";
        config.setCleanupCron(cron);

        return config;
    }
}