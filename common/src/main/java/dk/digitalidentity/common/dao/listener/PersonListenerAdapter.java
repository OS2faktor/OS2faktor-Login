package dk.digitalidentity.common.dao.listener;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.NemloginQueue;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NemloginAction;
import dk.digitalidentity.common.service.NemloginQueueService;

// we need to ship this ASync, so it leaves the current transaction,
// and we do not end into infinite loops - also we do not need to modify
// the person, and we should already have all relevant data read on the person
@Component
public class PersonListenerAdapter {
	
	@Autowired
	private NemloginQueueService nemloginQueueService;

	@Autowired
	private CommonConfiguration commonConfiguration;

	@Async
	public void generateNemloginActions(Person person) {
		List<NemloginQueue> actions = new ArrayList<>();

		// create - brand new person record with transferToNemLog-in set to true
		if (person.isTransferToNemlogin()) {
			actions.add(new NemloginQueue(person, NemloginAction.CREATE));
			
			if (person.isQualifiedSignature()) {
				actions.add(new NemloginQueue(person, NemloginAction.ASSIGN_QUALIFIED_SIGNATURE));
			}
			
			if (person.isPrivateMitId()) {
				actions.add(new NemloginQueue(person, NemloginAction.ASSIGN_PRIVATE_MIT_ID));
			}
		}

		if (!actions.isEmpty()) {
			nemloginQueueService.saveAll(actions);
		}
	}
	
	@Async
    public void generateNemloginActions(Set<String> props, Person person) {
		List<NemloginQueue> actions = new ArrayList<>();

		// if no NemLoginUUID is present, but we need to transfer to MitID Erhverv, then create a CREATE order
		if (!StringUtils.hasLength(person.getNemloginUserUuid())) {
			if (person.isTransferToNemlogin() && !person.isLocked()) {
				// wipe existing failed in queue, clean slate so to speak
				nemloginQueueService.deleteFailedByPerson(person);
				
				// check if there is an existing create order (not failed, as those are deleted above)
				int existingCreateActionCount = nemloginQueueService.getByPersonAndAction(person, NemloginAction.CREATE).size();
				if (existingCreateActionCount == 0) {
					actions.add(new NemloginQueue(person, NemloginAction.CREATE));
				}
			}
		}

		// suspend or reactivate scenarioes
		if (StringUtils.hasLength(person.getNemloginUserUuid()) && props.contains("transferToNemlogin")) {
			if (person.isTransferToNemlogin()) {
				// assume the current status is "SUSPENDED", so reactivate
				actions.add(new NemloginQueue(person, NemloginAction.REACTIVATE));
			}			
			else {
				// assume current status is "ACTIVE", so suspend
				actions.add(new NemloginQueue(person, NemloginAction.SUSPEND));
			}
		}

		// if the person has a MitID Erhverv, and the email changes, update it in MitID Erhverv
		if (props.contains("email")) {
			if (person.isTransferToNemlogin() && StringUtils.hasLength(person.getNemloginUserUuid())) {
				actions.add(new NemloginQueue(person, NemloginAction.CHANGE_EMAIL));
			}
		}

		// flip private MitID
		if (props.contains("privateMitId") && commonConfiguration.getNemLoginApi().isPrivateMitIdEnabled()) {
			actions.add(new NemloginQueue(person, person.isPrivateMitId() ? NemloginAction.ASSIGN_PRIVATE_MIT_ID : NemloginAction.REVOKE_PRIVATE_MIT_ID));
		}

		// flip qualified signature
		if (props.contains("qualifiedSignature") && commonConfiguration.getNemLoginApi().isQualifiedSignatureEnabled()) {
			actions.add(new NemloginQueue(person, person.isQualifiedSignature() ? NemloginAction.ASSIGN_QUALIFIED_SIGNATURE : NemloginAction.REVOKE_QUALIFIED_SIGNATURE));
		}

		if (!actions.isEmpty()) {
			nemloginQueueService.saveAll(actions);
		}
    }
}
