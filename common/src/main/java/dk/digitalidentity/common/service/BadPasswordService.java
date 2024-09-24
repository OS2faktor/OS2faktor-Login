package dk.digitalidentity.common.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import dk.digitalidentity.common.dao.BadPasswordDao;
import dk.digitalidentity.common.dao.model.BadPassword;

@Service
@EnableScheduling
public class BadPasswordService {
	private Set<String> cache = new HashSet<>();

	@Autowired
	private BadPasswordDao badPasswordDao;
	
	public List<BadPassword> getAll() {
		return badPasswordDao.findAll();
	}

	// reload every 5 minutes
	@Scheduled(fixedDelay = 5 * 60 * 1000)
	public void reloadBadPasswords() {
		cache = badPasswordDao.findAll().stream().map(p -> p.getPassword().toLowerCase()).collect(Collectors.toSet());
	}

	public void delete(long id) {
		badPasswordDao.deleteById(id);
	}
	
	public boolean match(String password) {
		if (!StringUtils.hasLength(password)) {
			return false;
		}

		password = password.toLowerCase();
		for (String word : cache) {
			if (password.contains(word)) {
				return true;
			}
		}

		return false;
	}
	
	public List<BadPassword> findByPassword(String password) {
		return badPasswordDao.findByPassword(password);
	}

	public BadPassword save(BadPassword badPassword) {
		return badPasswordDao.save(badPassword);
	}
}
