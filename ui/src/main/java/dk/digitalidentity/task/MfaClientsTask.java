package dk.digitalidentity.task;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import dk.digitalidentity.common.dao.model.LocalRegisteredMfaClient;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.dao.model.enums.SettingKey;
import dk.digitalidentity.common.service.LocalRegisteredMfaClientService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.SettingService;
import dk.digitalidentity.common.service.mfa.MFAManagementService;
import dk.digitalidentity.common.service.mfa.MFAService;
import dk.digitalidentity.common.service.mfa.model.ClientType;
import dk.digitalidentity.common.service.mfa.model.MfaClient;
import dk.digitalidentity.common.service.mfa.model.ProjectionClient;
import dk.digitalidentity.config.OS2faktorConfiguration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@EnableScheduling
public class MfaClientsTask {

	@Autowired
	private OS2faktorConfiguration configuration;
	
	@Autowired
	private MFAService mfaService;
	
	@Autowired
	private SettingService settingService;

	@Autowired
	private PersonService personService;

	@Autowired
	private MFAManagementService mfaManagementService;
	
	@Autowired
	private LocalRegisteredMfaClientService localRegisteredMfaClientService;

	// nightly
	@Scheduled(cron = "${cron.mfa.db.sync:0 #{new java.util.Random().nextInt(55)} 1 * * ?}")
	public void updateMfaCache() {
		if (!configuration.getScheduled().isEnabled()) {
			return;
		}
		
		StopWatch stopWatch = new StopWatch();
		stopWatch.start();
		
		log.info("Performing a synchronization of all MFA clients from OS2faktor database into cached clients table");
		
		// find all active persons and put into a cpr-based map
		Map<String, List<Person>> personMap = new HashMap<>();
		for (Person person : personService.getAll(p -> p.getMfaClients().size()).stream().collect(Collectors.toList())) {
			try {
				String encodedSsn = mfaService.encryptAndEncodeSsn(person.getCpr());
				
				if (!personMap.containsKey(encodedSsn)) {
					personMap.put(encodedSsn, new ArrayList<>());
				}

				personMap.get(encodedSsn).add(person);
			}
			catch (Exception ex) {
				log.error("Unable to encode cpr for person " + person.getId(), ex);
			}
		}
		
		List<ProjectionClient> allLocallyRegisteredMfaClients = mfaManagementService.lookupLocalMfaClientsInCentral(personMap);
		Map<String, List<ProjectionClient>> allLocallyRegisteredMfaClientsBySsn = allLocallyRegisteredMfaClients.stream().collect(Collectors.groupingBy(ProjectionClient::getSsn));
		
		List<ProjectionClient> allRegisteredMfaClients = mfaManagementService.lookupMfaClientsInCentral(personMap);
		Map<String, List<ProjectionClient>> allRegisteredMfaClientsBySsn = allRegisteredMfaClients.stream().collect(Collectors.groupingBy(ProjectionClient::getSsn));

		List<LocalRegisteredMfaClient> locallyRegisteredClients = localRegisteredMfaClientService.getAll();
		Map<String, List<LocalRegisteredMfaClient>> locallyRegisteredClientsBySsn = locallyRegisteredClients.stream().collect(Collectors.groupingBy(LocalRegisteredMfaClient::getCpr));

		for (String encodedSsn : personMap.keySet()) {
			List<Person> persons = personMap.get(encodedSsn);
			
			List<MfaClient> mfaClients = new ArrayList<>();
			Set<String> mfaClientsDeviceIds = new HashSet<>();
			
			// globally stored in OS2faktor MFA
			List<ProjectionClient> storedMfaClients = allRegisteredMfaClientsBySsn.get(encodedSsn);
			if (storedMfaClients != null) {
				for (ProjectionClient localClient : storedMfaClients) {
					if (!mfaClientsDeviceIds.contains(localClient.getDeviceId())) {
						MfaClient client = new MfaClient();
						client.setDeviceId(localClient.getDeviceId());
						client.setName(localClient.getName());
						client.setPasswordless(localClient.isPasswordless());
						client.setLocalClient(true);
						client.setSerialnumber(localClient.getSerialnumber());
						client.setLastUsed(localClient.getLastUsed());
						client.setAssociatedUserTimestamp(localClient.getAssociatedUserTimestamp());

						try {
							client.setType(ClientType.valueOf(localClient.getClientType()));
							client.setNsisLevel(NSISLevel.valueOf(localClient.getNsisLevel()));
						}
						catch (Exception ex) {
							log.warn("Unable to parse enum value for " + localClient.getDeviceId(), ex);
							continue;
						}

						mfaClients.add(client);
						mfaClientsDeviceIds.add(localClient.getDeviceId());
					}
				}
			}

			// locally stored in OS2faktor MFA
			List<ProjectionClient> locallyStoredMfaClients = allLocallyRegisteredMfaClientsBySsn.get(encodedSsn);
			if (locallyStoredMfaClients != null) {
				for (ProjectionClient localClient : locallyStoredMfaClients) {
					if (!mfaClientsDeviceIds.contains(localClient.getDeviceId())) {
						MfaClient client = new MfaClient();
						client.setDeviceId(localClient.getDeviceId());
						client.setName(localClient.getName());
						client.setPasswordless(localClient.isPasswordless());
						client.setLocalClient(true);
						client.setSerialnumber(localClient.getSerialnumber());
						client.setLastUsed(localClient.getLastUsed());
						client.setAssociatedUserTimestamp(localClient.getAssociatedUserTimestamp());

						try {
							client.setType(ClientType.valueOf(localClient.getClientType()));
							client.setNsisLevel(NSISLevel.valueOf(localClient.getNsisLevel()));
						}
						catch (Exception ex) {
							log.warn("Unable to parse enum value for " + localClient.getDeviceId(), ex);
							continue;
						}

						mfaClients.add(client);
						mfaClientsDeviceIds.add(localClient.getDeviceId());
					}
				}
			}

			// locally stored in OS2faktor Login
			List<LocalRegisteredMfaClient> localClients = locallyRegisteredClientsBySsn.get(persons.get(0).getCpr());
			if (localClients != null) {
				for (LocalRegisteredMfaClient localClient : localClients) {
					if (!mfaClientsDeviceIds.contains(localClient.getDeviceId())) {
						MfaClient client = new MfaClient();
						client.setDeviceId(localClient.getDeviceId());
						client.setName(localClient.getName());
						client.setNsisLevel(localClient.getNsisLevel());
						client.setType(localClient.getType());
						client.setLocalClient(true);

						if (localClient.getAssociatedUserTimestamp() != null) {
							client.setAssociatedUserTimestamp(Instant.ofEpochMilli(localClient.getAssociatedUserTimestamp().getTime())
									.atZone(ZoneId.systemDefault())
									.toLocalDateTime());
						}

						mfaClients.add(client);
						mfaClientsDeviceIds.add(localClient.getDeviceId());
					}
				}
			}

			mfaService.maintainCachedClients(persons, mfaClients);
		}
		
		stopWatch.stop();
		log.info("completed in: " + stopWatch.toString());
	}
	
	// run AFTER the syncMfaClients task, so we are sure we have fresh data
	@Scheduled(cron = "0 #{new java.util.Random().nextInt(59)} 2 * * ?")
	public void removeTOTPHDevicesTask() {
		if (!configuration.getScheduled().isEnabled()) {
			return; // Don't run if scheduled jobs are not enabled
		}

		if (!settingService.getBoolean(SettingKey.REMOVE_DEVICE_WHEN_PERSON_LOCKED)) {
            return;
        }
		
		log.info("Running task: removeTOTPHDevicesTask");

		mfaManagementService.removeTOTPHDevicesOnLockedPersons();
		
		log.info("Completed task: removeTOTPHDevicesTask");
	}

	// run once every 15 minutes
	@Scheduled(fixedDelay = 15 * 60 * 1000)
	public void fetchMfaLoginHistory() {
		if (!configuration.getScheduled().isEnabled()) {
			return; // Don't run if scheduled jobs are not enabled
		}

		mfaService.fetchMfaLoginHistory();
	}

	@Scheduled(cron = "0 #{new java.util.Random().nextInt(59)} 3 * * ?")
	public void removeOldMfaLoginHistory() {
		if (!configuration.getScheduled().isEnabled()) {
			return; // Don't run if scheduled jobs are not enabled
		}

		log.info("Running task: removeOldMfaLoginHistory");

		mfaService.removeOldMfaLoginHistory();
		
		log.info("Completed task: removeOldMfaLoginHistory");		
	}
}
