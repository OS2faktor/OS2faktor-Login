package dk.digitalidentity.task;

import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.service.MetadataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@Slf4j
public class CertificateMonitoringTask {

	@Autowired
	private MetadataService metadataService;

	@Autowired
	private OS2faktorConfiguration configuration;

    @Scheduled(cron = "${task.cert.monitoring:0 #{new java.util.Random().nextInt(55)} 14 * * WED}")
	public void monitorCertificates() {
		if (configuration.getScheduled().isEnabled()) {
			log.info("Certificate monitoring started");

			metadataService.monitorCertificates();
		}
	}
}
