package dk.digitalidentity.rest.admin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.common.dao.model.KnownNetwork;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.KnownNetworkService;
import dk.digitalidentity.security.RequireAdministrator;
import dk.digitalidentity.security.SecurityUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequireAdministrator
@RestController
public class KnownNetworkRestController {

	@Autowired
	private AuditLogger auditLogger;

	@Autowired
	private KnownNetworkService knownNetworkService;

	@Autowired
	private SecurityUtil securityUtil;

	@PostMapping("/rest/admin/knownNetworks/save")
	@ResponseBody
	public ResponseEntity<?> saveNetworks(@RequestBody List<String> knownNetworks) {
		Person admin = securityUtil.getPerson();
		if (admin == null) {
			log.warn("Could not find admin while saving known networks");
			return ResponseEntity.badRequest().build();
		}

		List<KnownNetwork> toBeSaved = new ArrayList<>();
		Map<String, KnownNetwork> currentNetworks = knownNetworkService.getAll().stream().collect(Collectors.toMap(KnownNetwork::getIp, Function.identity()));
		Set<String> originalIps = new HashSet<>(currentNetworks.keySet());
		for (String knownNetwork : knownNetworks) {
			KnownNetwork network = currentNetworks.getOrDefault(knownNetwork, new KnownNetwork(knownNetwork));
			currentNetworks.remove(network.getIp());
			toBeSaved.add(network);
		}

		List<String> removed = currentNetworks.values().stream().map(KnownNetwork::getIp).collect(Collectors.toList());
		List<String> added = knownNetworks.stream().filter(ip -> !originalIps.contains(ip)).collect(Collectors.toList());

		knownNetworkService.deleteAll(List.copyOf(currentNetworks.values()));
		knownNetworkService.saveAll(toBeSaved);
		auditLogger.changeKnownNetworks(added, removed, admin);

		return ResponseEntity.ok().build();
	}
}
