package dk.digitalidentity.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.EmailTemplate;
import dk.digitalidentity.common.dao.model.EmailTemplateChild;
import dk.digitalidentity.common.dao.model.PasswordSetting;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.EmailTemplateType;
import dk.digitalidentity.common.service.ADPasswordService;
import dk.digitalidentity.common.service.EmailTemplateService;
import dk.digitalidentity.common.service.PasswordSettingService;
import dk.digitalidentity.common.service.PersonService;

@Service
public class PasswordExpiresService {

	@Autowired
	private PersonService personService;

	@Autowired
	private CommonConfiguration commonConfiguration;

	@Autowired
	private PasswordSettingService passwordSettingService;

	@Autowired
	private EmailTemplateSenderService emailTemplateSenderService;

	@Autowired
	private ADPasswordService adPasswordService;
	
	@Autowired
	private EmailTemplateService emailTemplateService;

	public void notifyPasswordExpires() {
		int reminderDaysBeforeExpired = commonConfiguration.getPasswordSoonExpire().getReminderDaysBeforeExpired();

		List<Person> persons = personService.getAll(p -> {
			p.getDomain().getName();
			p.getTopLevelDomain().getName();
		});

		for (Person person : persons) {
			boolean notify = false;
			PasswordSetting settings = passwordSettingService.getSettings(person);
			
			if (settings.isForceChangePasswordEnabled() && person.getPasswordTimestamp() != null) {
				LocalDateTime expiredTimestamp = person.getPasswordTimestamp().plusDays(settings.getForceChangePasswordInterval());
				LocalDateTime almostExpiredTimestamp = expiredTimestamp.minusDays(reminderDaysBeforeExpired);

				if (LocalDate.now().equals(almostExpiredTimestamp.toLocalDate())) {
					notify = true;
				}
			}

			if (notify) {
				EmailTemplate emailTemplate = emailTemplateService.findByTemplateType(EmailTemplateType.PASSWORD_EXPIRES);
				for (EmailTemplateChild child : emailTemplate.getChildren()) {
					if (child.isEnabled() && child.getDomain().getId() == person.getDomain().getId()) {
						String message = EmailTemplateService.safeReplacePlaceholder(child.getMessage(), EmailTemplateService.RECIPIENT_PLACEHOLDER, person.getName());
						message = EmailTemplateService.safeReplacePlaceholder(message, EmailTemplateService.USERID_PLACEHOLDER, person.getSamaccountName());
						emailTemplateSenderService.send(person.getEmail(), person.getCpr(), person, child.getTitle(), message, child, false);
					}
				}

				adPasswordService.attemptRunPasswordExpiresSoonScript(person);
			}
		}
	}
}
