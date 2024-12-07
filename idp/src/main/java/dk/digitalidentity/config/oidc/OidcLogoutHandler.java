package dk.digitalidentity.config.oidc;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.service.SessionHelper;
import dk.digitalidentity.util.ResponderException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Service
public class OidcLogoutHandler extends SimpleUrlLogoutSuccessHandler {
	private final SessionHelper sessionHelper;
	private final AuditLogger auditLogger;

	public OidcLogoutHandler(SessionHelper sessionHelper, AuditLogger auditLogger) {
		this.sessionHelper = sessionHelper;
		this.auditLogger = auditLogger;
	}

	@Override
	public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
		super.setDefaultTargetUrl("/");

		Person person = sessionHelper.getPerson();
		if (person != null) {
			auditLogger.logout(person);
		}

		try {
			sessionHelper.logout(null);
		}
		catch (ResponderException e) {
			throw new RuntimeException(e);
		}

		// Call super method like always
		super.onLogoutSuccess(request, response, authentication);
	}
}
