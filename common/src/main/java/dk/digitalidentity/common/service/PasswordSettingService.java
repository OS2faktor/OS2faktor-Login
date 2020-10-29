package dk.digitalidentity.common.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.PasswordSettingDao;
import dk.digitalidentity.common.dao.model.PasswordSetting;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PasswordSettingService {

	@Autowired
	private PasswordSettingDao passwordSettingDao;

	public PasswordSetting getSettings() {
		List<PasswordSetting> all = passwordSettingDao.findAll();
		
		if (all.size() == 0) {
			PasswordSetting settings = new PasswordSetting();
			settings.setMinLength(10L);
			settings.setBothCapitalAndSmallLetters(true);
			settings.setRequireDigits(false);
			settings.setRequireSpecialCharacters(false);

			return settings;
		}
		else if (all.size() == 1) {
			return all.get(0);
		}

		log.error("More than one row with password rules");

		return all.get(0);
	}

	public PasswordSetting save(PasswordSetting entity) {
		return passwordSettingDao.save(entity);
	}
}
