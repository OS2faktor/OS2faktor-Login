package dk.digitalidentity.service;

import java.time.LocalDateTime;
import java.util.List;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.KeystoreDao;
import dk.digitalidentity.common.dao.model.Keystore;
import dk.digitalidentity.common.dao.model.enums.SettingsKey;
import dk.digitalidentity.common.service.SettingService;
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
		LocalDateTime tts = settingService.getLocalDateTimeSetting(SettingsKey.CERTIFICATE_ROLLOVER_TTS);
		if (tts.isBefore(LocalDateTime.now())) {
			List<Keystore> keystores = keystoreDao.findAll();

			// ensure that there are two certificates
			if (keystores.size() != 2) {
				log.error("Planned certificate rollover not possible, because there are " + keystores.size() + " certificate(s) in database");
				return;
			}
			
			for (Keystore keystore : keystores) {
				if (keystore.isPrimaryForIdp() != keystore.isPrimaryForNemLogin()) {
					log.error("Planned certificate rollover not possible, because certificates are not aligned for Idp/NemLogin");
					return;
				}
				
				keystore.setPrimaryForIdp(!keystore.isPrimaryForIdp());
				keystore.setPrimaryForNemLogin(!keystore.isPrimaryForNemLogin());
				keystore.setLastUpdated(LocalDateTime.now());
			}
			
			keystoreDao.saveAll(keystores);
			settingService.setLocalDateTimeSetting(SettingsKey.CERTIFICATE_ROLLOVER_TTS, LocalDateTime.parse(SettingsKey.CERTIFICATE_ROLLOVER_TTS.getDefaultValue()));

			log.info("Swapped primary certificates");
		}
	}

	public List<Keystore> findAll() {
		return keystoreDao.findAll();
	}

	public void save(Keystore keystore) {
		keystoreDao.save(keystore);
	}

	public void saveAll(List<Keystore> keystores) {
		keystoreDao.saveAll(keystores);
	}
}
