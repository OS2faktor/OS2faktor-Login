package dk.digitalidentity.controller;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import dk.digitalidentity.service.OidcAuthorizationCodeService;
import dk.digitalidentity.service.WSFederationService;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.io.UnmarshallingException;
import org.opensaml.messaging.context.MessageContext;
import org.opensaml.messaging.encoder.MessageEncodingException;
import org.opensaml.saml.common.SAMLObject;
import org.opensaml.saml.common.binding.SAMLBindingSupport;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.binding.encoding.impl.HTTPPostEncoder;
import org.opensaml.saml.saml2.binding.encoding.impl.HTTPRedirectDeflateEncoder;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.LogoutRequest;
import org.opensaml.saml.saml2.core.LogoutResponse;
import org.opensaml.saml.saml2.core.impl.AssertionUnmarshaller;
import org.opensaml.saml.saml2.metadata.SingleLogoutService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.controller.dto.LoginRequest;
import dk.digitalidentity.nemlogin.NemLoginUtil;
import dk.digitalidentity.samlmodule.model.TokenUser;
import dk.digitalidentity.service.AssertionService;
import dk.digitalidentity.service.ErrorResponseService;
import dk.digitalidentity.service.FlowService;
import dk.digitalidentity.service.LoginService;
import dk.digitalidentity.service.LogoutResponseService;
import dk.digitalidentity.service.OpenSAMLHelperService;
import dk.digitalidentity.service.SessionHelper;
import dk.digitalidentity.service.serviceprovider.ServiceProvider;
import dk.digitalidentity.service.serviceprovider.ServiceProviderFactory;
import dk.digitalidentity.util.Constants;
import dk.digitalidentity.util.LoggingUtil;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import lombok.extern.slf4j.Slf4j;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.velocity.VelocityEngine;

@Slf4j
@Controller
public class MitIDController {

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
	private NemLoginUtil nemLoginUtil;

	@Autowired
	private ServiceProviderFactory serviceProviderFactory;

	@Autowired
	private LogoutResponseService logoutResponseService;

	@Autowired
	private OpenSAMLHelperService samlHelper;

	@Autowired
	private LoggingUtil loggingUtil;

	@Autowired
	private AssertionService assertionService;

	@Autowired
	private OidcAuthorizationCodeService oidcAuthorizationCodeService;

	@Autowired
	private WSFederationService wsFederationService;

	@GetMapping("/sso/saml/nemlogin/complete")
	public ModelAndView nemLogInComplete(Model model, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws RequesterException, ResponderException {
		TokenUser tokenUser = nemLoginUtil.getTokenUser();
		if (tokenUser == null) {
			return handleMitIdErrors(httpServletResponse, "Intet bruger-token modtaget efter afsluttet NemLog-in");
		}

		// get LOA
		NSISLevel nsisLevel = NSISLevel.NONE;
		Map<String, Object> attributes = tokenUser.getAttributes();
		if (!attributes.containsKey(Constants.LEVEL_OF_ASSURANCE)) {
			log.warn("Token from NemLog-in does not contain an NSIS level - setting it to NONE on session");
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

		// if the originating AuthnRequest (from the upstream ServiceProvider) was a forced NemLog-in flow, then
		// we handle the request in a separate method, and ignore the rest of the logic
		if (sessionHelper.isInNemLogInBrokerFlow()) {
			return handleNemLogInAsBroker(httpServletResponse, model, tokenUser);
		}

		// set nameID as an identifier on the session associated with the MitID login.
		String mitIdName = nemLoginUtil.getPersonUuid();

		// in case we end up in a sub-flow, we need to know that we are in the middle of a NemID/MitID login flow
		sessionHelper.setInNemIdOrMitIDAuthenticationFlow(true);

		// store for later use
		sessionHelper.setMitIDNameID(mitIdName);
		sessionHelper.setNemIDMitIDNSISLevel(nsisLevel);

		auditLogger.usedNemLogin(sessionHelper.getPerson(), nsisLevel, tokenUser.getAndClearRawToken());

		// check if we have a person on the session, if we do we will only work with this person
		Person person = sessionHelper.getPerson();

		// if no existing person is found on the session: treat as fresh login
		if (person == null) {
			List<Person> availablePeople = nemLoginUtil.getAvailablePeople();

			// persons locked by 3rd party (municipality, admin or cpr) are filtered out
			availablePeople = availablePeople.stream()
					.filter(p -> !(p.isLockedAdmin() || p.isLockedCivilState() || p.isLockedDataset()))
					.collect(Collectors.toList());

			// if no user accounts can be associated with the CPR-Number we fetched from
			// NemLog-in (meaning the people list is empty) we will have to error
			if (availablePeople.isEmpty()) {				
				auditLogger.rejectedUnknownPerson(mitIdName, nemLoginUtil.getCpr());
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
			if (!Objects.equals(nemLoginUtil.getCpr(), person.getCpr())) {
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

	private ModelAndView handleNemLogInAsBroker(HttpServletResponse httpServletResponse, Model model, TokenUser tokenUser) throws ResponderException, RequesterException {
		LoginRequest loginRequest = sessionHelper.getLoginRequest();
		if (loginRequest == null) {
			return handleMitIdErrors(httpServletResponse, "Ingen login forespørgsel på sessionen");
		}
		ServiceProvider serviceProvider = serviceProviderFactory.getServiceProvider(loginRequest.getServiceProviderId());


		String rawToken = tokenUser.getRawToken();
		if (rawToken == null) {
			return handleMitIdErrors(httpServletResponse, "Intet login svar fra NemLog-In");
		}

		Assertion assertion;
		try {
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
			default:
				throw new IllegalStateException("Unexpected value: " + serviceProvider.getProtocol());
		}
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

	@GetMapping("/sso/saml/nemlogin/logout/complete")
	public String nemLogInLogout(Model model, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws RequesterException, ResponderException {
		// After a successful NemLog-in logout we end up here

		if (sessionHelper.isInInsufficientNSISLevelFromMitIDFlow()) {
			sessionHelper.setInInsufficientNSISLevelFromMitIDFlow(false);
			return "error-mitid-insufficient-nsis-level";
		}

		// If there is no LogoutRequest on session, show IdP index.
		// this happens when logout is IdP initiated
		LogoutRequest logoutRequest = sessionHelper.getLogoutRequest();
		if (logoutRequest == null) {
			auditLogger.logout(sessionHelper.getPerson());
			sessionHelper.invalidateSession();
			return "redirect:/";
		}

		// Create LogoutResponse
		ServiceProvider serviceProvider = serviceProviderFactory.getServiceProvider(logoutRequest.getIssuer().getValue());
		SingleLogoutService logoutEndpoint = serviceProvider.getLogoutResponseEndpoint();

		String destination = StringUtils.hasLength(logoutEndpoint.getResponseLocation()) ? logoutEndpoint.getResponseLocation() : logoutEndpoint.getLocation();
		MessageContext<SAMLObject> messageContext = logoutResponseService.createMessageContextWithLogoutResponse(logoutRequest, destination, logoutEndpoint.getBinding());

		// Log to Console and AuditLog
		auditLogger.logoutResponse(sessionHelper.getPerson(), samlHelper.prettyPrint((LogoutResponse) messageContext.getMessage()), true, serviceProvider.getName(null));
		auditLogger.logout(sessionHelper.getPerson());
		loggingUtil.logLogoutResponse((LogoutResponse) messageContext.getMessage(), Constants.OUTGOING);

		// Set RelayState
		SAMLBindingSupport.setRelayState(messageContext, sessionHelper.getRelayState());

		// Logout Response is sent as the last thing after all LogoutRequests so delete
		// the remaining values
		sessionHelper.invalidateSession();

		// Deflating and sending the message
		try {
			if (SAMLConstants.SAML2_POST_BINDING_URI.equals(logoutEndpoint.getBinding())) {
				HTTPRedirectDeflateEncoder encoder = new HTTPRedirectDeflateEncoder();

				encoder.setMessageContext(messageContext);
				encoder.setHttpServletResponse(httpServletResponse);

				encoder.initialize();
				encoder.encode();
			}
			else if (SAMLConstants.SAML2_REDIRECT_BINDING_URI.equals(logoutEndpoint.getBinding())) {
				HTTPPostEncoder encoder = new HTTPPostEncoder();

				encoder.setHttpServletResponse(httpServletResponse);
				encoder.setMessageContext(messageContext);
				encoder.setVelocityEngine(VelocityEngine.newVelocityEngine());

				encoder.initialize();
				encoder.encode();
			}
		}
		catch (ComponentInitializationException | MessageEncodingException e) {
			throw new ResponderException("Kunne ikke sende logout svar (LogoutResponse)", e);
		}

		return null;
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
