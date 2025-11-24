package dk.digitalidentity.claimsprovider;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.io.UnmarshallingException;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.impl.AssertionUnmarshaller;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.servlet.ModelAndView;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.controller.dto.LoginRequest;
import dk.digitalidentity.samlmodule.model.TokenUser;
import dk.digitalidentity.service.AssertionService;
import dk.digitalidentity.service.ErrorResponseService;
import dk.digitalidentity.service.FlowService;
import dk.digitalidentity.service.LoginService;
import dk.digitalidentity.service.OidcAuthorizationCodeService;
import dk.digitalidentity.service.SessionHelper;
import dk.digitalidentity.service.WSFederationService;
import dk.digitalidentity.service.serviceprovider.ServiceProvider;
import dk.digitalidentity.service.serviceprovider.ServiceProviderFactory;
import dk.digitalidentity.util.Constants;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
public class MitIDService {

	@Autowired
	private HttpServletRequest request;

	@Autowired
	private AuditLogger auditLogger;

	@Autowired
	private SessionHelper sessionHelper;

	@Autowired
	private FlowService flowService;

	@Autowired
	private LoginService loginService;

	@Autowired
	private ErrorResponseService errorResponseService;

	@Autowired
	private ClaimsProviderUtil nemLoginUtil;

	@Autowired
	private ServiceProviderFactory serviceProviderFactory;

	@Autowired
	private AssertionService assertionService;

	@Autowired
	private OidcAuthorizationCodeService oidcAuthorizationCodeService;

	@Autowired
	private WSFederationService wsFederationService;
	
	@Autowired
	private CommonConfiguration commonConfiguration;

	public ModelAndView nemLogInComplete(Model model, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, TokenUser tokenUser) throws RequesterException, ResponderException {
		
		// get LOA
		NSISLevel nsisLevel = NSISLevel.NONE;
		Map<String, Object> attributes = tokenUser.getAttributes();
		if (!attributes.containsKey(Constants.LEVEL_OF_ASSURANCE)) {
			return handleMitIdErrors(httpServletResponse, "Brugertoken fra NemLog-in indeholder ikke et NSIS sikringsniveau!");
		}
		else {
			String loa = (String) attributes.get(Constants.LEVEL_OF_ASSURANCE);

			try {
				nsisLevel = NSISLevel.valueOf(loa.toUpperCase());
			}
			catch (IllegalArgumentException e) {
				return handleMitIdErrors(httpServletResponse, "NSIS sikringsniveau fra login token er ukendt: " + loa);
			}
		}

		// we need a cpr for further validation
		if (nemLoginUtil.getCpr() == null && !(commonConfiguration.getMitIdErhverv().isEnabled() && commonConfiguration.getMitIdErhverv().isAllowMissingCpr())) {
			return handleMitIdErrors(httpServletResponse, "Brugertoken fra NemLog-in indeholder ikke personnummer!");
		}

		// pull all needed data from NemLoginUtil now, as data will be gone from this point on
		String mitIdName = nemLoginUtil.getPersonUuid();
		List<Person> availablePeople = nemLoginUtil.getAvailablePeople();
		String cpr = nemLoginUtil.getCpr();
		String rawToken = tokenUser.getAndClearRawToken();

		// Spring Authorization Server does not play well with a full authenticated Authentication object, and since we do
		// not actually need it from this point on, we can just wipe it and replace it with an AnonymousAuthenticationToken
		// NOTE: after this point, do not access nemLoginUtil.getXXX methods, as they will not work
		if (SecurityContextHolder.getContext() != null &&
			SecurityContextHolder.getContext().getAuthentication() != null) {

			SecurityContext securityContext = SecurityContextHolder.getContext();
			Object principal = securityContext.getAuthentication().getPrincipal();

			ArrayList<GrantedAuthority> authorities = new ArrayList<GrantedAuthority>();
			authorities.add(new SimpleGrantedAuthority("USER"));
			Authentication authentication = new AnonymousAuthenticationToken(tokenUser.getUsername(), principal, authorities);
			SecurityContextHolder.getContext().setAuthentication(authentication);
			
			if (request != null) {
				HttpSession session = request.getSession(true);
				session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
			}
		}
		
		sessionHelper.setAuthenticatedWithNemIdOrMitId(true);

		// if the originating AuthnRequest (from the upstream ServiceProvider) was a forced NemLog-in flow, then
		// we handle the request in a separate method, and ignore the rest of the logic
		if (sessionHelper.isInNemLogInBrokerFlow()) {
			return handleNemLogInAsBroker(httpServletResponse, model, rawToken);
		}

		// in case we end up in a sub-flow, we need to know that we are in the middle of a NemID/MitID login flow
		sessionHelper.setInNemIdOrMitIDAuthenticationFlow(true);

		// store for later use
		sessionHelper.setMitIDNameID(mitIdName);
		sessionHelper.setNemIDMitIDNSISLevel(nsisLevel);
		auditLogger.usedNemLogin(sessionHelper.getPerson(), nsisLevel, rawToken);

		// check if we have a person on the session, if we do we will only work with this person
		Person person = sessionHelper.getPerson();

		// if no existing person is found on the session: treat as fresh login
		if (person == null) {
			// persons locked by 3rd party (municipality, admin or cpr) are filtered out
			availablePeople = availablePeople.stream()
					.filter(p -> !(p.isLockedAdmin() || p.isLockedCivilState() || p.isLockedDataset()))
					.collect(Collectors.toList());

			// if no user accounts can be associated with the CPR-Number we fetched from
			// NemLog-in (meaning the people list is empty) we will have to error
			if (availablePeople.isEmpty()) {
				LoginRequest loginRequest = sessionHelper.getLoginRequest();
				if (loginRequest != null) {
					ServiceProvider serviceProvider = serviceProviderFactory.getServiceProvider(loginRequest);
					if (serviceProvider.isAllowAnonymousUsers()) {
						return handleNemLogInAsBroker(httpServletResponse, model, rawToken);
					}
				}
				
				auditLogger.rejectedUnknownPerson(mitIdName, cpr, null);
				return new ModelAndView("error-unknown-user");
			}

			// handle multiple user accounts
			if (availablePeople.size() != 1) {
				return flowService.initiateUserSelect(model, availablePeople, nsisLevel, sessionHelper.getLoginRequest(), httpServletRequest, httpServletResponse);
			}
			else {
				// if we only have one person use that one
				person = availablePeople.get(0);
			}
		}
		else {
			// an existing person was found on the session, validate CPR-Number from NemLog-in against that persons CPR.
			if (!Objects.equals(cpr, person.getCpr())) {
				LoginRequest loginRequest = sessionHelper.getLoginRequest();
				if (loginRequest == null) {
					return invalidateSessionAndSendRedirect();
				}
				
				errorResponseService.sendError(httpServletResponse, loginRequest, new ResponderException("Bruger foretog MitID login med et andet CPR end der allerede var gemt på sessionen"));

				return null;
			}
		}

		// The specific person has now been determined.
		return loginService.continueLoginWithMitIdOrNemId(person, nsisLevel, sessionHelper.getLoginRequest(), httpServletRequest, httpServletResponse, model);
	}

	private ModelAndView handleNemLogInAsBroker(HttpServletResponse httpServletResponse, Model model, String rawToken) throws ResponderException, RequesterException {
		LoginRequest loginRequest = sessionHelper.getLoginRequest();
		if (loginRequest == null) {
			return handleMitIdErrors(httpServletResponse, "Ingen login forespørgsel på sessionen");
		}
		
		ServiceProvider serviceProvider = serviceProviderFactory.getServiceProvider(loginRequest.getServiceProviderId());

		if (rawToken == null) {
			return handleMitIdErrors(httpServletResponse, "Intet login svar fra NemLog-In");
		}

		Assertion assertion;
		try {
			auditLogger.usedNemLoginBrokering(rawToken);
			assertion = getNemLogInAssertion(rawToken);
		}
		catch (ParserConfigurationException | IOException | SAXException e) {
			return handleMitIdErrors(httpServletResponse, "Kunne ikke læse NemLogIn login svar");
		}
		catch (UnmarshallingException e) {
			return handleMitIdErrors(httpServletResponse, "Kunne ikke afkode NemLogIn login svar");
		}

		switch (serviceProvider.getProtocol()) {
			case SAML20:
				return assertionService.createAndSendBrokeredAssertion(httpServletResponse, loginRequest, assertion);
			case OIDC10:
				return oidcAuthorizationCodeService.createAndSendBrokeredAuthorizationCode(httpServletResponse, assertion, serviceProvider, loginRequest);
			case WSFED:
				return wsFederationService.createAndSendBrokeredSecurityTokenResponse(model, loginRequest, assertion, serviceProvider);
			case ENTRAMFA:
				// TODO: should we support this?
				throw new IllegalStateException("NemLog-in brokering for EntraMFA is not supported");
		}
		
		throw new IllegalStateException("Unexpected value: " + serviceProvider.getProtocol());
	}

	private static Assertion getNemLogInAssertion(String rawToken) throws ParserConfigurationException, SAXException, IOException, UnmarshallingException {
		// Parse raw Assertion String
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		dbf.setNamespaceAware(true);
		dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
		dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		DocumentBuilder db = dbf.newDocumentBuilder();
		Document doc = db.parse(new ByteArrayInputStream(rawToken.getBytes("UTF-8")));

		// Get Assertion Element
		Element documentElement = doc.getDocumentElement();

		// Unmarshall Assertion
		AssertionUnmarshaller unmarshaller = new AssertionUnmarshaller();
		XMLObject xmlObject = unmarshaller.unmarshall(documentElement);
		return (Assertion) xmlObject;
	}

	private ModelAndView invalidateSessionAndSendRedirect() {
		log.warn("No authnRequest found on session, redirecting to index page");
		return new ModelAndView("redirect:/");
	}

	private ModelAndView handleMitIdErrors(HttpServletResponse httpServletResponse, String errMsg) throws ResponderException, RequesterException {
		LoginRequest loginRequest = sessionHelper.getLoginRequest();
		if (loginRequest == null) {
			log.warn(errMsg);
			return invalidateSessionAndSendRedirect();
		}

		errorResponseService.sendError(httpServletResponse, loginRequest, new ResponderException(errMsg));
		return null;
	}
}
