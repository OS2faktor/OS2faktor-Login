package dk.digitalidentity.service;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.session.Session;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.util.Constants;

/**
 * This session server is used to fetch session information from other sessions than the one tied to this context
 */
@Service
public class OtherSessionHelper {

	@Autowired
	private JdbcIndexedSessionRepository sessionRepository;
	
	@Autowired
	private PersonService personService;

	public String getString(String sessionId, String sessionKey) {
		return get(sessionId, sessionKey, String.class);
	}

	public LocalDateTime getLocalDateTime(String sessionId, String sessionKey) {
		return get(sessionId, sessionKey, LocalDateTime.class);
	}


	public Person getPerson(String sessionId) {
		Long personId = get(sessionId, Constants.PERSON_ID, Long.class);
		if (personId == null) {
			return null;
		}
		
		return personService.getById(personId);
	}

	/**
	 * Generic session value setter method.
	 * @return true if setting the value was a success, otherwise false
	 */
	public boolean set(String sessionId, String sessionKey, Object value) {
		Session session = getSessionBySessionId(sessionId);
		if (session == null) {
			return false;
		}

		session.setAttribute(sessionKey, value);
		return true;
	}

	/**
	 * Fetches a session by a supplied session id
	 * @param sessionId the id to get session by
	 * @return a session or null if no session matching the id is found
	 */
	private Session getSessionBySessionId(String sessionId) {
		if (!StringUtils.hasLength(sessionId)) {
			return null;
		}
		
		return sessionRepository.findById(sessionId);
	}

	/**
	 * Gets a value from a session based on sessionId and sessionKey. It checks that the returned is non-null and an instance of the supplied class
	 * @param clazz please note for base types (like int, long) setAttribute on session will automatically convert to a Integer and Long so those are the classes needed to fetch them.
	 * @return an object that is an instance of the supplied class, otherwise returns null
	 */
	private <T> T get(String sessionId, String sessionKey, Class<T> clazz) {
		Session session = getSessionBySessionId(sessionId);
		if (session == null) {
			return null;
		}
		Object attribute = session.getAttribute(sessionKey);

		if (attribute != null && clazz.isInstance(attribute)) {
			return clazz.cast(attribute);
		}

		return null;
	}
}
