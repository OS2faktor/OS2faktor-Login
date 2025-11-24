package dk.digitalidentity.rest.admin;

import java.time.LocalDateTime;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.common.dao.model.MitIdErhvervAccountError;
import dk.digitalidentity.common.dao.model.MitidErhvervCache;
import dk.digitalidentity.common.dao.model.NemloginQueue;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NemloginAction;
import dk.digitalidentity.common.service.MitIdErhvervAccountErrorService;
import dk.digitalidentity.common.service.NemloginQueueService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.mvc.admin.dto.NemLoginQueueRetryDTO;
import dk.digitalidentity.security.RequireSupporter;
import dk.digitalidentity.service.MitidErhvervCacheService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequireSupporter
@RestController
public class NemLoginQueueRestController {

	@Autowired
	private NemloginQueueService nemloginQueueService;
	
	@Autowired
	private PersonService personService;

	@Autowired
	private MitIdErhvervAccountErrorService mitIdErhvervAccountErrorService;
	
	@Autowired
	private MitidErhvervCacheService mitidErhvervCacheService;

	@PostMapping("/admin/nemlogin_queue/retryAction")
	public ResponseEntity<?> rerunAction(@RequestBody NemLoginQueueRetryDTO retry) {
		NemloginQueue queue = nemloginQueueService.getById(retry.getQueueId());
		if (queue == null) {
			return new ResponseEntity<>("Ordren findes ikke", HttpStatus.NOT_FOUND);
		}

		// TODO: fjern alt om RID i UI - det bruger vi ikke længere

		queue.setFailed(false);
		queue.setFailureReason(null);
		nemloginQueueService.save(queue);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@PostMapping("/admin/nemlogin_queue/fixAction/{id}")
	public ResponseEntity<?> fixAccountError(@PathVariable("id") long id) {
		MitIdErhvervAccountError dto = mitIdErhvervAccountErrorService.findById(id);
		if (dto == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		log.info("Attempting to fix " + dto.getErrorType() + " for " + dto.getPerson().getId());
		
		switch (dto.getErrorType()) {
			// null the NL3 UUID and attempt to reorder a MitID Erhverv account
			case ACCOUNT_DELETED_IN_MITID_ERHVERV: {
				Person person = dto.getPerson();

				if (StringUtils.hasLength(person.getNemloginUserUuid())) {
					person.setNemloginUserUuid(null);
					personService.save(person);
	
					if (person.isTransferToNemlogin()) {
						NemloginQueue newOrder = new NemloginQueue();
						newOrder.setAction(NemloginAction.CREATE);
						newOrder.setPerson(person);
						newOrder.setTts(LocalDateTime.now());
	
						nemloginQueueService.save(newOrder);
					}
				}

				break;
			}
			
			// attempt to reactivate the MitID Erhverv account
			case ACCOUNT_DISABLED_IN_MITID_ERHVERV: {
				Person person = dto.getPerson();

				if (StringUtils.hasLength(person.getNemloginUserUuid())) {
					if (person.isTransferToNemlogin()) {
						NemloginQueue newOrder = new NemloginQueue();
						newOrder.setAction(NemloginAction.REACTIVATE);
						newOrder.setNemloginUserUuid(person.getNemloginUserUuid());
						newOrder.setPerson(person);
						newOrder.setTts(LocalDateTime.now());
	
						nemloginQueueService.save(newOrder);
					}
				}

				break;
			}
			
			// copy the NL3 UUID onto the user, and then perform an association of the userId for good measure
			case UNASSOCIATED_ACCOUNT_IN_MITID_ERHVERV: {
				Person person = dto.getPerson();
				
				if (!StringUtils.hasLength(person.getNemloginUserUuid())) {
					person.setNemloginUserUuid(dto.getNemloginUserUuid());
					personService.save(person);
	
					// it should exist - the error arrives due to the cache-check
					MitidErhvervCache cache = mitidErhvervCacheService.findByUuid(dto.getNemloginUserUuid());
					if (cache != null) {
						if (!cache.isLocalCredential()) {
							NemloginQueue newOrder = new NemloginQueue();
							newOrder.setAction(NemloginAction.ASSIGN_LOCAL_USER_ID);
							newOrder.setNemloginUserUuid(person.getNemloginUserUuid());
							newOrder.setPerson(person);
							newOrder.setTts(LocalDateTime.now());								

							nemloginQueueService.save(newOrder);
						}

						if (Objects.equals(cache.getStatus(), "Suspended") && !person.isLocked()) {
							NemloginQueue newOrder = new NemloginQueue();
							newOrder.setAction(NemloginAction.REACTIVATE);
							newOrder.setNemloginUserUuid(person.getNemloginUserUuid());
							newOrder.setPerson(person);
							newOrder.setTts(LocalDateTime.now());

							nemloginQueueService.save(newOrder);
						}
					}
				}

				break;
			}
		}

		// handled, so remove it from the table
		mitIdErhvervAccountErrorService.delete(dto);

		return new ResponseEntity<>(HttpStatus.OK);
	}
}
