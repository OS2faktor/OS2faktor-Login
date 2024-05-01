package dk.digitalidentity.rest.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.common.dao.model.KnownNetwork;
import dk.digitalidentity.common.service.KnownNetworkService;
import dk.digitalidentity.security.RequireAdministrator;

@RequireAdministrator
@RestController
public class KnownNetworkRestController {

	@Autowired
	private KnownNetworkService knownNetworkService;

	@PostMapping("/rest/admin/knownNetworks/save")
	@ResponseBody
	public ResponseEntity<?> saveNetworks(@RequestBody List<String> knownNetworks) {
		List<KnownNetwork> toBeSaved = new ArrayList<>();
		Map<String, KnownNetwork> currentNetworks = knownNetworkService.getAll().stream().collect(Collectors.toMap(KnownNetwork::getIp, Function.identity()));
		for (String knownNetwork : knownNetworks) {
			KnownNetwork network = currentNetworks.getOrDefault(knownNetwork, new KnownNetwork(knownNetwork));
			currentNetworks.remove(network.getIp());
			toBeSaved.add(network);
		}

		knownNetworkService.deleteAll(List.copyOf(currentNetworks.values()));
		knownNetworkService.saveAll(toBeSaved);

		return ResponseEntity.ok().build();
	}
}
