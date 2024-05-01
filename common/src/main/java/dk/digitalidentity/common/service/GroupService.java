package dk.digitalidentity.common.service;

import dk.digitalidentity.common.dao.GroupDao;
import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.Group;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.mapping.PersonGroupMapping;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GroupService {

	@Autowired
	private GroupDao groupDao;

	public Group getById(long id) {
		return groupDao.findById(id);
	}
	
	public Group getByUUID(String uuid) {
		return groupDao.findByUuid(uuid);
	}

	public List<Group> getByUuidIn(Set<String> uuids) {
		return groupDao.findByUuidIn(uuids);
	}

	public Group getByName(String name) {
		return groupDao.findByName(name);
	}

	public List<Group> getAll() {
		return groupDao.findAll();
	}

	public Group save(Group group) {
		return groupDao.save(group);
	}

	public List<Group> saveAll(List<Group> groups) {
		return groupDao.saveAll(groups);
	}

	public void delete(Group group) {
		groupDao.delete(group);
	}

	public void deleteById(long id) {
		groupDao.deleteById(id);
	}

	public List<Group> getByDomain(Domain domain) {
		return groupDao.findByDomain(domain);
	}

	public static boolean memberOfGroup(Person person, Group group) {
		Set<Long> groupIds = new HashSet<>();
		groupIds.add(group.getId());

		return memberOf(person, groupIds);
	}

	public static boolean memberOfGroup(Person person, List<Group> groups) {
		Set<Long> groupIds = groups.stream().map(g -> g.getId()).collect(Collectors.toSet());

		return memberOf(person, groupIds);
	}

	public static boolean memberOfGroupMapping(Person person, List<PersonGroupMapping> mappings) {		
		Set<Long> groupIds = mappings.stream().map(pgm -> pgm.getGroup().getId()).collect(Collectors.toSet());
		
		return memberOf(person, groupIds);
	}
	
	private static boolean memberOf(Person person, Set<Long> groupIds) {
		if (person.getGroups() == null) {
			return false;
		}

		Set<Long> groupMemberShips = person.getGroups().stream().map(pgm -> pgm.getGroup().getId()).collect(Collectors.toSet());
		if (groupIds.stream().anyMatch(id -> groupMemberShips.contains(id))) {
			return true;
		}

		return false;
	}
}
