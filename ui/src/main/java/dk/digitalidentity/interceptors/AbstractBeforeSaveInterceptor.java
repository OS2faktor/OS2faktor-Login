package dk.digitalidentity.interceptors;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.PersonDao;
import dk.digitalidentity.common.dao.model.NemloginQueue;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NemloginAction;
import dk.digitalidentity.common.service.NemloginQueueService;

@Component
public class AbstractBeforeSaveInterceptor {
	
	@Autowired
	private PersonDao personDao;

	@Autowired
	private NemloginQueueService nemloginQueueService;
	
	@Autowired
	private AbstractBeforeSaveInterceptor self;
	
	@Autowired
	private CommonConfiguration commonConfiguration;
	
	@Transactional
	public void handleSavePerson(Person person) {
		
		// handle nemLogin action queue
		if (commonConfiguration.getNemLoginApi().isEnabled()) {
			List<NemloginQueue> actions = new ArrayList<>();

			Person oldPerson = self.loadOldPerson(person.getId());
			if (oldPerson != null) {

				if (!StringUtils.hasLength(person.getNemloginUserUuid())) {
					// create if needed (but wait until we have updated name from CPR register)
					// TODO: disabled check for now - we might need it later, but then a safer check, that
					//       just ensures a lookup in CPR before sending to NemLogin
					if (person.isTransferToNemlogin()) { // && person.isCprNameUpdated()) {
						// wipe existing failed in queue, clean slate so to speak
						nemloginQueueService.deleteFailedByPerson(person);
						
						// check if there is an existing create order (not failed, as those are deleted above)
						int existingCreateActionCount = nemloginQueueService.getByPersonAndAction(oldPerson, NemloginAction.CREATE).size();
						if (existingCreateActionCount == 0) {
							actions.add(new NemloginQueue(person, NemloginAction.CREATE));
						}
					}
				}
				else {
					// update if needed
					boolean oldTransferToNemlogin = oldPerson.isTransferToNemlogin() && !oldPerson.isLocked();
					boolean transferToNemlogin = person.isTransferToNemlogin() && !person.isLocked();

					if (oldTransferToNemlogin && !transferToNemlogin) {
						// deactivate - no reason to perform any other updates

						actions.add(new NemloginQueue(person, NemloginAction.SUSPEND));
					}
					else if (!oldTransferToNemlogin && transferToNemlogin) {
						// reactivate - all fields should be updated anyway

						actions.add(new NemloginQueue(person, NemloginAction.REACTIVATE));
					}
					else if (transferToNemlogin) {
						// generic updates on single fields

						if (!Objects.equals(oldPerson.getEmail(), person.getEmail())) {
							actions.add(new NemloginQueue(person, NemloginAction.CHANGE_EMAIL));
						}
					}

					if (commonConfiguration.getNemLoginApi().isPrivateMitIdEnabled()) {
						boolean oldPrivateMitId = oldPerson.isPrivateMitId() && oldTransferToNemlogin;
						boolean privateMitId = person.isPrivateMitId() && transferToNemlogin;
						if (!Objects.equals(oldPrivateMitId, privateMitId)) {
							actions.add(new NemloginQueue(person, privateMitId ? NemloginAction.ASSIGN_PRIVATE_MIT_ID : NemloginAction.REVOKE_PRIVATE_MIT_ID));
						}
					}
					
					if (commonConfiguration.getNemLoginApi().isQualifiedSignatureEnabled()) {
						boolean oldQualifiedSignature = oldPerson.isQualifiedSignature();
						boolean qualifiedSignature = person.isQualifiedSignature();
						if (!Objects.equals(oldQualifiedSignature, qualifiedSignature)) {
							actions.add(new NemloginQueue(person, qualifiedSignature ? NemloginAction.ASSIGN_QUALIFIED_SIGNATURE : NemloginAction.REVOKE_QUALIFIED_SIGNATURE));
						}
					}
				}
			}
			else {
				
				// create - brand new person record with transferToNemLog-in set to true
				if (person.isTransferToNemlogin()) {
					actions.add(new NemloginQueue(person, NemloginAction.CREATE));
				}
			}
			
			if (!actions.isEmpty()) {
				nemloginQueueService.saveAll(actions);
			}
		}
	}

	// we ensure a fresh copy is loaded by setting the propagation
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Person loadOldPerson(long id) {
		return personDao.findById(id);
	}
}
