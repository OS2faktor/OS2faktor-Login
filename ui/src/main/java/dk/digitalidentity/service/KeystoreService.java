package dk.digitalidentity.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.KeystoreDao;
import dk.digitalidentity.common.dao.model.Keystore;
import dk.digitalidentity.common.dao.model.enums.KnownCertificateAliases;
import dk.digitalidentity.common.dao.model.enums.SettingKey;
import dk.digitalidentity.common.service.SettingService;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class KeystoreService {

	@Autowired
	private KeystoreDao keystoreDao;
	
	@Autowired
	private SettingService settingService;
	
	@Transactional
	public void performCertificateSwap() {
		LocalDateTime tts = settingService.getLocalDateTimeSetting(SettingKey.CERTIFICATE_ROLLOVER_TTS);
		if (tts.isBefore(LocalDateTime.now())) {
			List<Keystore> keystores = keystoreDao.findAll();

			// make sure we have both a primary and a secondary available
			Keystore primary = null, secondary = null;
			for (Keystore keystore : keystores) {
				if (keystore.isPrimary()) {
					primary = keystore;
				}
				else if (keystore.isSecondary()) {
					secondary = keystore;
				}
			}

			if (secondary == null || primary == null) {
				log.error("Cannot perform certificate rollover, as we are missing primary/secondary pair");
				return;
			}

			primary.setAlias(KnownCertificateAliases.OCES_SECONDARY.toString());
			primary.setDisabled(true);
			primary.setLastUpdated(LocalDateTime.now());
			keystoreDao.save(primary);

			secondary.setAlias(KnownCertificateAliases.OCES.toString());
			secondary.setDisabled(false);
			secondary.setLastUpdated(LocalDateTime.now());
			keystoreDao.save(secondary);

			settingService.setLocalDateTimeSetting(SettingKey.CERTIFICATE_ROLLOVER_TTS, LocalDateTime.parse(SettingKey.CERTIFICATE_ROLLOVER_TTS.getDefaultValue()));

			log.info("Swapped primary certificates");
		}
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
