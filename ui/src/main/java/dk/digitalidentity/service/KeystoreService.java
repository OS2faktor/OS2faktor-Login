package dk.digitalidentity.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.KeystoreDao;
import dk.digitalidentity.common.dao.model.Keystore;
import dk.digitalidentity.common.dao.model.enums.KnownCertificateAliases;
import dk.digitalidentity.common.dao.model.enums.SettingKey;
import dk.digitalidentity.common.service.CertificateChangelogService;
import dk.digitalidentity.common.service.SettingService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class KeystoreService {

	@Autowired
	private KeystoreDao keystoreDao;
	
	@Autowired
	private SettingService settingService;
	
	@Autowired
	private CertificateChangelogService certificateChangelogService;

	public void performCertificateSwap() {
		LocalDateTime tts = settingService.getLocalDateTimeSetting(SettingKey.CERTIFICATE_ROLLOVER_TTS);
		if (tts.isBefore(LocalDateTime.now())) {
			performSwap(KnownCertificateAliases.OCES, KnownCertificateAliases.OCES_SECONDARY, SettingKey.CERTIFICATE_ROLLOVER_TTS);
		}
		
		tts = settingService.getLocalDateTimeSetting(SettingKey.CERTIFICATE_ROLLOVER_NL_TTS);
		if (tts.isBefore(LocalDateTime.now())) {
			performSwap(KnownCertificateAliases.NEMLOGIN, KnownCertificateAliases.NEMLOGIN_SECONDARY, SettingKey.CERTIFICATE_ROLLOVER_NL_TTS);
		}
	}
	
	private void performSwap(KnownCertificateAliases primary, KnownCertificateAliases secondary, SettingKey settingKey) {
		List<Keystore> keystores = keystoreDao.findAll();

		// make sure we have both a primary and a secondary available
		Keystore primaryKs = null, secondaryKs = null;
		for (Keystore keystore : keystores) {
			if (keystore.getAlias().equals(primary.toString())) {
				primaryKs = keystore;
			}
			else if (keystore.getAlias().equals(secondary.toString())) {
				secondaryKs = keystore;
			}
		}

		if (secondaryKs == null || primaryKs == null) {
			log.error("Cannot perform certificate rollover for " + primary.toString() + " , as we are missing primary/secondary pair");
			return;
		}

		primaryKs.setAlias(secondary.toString());
		primaryKs.setDisabled(true);
		primaryKs.setLastUpdated(LocalDateTime.now());
		keystoreDao.save(primaryKs);

		secondaryKs.setAlias(primary.toString());
		secondaryKs.setDisabled(false);
		secondaryKs.setLastUpdated(LocalDateTime.now());
		keystoreDao.save(secondaryKs);

		settingService.setLocalDateTimeSetting(settingKey, LocalDateTime.parse(settingKey.getDefaultValue()));
		
		certificateChangelogService.swapCertificate("system", "Promoted secondary certificate to primary for " + primary.toString());

		log.info("Swapped primary/secondary certificates for " + primary.toString());
	}

	public List<Keystore> findAll() {
		return keystoreDao.findAll();
	}
	
	public Keystore findByAlias(String alias) {
		return keystoreDao.findByAlias(alias);
	}

	public void save(Keystore keystore) {
		keystoreDao.save(keystore);
	}

	public void saveAll(List<Keystore> keystores) {
		keystoreDao.saveAll(keystores);
	}
}
