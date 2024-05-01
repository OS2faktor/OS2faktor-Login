package dk.digitalidentity.controller;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.TemporaryClientSessionKey;
import dk.digitalidentity.common.dao.model.mapping.TemporaryClientSessionMapping;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.TemporaryClientSessionKeyService;
import dk.digitalidentity.common.service.TemporaryClientSessionMappingService;
import dk.digitalidentity.service.SessionHelper;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.Objects;

@Slf4j
@Controller
public class WindowsClientTokenLoginController {
	private static final String AUTO_CLOSING_PAGE = "clientLoginSuccess";

	@Autowired
	private SessionHelper sessionHelper;

	@Autowired
	private TemporaryClientSessionKeyService temporaryClientSessionKeyService;

	@Autowired
	private TemporaryClientSessionMappingService sessionMappingService;

	@Autowired
	private AuditLogger auditLogger;

	@GetMapping("/sso/saml/client/login")
	public String continueClientLogin(@RequestParam("sessionKey") String sessionKey, @RequestParam(value = "info", required = false, defaultValue = "") String info, @RequestParam(value = "version", required = false, defaultValue = "") String version, HttpServletRequest request) {
		// No real reason to have meaningful error messages on this endpoint.
		// It is only hit by headless programs running to establish sessions for users using the windows login credential provider

		if (!StringUtils.hasLength(sessionKey)) {
			return AUTO_CLOSING_PAGE;
		}

		TemporaryClientSessionKey temporaryClient = temporaryClientSessionKeyService.getBySessionKey(sessionKey);
		if (temporaryClient == null) {
			log.warn("Could not find temporaryClient");
			return AUTO_CLOSING_PAGE;
		}

		// The temporaryClient should have been created at most 5 minutes before calling this endpoint
		if (LocalDateTime.now().minusMinutes(5).isAfter(temporaryClient.getTts())) {
			log.warn("Client asked for session using expired key");
			return AUTO_CLOSING_PAGE;
		}

		Person sessionPerson = sessionHelper.getPerson();
		if (sessionPerson != null && !Objects.equals(sessionPerson.getId(), temporaryClient.getPerson().getId())) {
			log.warn("sessionHelper.getPerson() was non-null AND did not match the person determined when issuing sessionKey");
			return AUTO_CLOSING_PAGE;
		}

		String temporaryClientIPAddress = temporaryClient.getIpAddress();
		String requestIPAddress = temporaryClientSessionKeyService.getIpAddressFromRequest(request);
		if (StringUtils.hasLength(requestIPAddress)) {
			if (requestIPAddress.equals(temporaryClientIPAddress)) {
				// Since the IP used to generate the sessionkey (A successful login) matches the ip now used to establish the session from the sessionkey
				// We update the IP address on this session to this IP.
				// This allows computers who have previously established a session on one IP and then changed IP, but verified themselves during Windows login to keep their session
				sessionHelper.setIPAddress(requestIPAddress);
			}
			else {
				log.warn("Client tried to get session using sessionKey that was generated with a different IP");
				auditLogger.sessionNotIssuedIPChanged(temporaryClient, info);
				return AUTO_CLOSING_PAGE;
			}
		}

		// Everything checks out, and we can set the person and the NSIS-Level on the session.
		// Note this will not lower their level if they have already established a higher NSIS level.

		// Remember spring session to token mapping, so we can refresh the session later on
		HttpSession session = sessionHelper.getServletRequest().getSession();
		sessionMappingService.save(new TemporaryClientSessionMapping(temporaryClient, session.getId()));

		String userAgent = "";
		if (request != null) {
			String header = request.getHeader("User-Agent");
			if (StringUtils.hasLength(header)) {
				userAgent = header;
			}
		}

		auditLogger.exchangeTemporaryTokenForSession(temporaryClient, info, userAgent, version);

		sessionHelper.setPasswordLevel(temporaryClient.getNsisLevel());
		sessionHelper.setAuthnInstant(DateTime.now());
		sessionHelper.setPerson(temporaryClient.getPerson());

		return AUTO_CLOSING_PAGE;
	}
}
