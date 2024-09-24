package dk.digitalidentity.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.common.dao.model.PasswordChangeQueue;
import dk.digitalidentity.common.service.PasswordChangeQueueService;

@RestController
public class PasswordChangeQueueApi {

	@Autowired
	private PasswordChangeQueueService passwordChangeQueueService;

	Comparator<PasswordChangeQueue> reverseComparator = (first, second) -> second.getTts().compareTo(first.getTts());

	@GetMapping("/api/passwordqueue/azure")
	public ResponseEntity<?> getNotSyncedToAzure(@RequestParam String domain) {
		List<PasswordChangeQueue> notSynced = passwordChangeQueueService.getNotSyncedAzure(domain);
		List<PasswordChangeQueue> result = filter(notSynced, "AZURE");

		return new ResponseEntity<>(result, HttpStatus.OK);
	}

	@PostMapping("/api/passwordqueue/azure/replicated")
	public ResponseEntity<?> setSyncedToAzure(@RequestParam String domain, @RequestBody List<Long> ids) {
		List<PasswordChangeQueue> notSynced = passwordChangeQueueService.getNotSyncedAzure(domain);
		List<PasswordChangeQueue> toSave = new ArrayList<>();
		for (PasswordChangeQueue passwordChangeQueue : notSynced) {
			if (ids.contains(passwordChangeQueue.getId())) {
				passwordChangeQueue.setAzureReplicated(true);
				toSave.add(passwordChangeQueue);
			}
		}

		passwordChangeQueueService.saveAll(toSave);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@GetMapping("/api/passwordqueue/workspace")
	public ResponseEntity<?> getNotSyncedToWorkspace(@RequestParam String domain) {
		List<PasswordChangeQueue> notSynced = passwordChangeQueueService.getNotSyncedGoogleWorkspace(domain);
		List<PasswordChangeQueue> result = filter(notSynced, "GW");

		return new ResponseEntity<>(result, HttpStatus.OK);
	}

	@PostMapping("/api/passwordqueue/workspace/replicated")
	public ResponseEntity<?> setSyncedToWorkspace(@RequestParam String domain, @RequestBody List<Long> ids) {
		List<PasswordChangeQueue> notSynced = passwordChangeQueueService.getNotSyncedGoogleWorkspace(domain);
		List<PasswordChangeQueue> toSave = new ArrayList<>();
		for (PasswordChangeQueue passwordChangeQueue : notSynced) {
			if (ids.contains(passwordChangeQueue.getId())) {
				passwordChangeQueue.setGoogleWorkspaceReplicated(true);
				toSave.add(passwordChangeQueue);
			}
		}

		passwordChangeQueueService.saveAll(toSave);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	private List<PasswordChangeQueue> filter(List<PasswordChangeQueue> notSynced, String type) {
		// sort by tts (newest first)
		notSynced.sort(reverseComparator);

		// remove duplicates (keeping the newest due to ordering)
		List<PasswordChangeQueue> result = new ArrayList<>();
		List<PasswordChangeQueue> redundant = new ArrayList<>();
		for (PasswordChangeQueue queue : notSynced) {
			if (result.stream().anyMatch(p -> Objects.equals(p.getSamaccountName(), queue.getSamaccountName()))) {
				if (type.equals("AZURE")) {
					queue.setAzureReplicated(true);
				}
				else if (type.equals("GW")) {
					queue.setGoogleWorkspaceReplicated(true);
				}

				redundant.add(queue);
			}
			else {
				result.add(queue);
			}
		}

		passwordChangeQueueService.saveAll(redundant);

		return result;
	}
}
