package dk.digitalidentity.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.EmailTemplate;
import dk.digitalidentity.common.dao.model.EmailTemplateChild;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.EmailTemplateType;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.CprService;
import dk.digitalidentity.common.service.EmailTemplateService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.dto.CprLookupDTO;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CprStatusCheckService {

	@Autowired
	private AuditLogger auditLogger;
	
	@Autowired
	private PersonService personService;
	
	@Autowired
	private CommonConfiguration configuration;
	
	@Autowired
	private EmailTemplateService emailTemplateService;
	
	@Autowired
	private EmailTemplateSenderService emailTemplateSenderService;
	
	@Autowired
	private CprService cprService;
	
	@Transactional
	public void syncNamesAndCivilstandFromCpr() throws Exception {
		if (!configuration.getCpr().isEnabled()) {
			log.warn("Called method syncNamesAndCivilstandFromCpr, but cpr is disabled");
			return;
		}

		auditLogger.updateFromCprJob();

		// change list of people to a map mapped by cpr (TODO: make the filtered lookup in the DB instead)
		List<Person> all = personService.getAll().stream().filter(p -> p.isNsisAllowed() && !p.isLockedDataset()).collect(Collectors.toList());
		HashMap<String, List<Person>> personMap = new HashMap<>();
		for (Person person : all) {
			if (!personMap.containsKey(person.getCpr())) {
				personMap.put(person.getCpr(), new ArrayList<Person>());
			}

			personMap.get(person.getCpr()).add(person);
		}

		// run through all unique CPR numbers and call CPR to check for changes
		int failedAttempts = 0;

		boolean singleSleepAttemptCompleted = false;
		List<Person> toBeSaved = new ArrayList<>();
		for (String cpr : personMap.keySet()) {
			// sometimes we just need to sleep a little bit - but only once, otherwise we will never complete this task
			if (!singleSleepAttemptCompleted && failedAttempts == 3) {
				try {
					singleSleepAttemptCompleted = true;
					Thread.sleep(3000);
				}
				catch (Exception ignored) {
					;
				}
			}
			else if (failedAttempts >= 5) {
				log.error("Got 5 timeouts in a row - aborting further lookup");
				break;
			}

			if (cpr.length() == 10 && !cprService.isFictionalCpr(cpr)) {
				try {
					// Fetch information from CPR
					Future<CprLookupDTO> cprFuture = cprService.getByCpr(cpr);
					CprLookupDTO dto = (cprFuture != null) ? cprFuture.get(5, TimeUnit.SECONDS) : null;

					if (dto == null) {
						log.warn("Cpr response was empty for " + PersonService.maskCpr(cpr));
						failedAttempts++;
						continue;
					}

					if (dto.isDoesNotExist()) {
						failedAttempts = 0;
						continue;
					}
					
					// Change name and nameProtection for updated people
					List<Person> people = personMap.get(cpr);
					for (Person person : people) {
						boolean change = false;
						
						// Dødsfald og bortkomst (samme statusfelt i den service vi kalder)
						if (!Objects.equals(person.isLockedDead(), dto.isDead())) {
							person.setLockedDead(dto.isDead());

							if (dto.isDead()) {
								auditLogger.personDead(person);
							}

							change = true;
						}

						// Umyndiggørelse
						if (!Objects.equals(person.isLockedDisenfranchised(), dto.isDisenfranchised())) {
							person.setLockedDisenfranchised(dto.isDisenfranchised());

							if (dto.isDisenfranchised()) {
								auditLogger.personDisenfranchised(person);
								
 								EmailTemplate emailTemplate = emailTemplateService.findByTemplateType(EmailTemplateType.PERSON_DISENFRANCHISED);
								for (EmailTemplateChild child : emailTemplate.getChildren()) {
									if (child.isEnabled() && child.getDomain().getId() == person.getDomain().getId()) {
										String message = EmailTemplateService.safeReplacePlaceholder(child.getMessage(), EmailTemplateService.RECIPIENT_PLACEHOLDER, person.getName());
										message = EmailTemplateService.safeReplacePlaceholder(message, EmailTemplateService.USERID_PLACEHOLDER, person.getSamaccountName());
										emailTemplateSenderService.send(person.getEmail(), person.getCpr(), person, child.getTitle(), message, child, true);
									}
								}
							}

							change = true;
						}
						
						// Extra check
						change |= cprService.updateName(person, dto);
						
						if (change) {
							toBeSaved.add(person);
						}
					}

				}
				catch (TimeoutException ex) {
					log.warn("Could not fetch data from cpr within the timeout for person: " + PersonService.maskCpr(cpr) , ex);
					failedAttempts++;
					continue;
				}

				failedAttempts = 0;
			}
			else {
				log.warn("CPR Lookup for person: " + PersonService.maskCpr(cpr) + " skipped because of invalid cpr.");
			}
		}

		if (toBeSaved.size() > 0) {
			personService.saveAll(toBeSaved);
		}
	}
}
