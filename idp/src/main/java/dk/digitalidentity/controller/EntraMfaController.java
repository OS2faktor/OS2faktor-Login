package dk.digitalidentity.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.controller.dto.EntraIdTokenHint;
import dk.digitalidentity.controller.dto.EntraPayload;
import dk.digitalidentity.controller.dto.LoginRequest;
import dk.digitalidentity.service.FlowService;
import dk.digitalidentity.service.SessionHelper;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
public class EntraMfaController {
	private EntraConfiguration entraConfiguration;

	record EntraConfiguration (
			String authorization_endpoint,
			String[] grant_types_supported,
			String[] id_token_signing_alg_values_supported,
			String issuer,
			String jwks_uri,
			String[] response_modes_supported,
			String[] response_types_supported,
			String[] scopes_supported,
			String[] subject_types_supported) { };

	@Autowired
	private OS2faktorConfiguration os2faktorConfiguration;
	
	@Autowired
	private SessionHelper sessionHelper;

	@Autowired
	private FlowService flowService;
	
	@Autowired
	private PersonService personService;
	
	@Autowired
	private AuditLogger auditLogger;
	
	@Autowired
	private CommonConfiguration commonConfiguration;

	@EventListener(ApplicationReadyEvent.class)
	public void init() throws Exception {
		entraConfiguration = new EntraConfiguration(
				os2faktorConfiguration.getBaseUrl() + "/entraMfa/authorize",
				new String[] { "implicit" },
				new String[] { "RS256" },
				os2faktorConfiguration.getEntityId(),
				os2faktorConfiguration.getBaseUrl() +  "/oauth2/jwks",
				new String[] { "form_post" },
				new String[] { "id_token" },
				new String[] { "openid" },
				new String[] { "public" }
			);
	}

	@ResponseBody
	@GetMapping(path = "/entraMfa/openid-configuration", produces = MediaType.APPLICATION_JSON_VALUE)
	public EntraConfiguration configuration() {
		return entraConfiguration;
	}

	@PostMapping("/entraMfa/authorize")
	public ModelAndView authorize(Model model, HttpServletRequest request, HttpServletResponse response) throws RequesterException, ResponderException {
		Map<String, String[]> parameters = request.getParameterMap();
		String upn = extractTokenHintField("upn", parameters);
		if (upn == null) {
			throw new RequesterException("Der er ikke angivet et UPN i forespørgslen fra EntraID");
		}

		String redirectUrl = extractField("redirect_uri", parameters);
		if (redirectUrl == null) {
			throw new RequesterException("Der er ikke angivet en redirectUrl i forespørgslen fra EntraID");
		}

		if (!commonConfiguration.getEntraMfa().getRedirectUrl().equals(redirectUrl)) {
			throw new RequesterException("Ugyldig redirectUrl: " + redirectUrl);
		}

		List<Person> persons = personService.getByUPN(upn);
		if (persons == null || persons.size() == 0) {
			throw new ResponderException("Der findes ingen brugere med det angivne UPN: " + upn);
		}
		
		if (persons.size() > 1) {
			throw new ResponderException("Der findes mere end én bruger med det angivne UPN: " + upn);
		}

		Person person = persons.get(0);
		Person existingPerson = sessionHelper.getPerson();

		if (existingPerson != null && existingPerson.getId() != person.getId()) {
			log.warn("Clearing existing session, because new person " + person.getId() + " differs from existing person on session " + existingPerson.getId());
			sessionHelper.clearSession();
		}

		EntraPayload payload = new EntraPayload();
		payload.setUpn(upn);
		payload.setRedirectUrl(redirectUrl);
		payload.setNonce(extractField("nonce", parameters));
		payload.setState(extractField("state", parameters));
		payload.setAudience(extractTokenHintField("aud", parameters));
		payload.setSubject(extractTokenHintField("sub", parameters));
		payload.setAcr(extractAcr(parameters));
		
		LoginRequest loginRequest = new LoginRequest(payload, request.getHeader("User-Agent"));

		auditLogger.entraMfaLoginRequest(person, upn, loginRequest.getUserAgent());

		sessionHelper.setInEntraMfaFlow(person, loginRequest);

		return flowService.initiateMFA(model, person, NSISLevel.NONE);
	}

	private String extractField(String field, Map<String, String[]> parameters) {
		try {
			return parameters.get(field)[0];
		}
		catch (Exception ignored) {
			;
		}
		
		return null;
	}

	private String extractAcr(Map<String, String[]> parameters) {
		String claims = extractField("claims", parameters);
		if (claims != null) {
			try {
				ObjectMapper mapper = new ObjectMapper();
				EntraIdTokenHint hint = mapper.readValue(claims, EntraIdTokenHint.class);

				return hint.getAcr().getValues().get(0);
			}
			catch (Exception ex) {
				log.warn("Failed to parse claims: " + claims, ex);
			}
		}
		
		return null;
	}

	// bit hackish, but we just need the upn claim from the token hint
	private String extractTokenHintField(String field, Map<String, String[]> parameters) {
		String tokenHint = null;

		try {
			tokenHint = parameters.get("id_token_hint")[0];
			
			JWT jwt = JWTParser.parse(tokenHint);
			
			if ("aud".equals(field)) {
				return jwt.getJWTClaimsSet().getAudience().get(0);
			}
			else if ("acr".equals(field)) {
				
			}
	
			return (String) jwt.getJWTClaimsSet().getClaim(field);
		}
		catch (Exception ex) {
			log.error("Unable to extract " + field + " from id_token_hint: " + tokenHint, ex);
		}
		
		return null;
	}
}
