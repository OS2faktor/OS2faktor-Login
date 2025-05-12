package dk.digitalidentity.common.service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.PasswordSettingDao;
import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.PasswordSetting;
import dk.digitalidentity.common.dao.model.Person;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@EnableCaching
public class PasswordSettingService {

	@Autowired
	private PasswordSettingDao passwordSettingDao;

	@Autowired
	private DomainService domainService;
	
	@Autowired
	private PasswordSettingService self;

	@CacheEvict(value = "passwordSettingsByDomain", allEntries = true)
	public void cleanPasswordSettingsCache() {
		;
	}

	@Scheduled(fixedRate = 5 * 60 * 1000)
	public void cleanUpTask() {
		self.cleanPasswordSettingsCache();
	}

	public PasswordSetting getSettings(Person person) {
		if (person.isTrustedEmployee()) {
			Domain domain = domainService.getTrustedEmployeesDomain();
			if (domain != null) {
				return self.getSettings(domain);
			}
		}

		return self.getSettings(person.getDomain());
	}

	public String getDisallowedNames(Person person) {
		List<String> disallowedNames = Arrays.stream(person.getName().toLowerCase().split(" ")).collect(Collectors.toList());
		if (person.getSamaccountName() != null) {
			disallowedNames.add(person.getLowerSamAccountName());
		}

		return disallowedNames.stream().collect(Collectors.joining(", "));
	}

	@Cacheable("passwordSettingsByDomain")
	public PasswordSetting getSettingsCached(Domain domain) {
		return getSettings(domain);
	}
	
	// only call this from the UI settings, when modifying rules for a given domain - the person-version is to be used for actual lookups
	public PasswordSetting getSettings(Domain domain) {
		List<PasswordSetting> all = passwordSettingDao.findByDomain(domain);

		if (all.size() == 0) {
			PasswordSetting settings = new PasswordSetting();
			settings.setMinLength(10L);
			settings.setMaxLength(64L);
			settings.setRequireComplexPassword(false);
			settings.setRequireLowercaseLetters(true);
			settings.setRequireUppercaseLetters(false);
			settings.setRequireDigits(false);
			settings.setRequireSpecialCharacters(false);
			settings.setDisallowDanishCharacters(false);
			settings.setDisallowNameAndUsername(false);
			settings.setOldPasswordNumber((long) 8);
			settings.setTriesBeforeLockNumber((long) 5);
			settings.setLockedMinutes((long) 5);
			settings.setMaxPasswordChangesPrDayEnabled(false);
			settings.setMaxPasswordChangesPrDay((long) 1);
			settings.setDomain(domain);
			settings.setPreventBadPasswords(true);
			settings.setSpecificSpecialCharactersEnabled(false);
			settings.setAllowedSpecialCharacters(null);
			settings.setForceChangePasswordEnabled(false);
			settings.setForceChangePasswordInterval(365L);

			return settings;
		}
		else if (all.size() == 1) {
			return all.get(0);
		}

		log.error("More than one row with password rules for domain: " + domain.getName());
		return all.get(0);
	}

	public List<PasswordSetting> getAllSettings() {
		return passwordSettingDao.findAll();
	}

	public PasswordSetting save(PasswordSetting entity) {
		return passwordSettingDao.save(entity);
	}
}
