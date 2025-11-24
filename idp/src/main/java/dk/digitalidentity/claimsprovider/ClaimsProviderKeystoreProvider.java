package dk.digitalidentity.claimsprovider;

import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.dao.model.enums.KnownCertificateAliases;
import dk.digitalidentity.samlmodule.service.DISAML_KeystoreProvider;
import dk.digitalidentity.service.KeystoreService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@EnableScheduling
public class ClaimsProviderKeystoreProvider implements DISAML_KeystoreProvider {
	private KeystoreService keystoreService;
	
	@Autowired
	public ClaimsProviderKeystoreProvider(@Lazy KeystoreService keystoreService) {
		this.keystoreService = keystoreService;
	}
	
	@Override
	public KeyStore getPrimaryKeystore() {
		return keystoreService.getJavaKeystore(KnownCertificateAliases.NEMLOGIN.toString());
	}

	@Override
	public String getPrimaryKeystorePassword() {
		return keystoreService.getJavaKeystorePassword(KnownCertificateAliases.NEMLOGIN.toString());
	}

	@Override
	public KeyStore getSecondaryKeystore() {
		return keystoreService.getJavaKeystore(KnownCertificateAliases.NEMLOGIN_SECONDARY.toString());
	}

	@Override
	public String getSecondaryKeystorePassword() {
		return keystoreService.getJavaKeystorePassword(KnownCertificateAliases.NEMLOGIN_SECONDARY.toString());
	}

	// run every day at 10'o'clock
	@Scheduled(cron = "0 #{new java.util.Random().nextInt(55)} 10 * * ?")
	public void checkExpiry() throws Exception {
		KeyStore keyStore = keystoreService.getJavaKeystore(KnownCertificateAliases.NEMLOGIN.toString());
		if (keyStore != null) {
			X509Certificate certificate = (X509Certificate) keyStore.getCertificate(keyStore.aliases().nextElement());
			
			LocalDate expiry = certificate
				.getNotAfter()
				.toInstant()
				.atZone(ZoneId.systemDefault())
				.toLocalDate();
			
			if (expiry.isBefore(LocalDate.now().plusMonths(3))) {
				// check if we have already planned a certificate rollover
				KeyStore secondaryKeystore = keystoreService.getJavaKeystore(KnownCertificateAliases.NEMLOGIN_SECONDARY.toString());
				if (secondaryKeystore != null) {
					X509Certificate secondaryCertificate = (X509Certificate) keyStore.getCertificate(keyStore.aliases().nextElement());
					
					LocalDate secondaryExpiry = secondaryCertificate
						.getNotAfter()
						.toInstant()
						.atZone(ZoneId.systemDefault())
						.toLocalDate();

					// if we have a secondary certificate, that is valid for at least another year, then we are in the progress of a rollover
					if (secondaryExpiry.isAfter(LocalDate.now().plusMonths(12))) {
						return;
					}
				}

				// well, that is likely the most important, but not the only ClaimsProvider that uses this certificate
				log.error("NemLog-in certificate (for private MitID) expires: " + expiry.toString());
			}
		}
	}
}
