package dk.digitalidentity.task;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.config.modules.nemlogin.NemLoginIdMConfiguration;
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.service.SamlMetadataService;
import lombok.extern.slf4j.Slf4j;

@Component
@EnableScheduling
@Slf4j
public class CertificateMonitoringTask {

	@Autowired
	private SamlMetadataService metadataService;

	@Autowired
	private OS2faktorConfiguration configuration;

	@Autowired
	private CommonConfiguration commonConfiguration;

	@Scheduled(cron = "${task.cert.monitoring:0 #{new java.util.Random().nextInt(60)} 14 * * WED}")
	public void monitorCertificates() {
		if (configuration.getScheduled().isEnabled()) {
			log.info("Certificate monitoring started");

			metadataService.monitorCertificates();
			monitorNemLoginIdMKeystore();
		}
	}

	private void monitorNemLoginIdMKeystore() {
		NemLoginIdMConfiguration nemLoginApi = commonConfiguration.getNemLoginApi();
		if (!nemLoginApi.isEnabled() || !StringUtils.hasLength(nemLoginApi.getKeystoreLocation())) {
			return;
		}

		try {
			File keystoreFile = ResourceUtils.getFile(nemLoginApi.getKeystoreLocation());

			try (FileInputStream fis = new FileInputStream(keystoreFile)) {
				KeyStore keyStore = KeyStore.getInstance("PKCS12");
				keyStore.load(fis, nemLoginApi.getKeystorePassword().toCharArray());
	
				String alias = keyStore.aliases().nextElement();
				X509Certificate certificate = (X509Certificate) keyStore.getCertificate(alias);
	
				LocalDate expiry = certificate.getNotAfter().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
				if (expiry.isBefore(LocalDate.now().plusMonths(2))) {
					log.error("NemLog-in IdM keystore certificate expires: " + expiry + " (location: " + nemLoginApi.getKeystoreLocation() + ")");
				}
			}
		}
		catch (Exception ex) {
			log.error("Error checking NemLog-in IdM keystore certificate expiry", ex);
		}
	}
}
