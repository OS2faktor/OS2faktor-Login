package dk.digitalidentity.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseUtil {
	private final String cleanClients = "DELETE FROM os2faktor.clients";
	private final String cleanUsers = "DELETE FROM os2faktor.users";
	private final String cleanNotifications = "DELETE FROM os2faktor.notifications";
	private final String cleanServers = "DELETE FROM os2faktor.servers";
	private final String cleanMunicipalities = "DELETE FROM os2faktor.municipalities";

	private final String createMunicipality = "INSERT INTO os2faktor.municipalities (cvr, api_key, name) VALUES ('12345678', 'Test1234', 'Test Kommune')";
	private final String createServer = "INSERT INTO os2faktor.servers (name, api_key, municipality_id) SELECT 'Test Kommune', 'e0418045-852f-4eb0-84ad-6bd03d95599b', id FROM os2faktor.municipalities WHERE cvr = '12345678'";
	private final String createUser = "INSERT INTO os2faktor.users (ssn, pid) VALUES ('2dxaLrOnS7g3kooVc4cTr2FS4I1BUEuIOuZxgCuf1RACUYNIxRYU80CgDRYa20vc', '9208-2002-2-788425013241')";
	private final String createClient = "INSERT INTO os2faktor.clients (device_id, api_key, client_type, name, user_id) SELECT '123-123-123-123', 'Test1234', 'CHROME', 'MFA', id FROM os2faktor.users WHERE pid = '9208-2002-2-788425013241'";
	
	private final String acceptNotifications = "UPDATE os2faktor.notifications SET client_authenticated = 1";
	private final String deleteMFALevel = "DELETE FROM os2faktor_nsis.SESSION_IDP_ATTRIBUTES WHERE ATTRIBUTE_NAME='MFA_AUTHENTICATION_LEVEL'";

	private static boolean initialized = false;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	public void resetClients() {
		if (!initialized) {
			jdbcTemplate.batchUpdate(cleanClients,
									 cleanUsers,
									 cleanNotifications,
									 cleanServers,
									 cleanMunicipalities,
									 createMunicipality,
									 createServer,
									 createUser,
									 createClient);
			
			initialized = true;
		}
	}
	
	public void acceptNotifications() {
		jdbcTemplate.batchUpdate(acceptNotifications);
	}

	public void deleteMFALevel() {
		jdbcTemplate.batchUpdate(deleteMFALevel);
	}
}
