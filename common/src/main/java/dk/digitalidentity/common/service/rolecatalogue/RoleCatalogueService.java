package dk.digitalidentity.common.service.rolecatalogue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.service.model.RoleCatalogueOIOBPPResponse;
import dk.digitalidentity.common.service.model.RoleCatalogueRolesResponse;
import dk.digitalidentity.common.service.rolecatalogue.RoleCatalogueStub.ItSystemRecord;
import dk.digitalidentity.common.service.rolecatalogue.RoleCatalogueStub.SystemRoleRecord;

@Service
@EnableScheduling
public class RoleCatalogueService {

	@Autowired
	private RoleCatalogueStub stub;

	// many lookups are cached for 5 seconds, so we can write code without having to worry about making the same REST call
	// multiple times during our code execution. Only drawback is that role-changes will take a few extra seconds to go into effect
	@Scheduled(fixedDelay = 5 * 1000)
	public void cacheClear() {
		stub.cacheClear();
	}

	// slow cache is cleared every 8 hours. This is just for showing pretty role names in UI
	@Scheduled(fixedDelay = 8 * 60 * 60 * 1000)
	public void slowCacheClear() {
		stub.slowCacheClear();
	}

	public String getOIOBPP(Person person, String system) {
		return stub.getOIOBPP(person.getSamaccountName(), system, person.getDomain().getRoleCatalogueDomain());
	}

	public List<String> getSystemRoles(Person person, String itSystem) {
		RoleCatalogueRolesResponse response = stub.lookupRoles(person.getSamaccountName(), itSystem, person.getDomain().getRoleCatalogueDomain());
		if (response == null) {
			return new ArrayList<>();
		}
		
		return response.getSystemRoles();
	}

	public List<String> getUserRoleNames(Person person, String itSystem) {
		RoleCatalogueRolesResponse response = stub.lookupRoles(person.getSamaccountName(), itSystem, person.getDomain().getRoleCatalogueDomain());
		if (response == null) {
			return new ArrayList<>();
		}

		return new ArrayList<>(response.getRoleMap().values());
	}

	public List<String> getUserRoles(Person person, String itSystem) {
		RoleCatalogueRolesResponse response = stub.lookupRoles(person.getSamaccountName(), itSystem, person.getDomain().getRoleCatalogueDomain());
		if (response == null) {
			return new ArrayList<>();
		}
		
		return response.getUserRoles();
	}
	
	public String getSystemRolesAsOIOBPP(Person person, String itSystem) {
		RoleCatalogueOIOBPPResponse response = lookupRolesAsOIOBPP(person, itSystem);
		if (response == null) {
			return null;
		}
		
		return response.getOioBPP();
	}

	public boolean hasUserRole(Person person, String userRoleId) {
		return stub.hasUserRole(person.getSamaccountName(), userRoleId, person.getDomain().getRoleCatalogueDomain());
	}

	public boolean hasSystemRole(Person person, String systemRoleId) {
		return stub.hasSystemRole(person.getSamaccountName(), systemRoleId, person.getDomain().getRoleCatalogueDomain());
	}
	
	private RoleCatalogueOIOBPPResponse lookupRolesAsOIOBPP(Person person, String itSystem) {
		return stub.lookupRolesAsOIOBPP(person.getSamaccountName(), itSystem, person.getDomain().getRoleCatalogueDomain());
	}

	public Map<String, String> getSystemRolesDisplayName(Person person, String itSystem) {

		// get person's systemRoles
		RoleCatalogueRolesResponse systemRolesResponse = stub.lookupRoles(person.getSamaccountName(), itSystem, person.getDomain().getRoleCatalogueDomain());
		if (systemRolesResponse == null) {
			// Person had no systemRoles assigned
			return new HashMap<>();
		}

		// get all itSystems
		List<ItSystemRecord> allItSystems = stub.getAllItSystems();

		// convert itSystem(identifier) to ID
		Optional<ItSystemRecord> matchingItSystem = allItSystems.stream().filter(x -> Objects.equals(x.identifier(), itSystem)).findAny();
		
		if (matchingItSystem.isEmpty()) {
			// Could not fetch all itSystems to get the id
			return new HashMap<>();
		}

		long itSystemId = matchingItSystem.get().id();

		// get systemRoles with names
		List<SystemRoleRecord> extendedSystemRoles = stub.getExtendedSystemRoles(itSystemId);
		if (extendedSystemRoles.isEmpty()) {
			// Could not fetch systemRoles from RoleCatalog
			return new HashMap<>();
		}

		// build a map of IDs and displayName
		Map<String, String> result = new HashMap<>();
		for (String systemRole : systemRolesResponse.getSystemRoles()) {
			Optional<SystemRoleRecord> systemRoleRecord = extendedSystemRoles.stream().filter(r -> Objects.equals(r.identifier(), systemRole)).findAny();
			if (systemRoleRecord.isPresent()) {
				result.put(systemRole, systemRoleRecord.get().name());
			}
		}

		return result;
	}

	public Map<String, String> getUserRolesWithDisplayNames(Person person, String itSystem) {
		RoleCatalogueRolesResponse response = stub.lookupRoles(person.getSamaccountName(), itSystem, person.getDomain().getRoleCatalogueDomain());
		if (response == null) {
			return new HashMap<>();
		}

		return response.getRoleMap();
	}
}
