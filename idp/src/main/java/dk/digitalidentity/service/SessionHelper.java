package dk.digitalidentity.service;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.joda.time.DateTime;
import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.core.xml.io.UnmarshallingException;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.LogoutRequest;
import org.opensaml.saml.saml2.core.impl.AuthnRequestMarshaller;
import org.opensaml.saml.saml2.core.impl.AuthnRequestUnmarshaller;
import org.opensaml.saml.saml2.core.impl.LogoutRequestMarshaller;
import org.opensaml.saml.saml2.core.impl.LogoutRequestUnmarshaller;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.w3c.dom.Element;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.SessionSetting;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.KnownNetworkService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.SessionSettingService;
import dk.digitalidentity.common.service.enums.ChangePasswordResult;
import dk.digitalidentity.common.service.mfa.model.MfaClient;
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.controller.dto.ClaimValueDTO;
import dk.digitalidentity.controller.dto.LoginRequest;
import dk.digitalidentity.controller.dto.LoginRequestDTO;
import dk.digitalidentity.samlmodule.util.SessionConstant;
import dk.digitalidentity.service.serviceprovider.ServiceProvider;
import dk.digitalidentity.util.Constants;
import dk.digitalidentity.util.ResponderException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class SessionHelper {
	private SecretKeySpec secretKey;

	@Autowired
	private PersonService personService;

	@Autowired
	private OS2faktorConfiguration os2faktorConfiguration;

	@Autowired
	private SessionSettingService sessionService;
	
	@Autowired
	private AuditLogger auditLogger;
	
	@Autowired
	private CommonConfiguration commonConfiguration;

	@Autowired
	private SessionSettingService sessionSettingService;
	
	@Autowired
	private KnownNetworkService knownNetworkService;
	
	public NSISLevel getLoginState() {
		return getLoginState(null, null);
	}

	public NSISLevel getLoginState(ServiceProvider serviceProvider, LoginRequest loginRequest) {
		Person person = getPerson();
		if (person == null) {
			return null;
		}

		NSISLevel passwordLevel = getPasswordLevel(serviceProvider, loginRequest);
		NSISLevel mfaLevel = getMFALevel(serviceProvider, loginRequest);
		NSISLevel personLevel = person.getNsisLevel();

		log.debug("passwordLevel = " + passwordLevel);
		log.debug("mfaLevel = " + mfaLevel);
		log.debug("personLevel = " + personLevel);

		if (NSISLevel.HIGH.equalOrLesser(personLevel) && NSISLevel.HIGH.equalOrLesser(passwordLevel) && NSISLevel.HIGH.equalOrLesser(mfaLevel)) {
			log.debug("LoginState evaluated to HIGH");
			return NSISLevel.HIGH;
		}

		if (NSISLevel.SUBSTANTIAL.equalOrLesser(personLevel) && NSISLevel.SUBSTANTIAL.equalOrLesser(passwordLevel) && NSISLevel.SUBSTANTIAL.equalOrLesser(mfaLevel)) {
			log.debug("LoginState evaluated to SUBSTANTIAL");
			return NSISLevel.SUBSTANTIAL;
		}

		// Does not need any mfa to verify for low
		if (NSISLevel.LOW.equalOrLesser(personLevel) && NSISLevel.LOW.equalOrLesser(passwordLevel)) {
			log.debug("LoginState evaluated to LOW");
			return NSISLevel.LOW;
		}

		if (NSISLevel.NONE.equalOrLesser(personLevel) && NSISLevel.NONE.equalOrLesser(passwordLevel)) {
			log.debug("LoginState evaluated to NONE");
			return NSISLevel.NONE;
		}

		log.debug("LoginState evaluated to null");
		return null;
	}

	public NSISLevel getPasswordLevel() {
		return getPasswordLevel(null, null);
	}

	public NSISLevel getPasswordLevel(ServiceProvider serviceProvider, LoginRequest loginRequest) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return null;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.PASSWORD_AUTHENTIFICATION_LEVEL);
		LocalDateTime timestamp = getPasswordLevelTimestamp();
		Person person = getPerson();

		if (attribute != null && timestamp != null && person != null) {
			Long passwordExpiry = getSessionLifetimePassword(person, serviceProvider, loginRequest);

			if (LocalDateTime.now().minusMinutes(passwordExpiry).isAfter(timestamp)) {
				auditLogger.sessionExpired(person);
				setPasswordLevelTimestamp(null);
				setPasswordLevel(null);
				return null;
			}

			return (NSISLevel) attribute;
		}

		return null;
	}

	// Will only elevate permissions or delete them
	public void setPasswordLevel(NSISLevel nsisLevel) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		// when password level is set, we blank mfa.level, as MFA authentication comes AFTER pwd-auth,
		// and when NULL'ing password-level, we should also NULL mfa-level
		setMFALevel(null);

		if (nsisLevel == null) {
			httpServletRequest.getSession().setAttribute(Constants.PASSWORD_AUTHENTIFICATION_LEVEL, null);
			setPasswordLevelTimestamp(null);

			return;
		}

		NSISLevel passwordLevel = getPasswordLevel();
		if (passwordLevel == null || !passwordLevel.isGreater(nsisLevel)) {
			httpServletRequest.getSession().setAttribute(Constants.PASSWORD_AUTHENTIFICATION_LEVEL, nsisLevel);
			setPasswordLevelTimestamp(LocalDateTime.now());
		}

		if (log.isDebugEnabled()) {
			log.debug("SetPasswordLevel: was=" + (passwordLevel != null ? passwordLevel : "<null>") + " now=" + nsisLevel);
		}
	}

	public LocalDateTime getPasswordLevelTimestamp() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return null;
		}

		Object timestamp = httpServletRequest.getSession().getAttribute(Constants.PASSWORD_AUTHENTIFICATION_LEVEL_TIMESTAMP);
		if (timestamp != null) {
			return (LocalDateTime) timestamp;
		}

		return null;
	}

	private void setPasswordLevelTimestamp(LocalDateTime timestamp) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		if (timestamp == null) {
			httpServletRequest.getSession().setAttribute(Constants.PASSWORD_AUTHENTIFICATION_LEVEL_TIMESTAMP, null);
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.PASSWORD_AUTHENTIFICATION_LEVEL_TIMESTAMP, timestamp);
	}

	public NSISLevel getMFALevel() {
		return getMFALevel(null, null);
	}

	public NSISLevel getMFALevel(ServiceProvider serviceProvider, LoginRequest loginRequest) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return null;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.MFA_AUTHENTICATION_LEVEL);
		LocalDateTime timestamp = getMFALevelTimestamp();
		Person person = getPerson();

		if (attribute != null && timestamp != null && person != null) {
			long mfaExpiry = getSessionLifetimeMfa(person, serviceProvider, loginRequest);

			if (LocalDateTime.now().minusMinutes(mfaExpiry).isAfter(timestamp)) {
				setMFALevelTimestamp(null);
				setMFALevel(null);
				return null;
			}

			return (NSISLevel) attribute;
		}

		return null;
	}
	
	public boolean hasUsedMFA() {
		if (getMFALevel() != null) {
			return true;
		}
		return false;
	}

	public void setNemIDMitIDNSISLevel(NSISLevel nsisLevel) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		if (nsisLevel == null) {
			httpServletRequest.getSession().removeAttribute(Constants.NEMID_MITID_AUTHENTICATION_LEVEL);
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.NEMID_MITID_AUTHENTICATION_LEVEL, nsisLevel);

		if (log.isDebugEnabled()) {
			log.debug("setNemIDMitIDNSISLevel: " + nsisLevel);
		}
	}
	
	public NSISLevel getNemIDMitIDNSISLevel() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return null;
		}

		Object level = httpServletRequest.getSession().getAttribute(Constants.NEMID_MITID_AUTHENTICATION_LEVEL);
		if (level != null) {
			return (NSISLevel) level;
		}

		return null;
	}
	
	// Will only elevate permissions or delete them
	public void setMFALevel(NSISLevel nsisLevel) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		if (nsisLevel == null) {
			httpServletRequest.getSession().removeAttribute(Constants.MFA_AUTHENTICATION_LEVEL);
			setMFALevelTimestamp(null);
			return;
		}

		NSISLevel mfaLevel = getMFALevel();
		if (mfaLevel == null || !mfaLevel.isGreater(nsisLevel)) {
			httpServletRequest.getSession().setAttribute(Constants.MFA_AUTHENTICATION_LEVEL, nsisLevel);
			LocalDateTime now = LocalDateTime.now();
			setMFALevelTimestamp(now);
			setPasswordLevelTimestamp(now);
		}

		if (log.isDebugEnabled()) {
			log.debug("SetMFALevel: was=" + (mfaLevel != null ? mfaLevel : "<null>") + " now=" + nsisLevel);
		}
	}

	public LocalDateTime getMFALevelTimestamp() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return null;
		}

		Object timestamp = httpServletRequest.getSession().getAttribute(Constants.MFA_AUTHENTICATION_LEVEL_TIMESTAMP);
		if (timestamp != null) {
			return (LocalDateTime) timestamp;
		}

		return null;
	}

	private void setMFALevelTimestamp(LocalDateTime timestamp) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		if (timestamp == null) {
			httpServletRequest.getSession().setAttribute(Constants.MFA_AUTHENTICATION_LEVEL_TIMESTAMP, null);
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.MFA_AUTHENTICATION_LEVEL_TIMESTAMP, timestamp);
	}

	public LogoutRequest getLogoutRequest() throws ResponderException {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return null;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.LOGOUT_REQUEST);

		if (attribute == null) {
			return null;
		}

		try {
			Element marshalledLogoutRequest = (Element) attribute;
			return (LogoutRequest) new LogoutRequestUnmarshaller().unmarshall(marshalledLogoutRequest);
		} catch (UnmarshallingException ex) {
			throw new ResponderException("Kunne ikke afkode logout forespørgsel (LogoutRequest)", ex);
		}
	}

	public void setLogoutRequest(LogoutRequest logoutRequest) throws ResponderException {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		if (logoutRequest == null) {
			httpServletRequest.getSession().setAttribute(Constants.LOGOUT_REQUEST, null);
			return;
		}

		try {
			Element marshall = new LogoutRequestMarshaller().marshall(logoutRequest);
			httpServletRequest.getSession().setAttribute(Constants.LOGOUT_REQUEST, marshall);
		} catch (MarshallingException ex) {
			throw new ResponderException("Kunne ikke omforme logout forespørgsel (LogoutRequest)", ex);
		}
	}

	@SuppressWarnings("unchecked")
	public Map<String, Map<String, String>> getServiceProviderSessions() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return null;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.SERVICE_PROVIDER);
		if (attribute != null) {
			return (Map<String, Map<String, String>>) attribute;
		} else {
			return new HashMap<>();
		}
	}

	public void addServiceProviderSession(final ServiceProvider serviceProvider) {
		final Map<String, Map<String, String>> spSessions = getServiceProviderSessions();
		spSessions.put(serviceProvider.getEntityId(), new HashMap<>());
		setServiceProviderSessions(spSessions);
	}

	public void removeServiceProviderSession(final ServiceProvider serviceProvider) {
		final Map<String, Map<String, String>> spSessions = getServiceProviderSessions();
		spSessions.remove(serviceProvider.getEntityId());

		setServiceProviderSessions(spSessions);
	}

	public void setServiceProviderSessions(Map<String, Map<String, String>> serviceProviders) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.SERVICE_PROVIDER, serviceProviders);
	}

	public void setIPAddress(String ipAddress) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.IP_ADDRESS, ipAddress);
	}

	public boolean handleValidateIP() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return true;
		}

		// Get current IP
		String remoteAddr = httpServletRequest.getHeader("X-FORWARDED-FOR");
		if (remoteAddr == null || "".equals(remoteAddr)) {
			remoteAddr = httpServletRequest.getRemoteAddr();
		}

		// Get stored IP
		Object attribute = httpServletRequest.getSession().getAttribute(Constants.IP_ADDRESS);	

		// Validate IP against one stored on session
		if (attribute == null) {
			// If nothing stored on session save remoteAddr on session
			httpServletRequest.getSession().setAttribute(Constants.IP_ADDRESS, remoteAddr);
			return true;
		}
		else {
			final String finalAddr = remoteAddr;

			if (Objects.equals((String) attribute, remoteAddr)) {
				return true;
			}
			else if (knownNetworkService.getAllIPs().stream().map(IpAddressMatcher::new).toList().stream().anyMatch(ip -> ip.matches(finalAddr))) {
				//checks if new IP is in knownNetwork and passes if so
				return true;
			}
			else {
				auditLogger.logoutCausedByIPChange(getPerson());

				// IP on session and from current request is not the same, so force the user to reauthenticate
				setPerson(null);
				setMFALevel(null);
				setPasswordLevel(null);
				
				httpServletRequest.getSession().setAttribute(Constants.IP_ADDRESS, remoteAddr);
				
				return false;
			}
		}
	}

	private AuthnRequest getAuthnRequest() throws ResponderException {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return null;
		}

		HttpSession session = httpServletRequest.getSession();
		Object attribute = session.getAttribute(Constants.AUTHN_REQUEST);
		if (attribute == null) {
			return null;
		}

		try {
			Element marshalledAuthnRequest = (Element) attribute;
			return (AuthnRequest) new AuthnRequestUnmarshaller().unmarshall(marshalledAuthnRequest);
		}
		catch (UnmarshallingException ex) {
			throw new ResponderException("Kunne ikke afkode login forespørgsel, Fejl url ikke kendt", ex);
		}
	}

	private void setAuthnRequest(AuthnRequest authnRequest) throws ResponderException {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		if (authnRequest == null) {
			httpServletRequest.getSession().setAttribute(Constants.AUTHN_REQUEST, null);
			return;
		}

		try {
			Element marshall = new AuthnRequestMarshaller().marshall(authnRequest);
			httpServletRequest.getSession().setAttribute(Constants.AUTHN_REQUEST, marshall);
		}
		catch (MarshallingException ex) {
			throw new ResponderException("Kunne ikke omforme login forespørgsel (AuthnRequest)", ex);
		}
	}

	public void setLoginRequest(LoginRequest loginRequest) throws ResponderException {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		if (loginRequest == null) {
			httpServletRequest.getSession().setAttribute(Constants.LOGIN_REQUEST, null);
			setAuthnRequest(null);
			return;
		}
		
		// TODO maybe refactor this at some point, the LoginRequest/LoginRequestDTO is an inelegant solution
		setAuthnRequest(loginRequest.getAuthnRequest());
		setRelayState(loginRequest.getRelayState());
		httpServletRequest.getSession().setAttribute(Constants.LOGIN_REQUEST, new LoginRequestDTO(loginRequest));
	}

	public LoginRequest getLoginRequest() throws ResponderException {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return null;
		}

		Object loginRequestObj = httpServletRequest.getSession().getAttribute(Constants.LOGIN_REQUEST);
		if (loginRequestObj == null) {
			return null;
		}
		
		return new LoginRequest((LoginRequestDTO) loginRequestObj, getAuthnRequest(), httpServletRequest.getHeader("User-Agent"));
	}

	public String getRelayState() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return null;
		}

		return (String) httpServletRequest.getSession().getAttribute(Constants.RELAY_STATE);
	}

	public void setRelayState(String relayState) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.RELAY_STATE, relayState);
	}

	public DateTime getAuthnInstant() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return null;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.AUTHN_INSTANT);
		return (attribute != null ? (DateTime) attribute : null);
	}

	public void setAuthnInstant(DateTime dateTime) {
		HttpServletRequest httpServletRequest = getServletRequest();

		if (httpServletRequest != null) {
			httpServletRequest.getSession().setAttribute(Constants.AUTHN_INSTANT, dateTime);
		}
	}

	public Person getPerson() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return null;
		}

		Object attribute = (isInEntraMfaFlow())
				? httpServletRequest.getSession().getAttribute(Constants.ENTRAID_MFA_PERSON)
				: httpServletRequest.getSession().getAttribute(Constants.PERSON_ID);

		if (attribute != null) {
			long personId = (long) attribute;
			return personService.getById(personId);
		}

		return null;
	}

	public void setPerson(Person person) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.PERSON_ID, person == null ? null : person.getId());
	}

	@SuppressWarnings("unchecked")
	public List<MfaClient> getMFAClients() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return null;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.MFA_CLIENTS);
		if (attribute != null) {
			try {
				if (attribute instanceof MfaClient) {
					return Collections.singletonList((MfaClient) attribute);
				}

				return (List<MfaClient>) attribute;
			}
			catch (Exception ex) {
				log.error("Could not cast what was stored in the session as a List<MfaClient>", ex);

				Person person = getPerson();
				log.warn("Class: " + attribute.getClass() + ", Person on session:" + (person != null ? person.getUuid() : "<null>"));
			}
		}
		return null;
	}

	public void setMFAClients(List<MfaClient> mfaDevices) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.MFA_CLIENTS, mfaDevices);
	}

	public NSISLevel getMFAClientRequiredNSISLevel() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return null;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.MFA_CLIENT_REQUIRED_NSIS_LEVEL);
		if (attribute != null) {
			return (NSISLevel) attribute;
		}

		return null;
	}

	public void setMFAClientRequiredNSISLevel(NSISLevel nsisLevel) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		if (nsisLevel == null) {
			httpServletRequest.getSession().setAttribute(Constants.MFA_CLIENT_REQUIRED_NSIS_LEVEL, null);
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.MFA_CLIENT_REQUIRED_NSIS_LEVEL, nsisLevel);
	}

	public MfaClient getSelectedMFAClient() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return null;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.MFA_SELECTED_CLIENT);
		if (attribute != null) {
			return (MfaClient) attribute;
		}
		return null;
	}

	public void setSelectedMFAClient(MfaClient mfaClient) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.MFA_SELECTED_CLIENT, mfaClient);
	}

	public String getSubscriptionKey() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return null;
		}

		return (String) httpServletRequest.getSession().getAttribute(Constants.SUBSCRIPTION_KEY);
	}

	public void setSubscriptionKey(String subscriptionKey) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.SUBSCRIPTION_KEY, subscriptionKey);
	}

	public boolean isAuthenticatedWithADPassword() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return false;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.AUTHENTICATED_WITH_AD_PASSWORD);
		return (boolean) (attribute != null ? attribute : false);
	}

	public void setAuthenticatedWithADPassword(boolean b) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.AUTHENTICATED_WITH_AD_PASSWORD, b);
	}
	
	public boolean isDoNotUseCurrentADPassword() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return false;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.DO_NOT_USE_CURRENT_AD_PASSWORD);
		return (boolean) (attribute != null ? attribute : false);
	}

	public void setDoNotUseCurrentADPassword(boolean b) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			log.warn("Unable to find servletRequest in setDoNotUseCurrentADPassword = " + b);
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.DO_NOT_USE_CURRENT_AD_PASSWORD, b);
	}

	public boolean isAuthenticatedWithNemIdOrMitId() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return false;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.AUTHENTICATED_WITH_NEMID_OR_MITID);
		return (boolean) (attribute != null ? attribute : false);
	}

	public void setAuthenticatedWithNemIdOrMitId(Boolean b) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.AUTHENTICATED_WITH_NEMID_OR_MITID, b);
	}

	public boolean isInNemIdOrMitIDAuthenticationFlow() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return false;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.NEMID_OR_MITID_AUTHENTICATION_FLOW);
		return (boolean) (attribute != null ? attribute : false);
	}

	public void setInNemIdOrMitIDAuthenticationFlow(Boolean b) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.NEMID_OR_MITID_AUTHENTICATION_FLOW, b);
	}

	public String getPassword() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return null;
		}

		String encryptedPassword = (String) httpServletRequest.getSession().getAttribute(Constants.PASSWORD);
		return decryptString(encryptedPassword);
	}

	public void setPassword(String password) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		if (password == null) {
			httpServletRequest.getSession().setAttribute(Constants.PASSWORD, null);
			return;
		}
		httpServletRequest.getSession().setAttribute(Constants.PASSWORD, encryptString(password));
	}

	private SecretKeySpec getKey(String myKey) {
		if (secretKey != null) {
			return secretKey;
		}

		byte[] key;
		MessageDigest sha = null;
		try {
			key = myKey.getBytes("UTF-8");
			sha = MessageDigest.getInstance("SHA-1");
			key = sha.digest(key);
			key = Arrays.copyOf(key, 16);
			secretKey = new SecretKeySpec(key, "AES");
		} catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
			log.error("Error in generating key", e);
		}

		return secretKey;
	}

	private String decryptString(String encryptedString) {
		if (!StringUtils.hasLength(encryptedString)) {
			return null;
		}

		try {
			SecretKeySpec key = getKey(os2faktorConfiguration.getPassword().getSecret());
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(128, new byte[]{0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00});
			cipher.init(Cipher.DECRYPT_MODE, key, gcmParameterSpec);
			return new String(cipher.doFinal(Base64.getDecoder().decode(encryptedString)));
		} catch (Exception e) {
			log.error("Error while decrypting string", e);
		}
		return null;
	}

	private String encryptString(String rawString) {
		if (!StringUtils.hasLength(rawString)) {
			return null;
		}

		try {
			SecretKeySpec key = getKey(os2faktorConfiguration.getPassword().getSecret());
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(128, new byte[]{0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00});
			cipher.init(Cipher.ENCRYPT_MODE, key, gcmParameterSpec);
			return Base64.getEncoder().encodeToString(cipher.doFinal(rawString.getBytes("UTF-8")));
		} catch (Exception e) {
			log.error("Error while encrypting string", e);
			throw new RuntimeException(e);
		}
	}

	public String getRequestedUsername() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return null;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.REQUESTED_USERNAME);
		if (attribute == null) {
			return null;
		}

		return (String) attribute;
	}

	public void setRequestedUsername(String requestedUsername) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.REQUESTED_USERNAME, requestedUsername);
	}

	public String getMitIDNameID() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return null;
		}

		return (String) httpServletRequest.getSession().getAttribute(Constants.MIT_ID_NAME_ID);
	}

	public void setMitIDNameID(String nameId) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.MIT_ID_NAME_ID, nameId);
	}

	public Person getADPerson() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return null;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.AD_PERSON_ID);

		if (attribute != null) {
			long personId = (long) attribute;
			return personService.getById(personId);
		}
		return null;
	}

	public void setADPerson(Person person) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.AD_PERSON_ID, person == null ? null : person.getId());
	}

	@SuppressWarnings("unchecked")
	public List<Person> getAvailablePeople() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return null;
		}

		List<Long> attribute = (List<Long>) httpServletRequest.getSession().getAttribute(Constants.AVAILABLE_PEOPLE);
		if (attribute == null) {
			return new ArrayList<>();
		}

		return attribute.stream().map(l -> personService.getById(l)).collect(Collectors.toList());
	}

	public void setAvailablePeople(List<Person> people) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		List<Long> peopleIds = people.stream().map(Person::getId).collect(Collectors.toList());
		httpServletRequest.getSession().setAttribute(Constants.AVAILABLE_PEOPLE, peopleIds);
	}

	public boolean isInDedicatedActivateAccountFlow() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return false;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.DEDICATED_ACTIVATE_ACCOUNT_FLOW);
		return (boolean) (attribute != null ? attribute : false);
	}

	public void setInDedicatedActivateAccountFlow(Boolean b) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.DEDICATED_ACTIVATE_ACCOUNT_FLOW, b);

		if (log.isDebugEnabled()) {
			log.debug("InDedicatedActivateAccountFlow: " + b);
		}
	}

	public boolean isInNemLogInBrokerFlow() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return false;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.NEM_LOG_IN_BROKER_FLOW);
		return (boolean) (attribute != null ? attribute : false);
	}

	public void setInNemLogInBrokerFlow(Boolean b) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.NEM_LOG_IN_BROKER_FLOW, b);

		if (log.isDebugEnabled()) {
			log.debug("InNemLogInBrokerFlow: " + b);
		}
	}

	public boolean isInChoosePasswordResetOrUnlockAccountFlow() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return false;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.CHOOSE_PASSWORD_RESET_OR_UNLOCK_ACCOUNT_FLOW);
		return (boolean) (attribute != null ? attribute : false);
	}
	
	public void setInChoosePasswordResetOrUnlockAccountFlow(Boolean b) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.CHOOSE_PASSWORD_RESET_OR_UNLOCK_ACCOUNT_FLOW, b);

		if (log.isDebugEnabled()) {
			log.debug("InChoosePasswordResetOrUnlockAccountFlow: " + b);
		}
	}
	
	public boolean isInChangePasswordFlowAndHasNotApprovedConditions() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return false;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.PASSWORD_CHANGE_NOT_APPROVED_CONDITIONS);
		return (boolean) (attribute != null ? attribute : false);
	}

	public void setInChangePasswordFlowAndHasNotApprovedConditions(Boolean b) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.PASSWORD_CHANGE_NOT_APPROVED_CONDITIONS, b);

		if (log.isDebugEnabled()) {
			log.debug("InChangePasswordFlowAndHasNotApprovedConditions: " + b);
		}
	}

	public boolean isInInsufficientNSISLevelFromMitIDFlow() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return false;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.INSUFFICIENT_NSIS_LEVEL_FROM_MIT_ID);
		return (boolean) (attribute != null ? attribute : false);
	}

	public void setInInsufficientNSISLevelFromMitIDFlow(boolean b) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.INSUFFICIENT_NSIS_LEVEL_FROM_MIT_ID, b);

		if (log.isDebugEnabled()) {
			log.debug("InDedicatedActivateAccountFlow: " + b);
		}
	}

	public void setInActivateAccountFlow(boolean b) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.ACTIVATE_ACCOUNT_FLOW, b);

		if (log.isDebugEnabled()) {
			log.debug("InActivateAccountFlow: " + b);
		}
	}

	public boolean isInActivateAccountFlow() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return false;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.ACTIVATE_ACCOUNT_FLOW);
		return (boolean) (attribute != null ? attribute : false);
	}

	public void setInApproveConditionsFlow(boolean inApproveConditionsFlow) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.APPROVE_CONDITIONS_FLOW, inApproveConditionsFlow);

		if (log.isDebugEnabled()) {
			log.debug("InApproveConditionsFlow: " + inApproveConditionsFlow);
		}
	}

	public boolean isInApproveConditionsFlow() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return false;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.APPROVE_CONDITIONS_FLOW);
		return (boolean) (attribute != null ? attribute : false);
	}

	public void setInPasswordChangeFlow(boolean inPasswordChangeFlow) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.PASSWORD_CHANGE_FLOW, inPasswordChangeFlow);

		if (log.isDebugEnabled()) {
			log.debug("InPasswordChangeFlow: " + inPasswordChangeFlow);
		}
	}

	public boolean isInPasswordChangeFlow() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return false;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.PASSWORD_CHANGE_FLOW);
		return (boolean) (attribute != null ? attribute : false);
	}
	
	public void setRequestProfessionalProfile() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(SessionConstant.PROFESSIONAL_PROFILE_ENABLED.getKey(), true);

		if (log.isDebugEnabled()) {
			log.debug("requestProfessionalProfile: true");
		}
	}

	public void setRequestPersonalProfile() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(SessionConstant.PERSONAL_PROFILE_ENABLED.getKey(), true);

		if (log.isDebugEnabled()) {
			log.debug("requestPersonalProfile: true");
		}
	}
	
	public void setInPasswordExpiryFlow(boolean inPasswordExpiryFlow) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.PASSWORD_EXPIRY_FLOW, inPasswordExpiryFlow);

		if (log.isDebugEnabled()) {
			log.debug("InPasswordExpiryFlow: " + inPasswordExpiryFlow);
		}
	}

	public boolean isInPasswordExpiryFlow() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return false;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.PASSWORD_EXPIRY_FLOW);
		return (boolean) (attribute != null ? attribute : false);
	}

	public void setInForceChangePasswordFlow(boolean inForceChangePasswordFlow) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.PASSWORD_FORCE_CHANGE_FLOW, inForceChangePasswordFlow);

		if (log.isDebugEnabled()) {
			log.debug("InForceChangePasswordFlow: " + inForceChangePasswordFlow);
		}
	}

	public boolean isInForceChangePasswordFlow() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return false;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.PASSWORD_FORCE_CHANGE_FLOW);
		return (boolean) (attribute != null ? attribute : false);
	}

	public void setInSelectClaimsFlow(boolean inSelectClaimsFlow) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.LOGIN_SELECT_CLAIMS_FLOW, inSelectClaimsFlow);

		if (log.isDebugEnabled()) {
			log.debug("inSelectClaimsFlow: " + inSelectClaimsFlow);
		}
	}

	public boolean isInSelectClaimsFlow() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return false;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.LOGIN_SELECT_CLAIMS_FLOW);
		return (boolean) (attribute != null ? attribute : false);
	}

	public void setDeclineUserActivation(boolean declineUserActivation) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.DECLINE_USER_ACTIVATION, declineUserActivation);

		if (log.isDebugEnabled()) {
			log.debug("DeclineUserActivation: " + declineUserActivation);
		}
	}

	public boolean isDeclineUserActivation() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return false;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.DECLINE_USER_ACTIVATION);
		return (boolean) (attribute != null ? attribute : false);
	}

	public String getPasswordChangeSuccessRedirect() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return null;
		}

		Object redirectUrl = httpServletRequest.getSession().getAttribute(Constants.PASSWORD_CHANGE_SUCCESS_REDIRECT);
		if (redirectUrl == null) {
			return null;
		}

		return (String) redirectUrl;
	}

	public void setPasswordChangeSuccessRedirect(String redirectUrl) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.PASSWORD_CHANGE_SUCCESS_REDIRECT, redirectUrl);
	}
	
	public ChangePasswordResult getPasswordChangeFailureReason() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return null;
		}

		Object passwordChangeFailureReason = httpServletRequest.getSession().getAttribute(Constants.PASSWORD_CHANGE_FAILURE_REASON);
		if (passwordChangeFailureReason == null) {
			return null;
		}

		return (ChangePasswordResult) passwordChangeFailureReason;
	}

	public void setPasswordChangeFailureReason(ChangePasswordResult passwordChangeFailureReason) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.PASSWORD_CHANGE_FAILURE_REASON, passwordChangeFailureReason);
	}

	public void setSelectableClaims(Map<String, ClaimValueDTO> selectableClaims) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		if (selectableClaims == null) {
			httpServletRequest.getSession().removeAttribute(Constants.LOGIN_SELECTABLE_CLAIMS);
		}

		httpServletRequest.getSession().setAttribute(Constants.LOGIN_SELECTABLE_CLAIMS, selectableClaims);
	}

	@SuppressWarnings("unchecked")
	public Map<String, ClaimValueDTO> getSelectableClaims() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return null;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.LOGIN_SELECTABLE_CLAIMS);
		if (attribute == null) {
			return null;
		}

		return (Map<String, ClaimValueDTO>) attribute;
	}

	public void setSelectedClaims(Map<String, String> selectedClaims) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		if (selectedClaims == null) {
			httpServletRequest.getSession().removeAttribute(Constants.LOGIN_SELECTED_CLAIMS);
		}

		httpServletRequest.getSession().setAttribute(Constants.LOGIN_SELECTED_CLAIMS, selectedClaims);
	}

	@SuppressWarnings("unchecked")
	public Map<String, String> getSelectedClaims() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return null;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.LOGIN_SELECTED_CLAIMS);
		if (attribute == null) {
			return null;
		}

		return (Map<String, String>) attribute;
	}

	public void refreshSession() {
		Person person = getPerson();
		if (person == null) {
			log.warn("Could not refresh session since no person is set on it");
			return;
		}

		SessionSetting passwordRulesSettings = sessionService.getSettings(person.getDomain());

		LocalDateTime passwordLevelTimestamp = getPasswordLevelTimestamp();
		Long passwordExpiry = passwordRulesSettings.getPasswordExpiry();
		if (passwordLevelTimestamp != null && !LocalDateTime.now().minusMinutes(passwordExpiry).isAfter(passwordLevelTimestamp)) {
			setPasswordLevelTimestamp(LocalDateTime.now());
		}

		LocalDateTime mfaLevelTimestamp = getMFALevelTimestamp();
		long mfaExpiry = getSessionLifetimeMfa(person);

		if (mfaLevelTimestamp != null && !LocalDateTime.now().minusMinutes(mfaExpiry).isAfter(mfaLevelTimestamp)) {
			setMFALevelTimestamp(LocalDateTime.now());
		}
	}

	public void clearAuthentication() {
		setPasswordLevel(null);
		setMFALevel(null);
		setPerson(null);
		setMFAClients(null);
		setAuthenticatedWithADPassword(false);
		setAuthenticatedWithNemIdOrMitId(false);
	}

	public void clearSession() {
		clearSession(true);
	}
	
	public void clearSession(boolean clearPasswordChangeSuccessRedirect) {
		if (log.isDebugEnabled()) {
			log.debug("Clearing session");
		}

		clearAuthentication();

		setServiceProviderSessions(null);
		setPasswordChangeFailureReason(null);

		// minimal flowstate clearing (could we clear all states?)
		setInDedicatedActivateAccountFlow(false);
		setInEntraMfaFlow(null, null);

		if (clearPasswordChangeSuccessRedirect) {
			setPasswordChangeSuccessRedirect(null);
		}
	}

	public void clearFlowStates() {
		setInPasswordChangeFlow(false);
		setInPasswordExpiryFlow(false);
		setInNemLogInBrokerFlow(false);
		setInActivateAccountFlow(false);
		setInApproveConditionsFlow(false);
		setInForceChangePasswordFlow(false);
		setInDedicatedActivateAccountFlow(false);
		setInInsufficientNSISLevelFromMitIDFlow(false);
		setInChangePasswordFlowAndHasNotApprovedConditions(false);
		setInNemIdOrMitIDAuthenticationFlow(false);
		setInChoosePasswordResetOrUnlockAccountFlow(false);
		setInSelectClaimsFlow(false);
		setInEntraMfaFlow(null, null);
		setInPasswordlessMfaFlow(false, null);
		
		// bit of a special case, but we ONLY use this field for the login-flow, so it IS a flow-state
		setNemIDMitIDNSISLevel(null);
	}

	public void logout(LogoutRequest logoutRequest) throws ResponderException {
		// Delete everything not needed for logout procedure
		// We need Person and ServiceProviderSessions
		setLoginRequest(null);

		// Password
		setPasswordLevel(null);
		setPasswordLevelTimestamp(null);
		setPassword(null);

		// MFA
		setMFALevel(null);
		setMFALevelTimestamp(null);
		setMFAClients(null);
		setSelectedMFAClient(null);
		setSubscriptionKey(null);
		setNemIDMitIDNSISLevel(null);

		// Other
		setAuthenticatedWithADPassword(false);
		setAuthenticatedWithNemIdOrMitId(false);
		setADPerson(null);
		setAvailablePeople(new ArrayList<>()); // This does not handle null case
		setInActivateAccountFlow(false);
		setInPasswordChangeFlow(false);
		setInPasswordExpiryFlow(false);
		setInApproveConditionsFlow(false);
		setRequestedUsername(null);
		setInPasswordlessMfaFlow(false, null);

		// Save LogoutRequest to session if one is provided
		if (logoutRequest != null) {
			setLogoutRequest(logoutRequest);
		}

		if (log.isDebugEnabled()) {
			log.debug("Session logout");
		}
	}

	public void invalidateSession() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().invalidate();

		if (log.isDebugEnabled()) {
			log.debug("Session invalidated");
		}
	}

	public HttpServletRequest getServletRequest() {
		RequestAttributes attribs = RequestContextHolder.getRequestAttributes();

		if (attribs instanceof ServletRequestAttributes) {
			return ((ServletRequestAttributes) attribs).getRequest();
		}

		return null;
	}

	public String serializeSessionAsString() {
		StringBuilder sb = new StringBuilder();

		HttpServletRequest servletRequest = getServletRequest();
		if (servletRequest == null) {
			// Pretty sure this never happens, but im going to keep the null check
			return "No HttpServletRequest found";
		}

		HttpSession session = servletRequest.getSession(false);
		if (session == null) {
			return "No Session associated with request";
		}

		// Specific cases that we always skip
		List<String> doNotPrint = List.of(
				Constants.PASSWORD,
				"org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository.CSRF_TOKEN",
				"SPRING_SECURITY_SAVED_REQUEST",
				"SPRING_SECURITY_CONTEXT"
		);

		for (Enumeration<String> attributeNames = session.getAttributeNames(); attributeNames.hasMoreElements(); ) {
			String attributeName = attributeNames.nextElement();
			Object attribute = session.getAttribute(attributeName);

			if (doNotPrint.contains(attributeName)) {
				continue;
			}

			// Generic handling
			sb.append(attributeName).append(": ");
			if (attribute == null) {
				sb.append("<null>");
			}
			else if (attribute instanceof Collection<?>) {
				sb.append(((Collection<?>) attribute).size());
			}
			else if (attribute instanceof Number) {
				sb.append((Number) attribute);
			}
			else if (attribute instanceof String) {
				sb.append((String) attribute);
			}
			else if (attribute instanceof Boolean) {
				sb.append(((Boolean) attribute));
			}
			else if (attribute instanceof NSISLevel) {
				sb.append(((NSISLevel) attribute));
			}
			else if (attribute instanceof DateTime) {
				sb.append(((DateTime) attribute));
			}
			else if (attribute instanceof LocalDateTime) {
				sb.append(((LocalDateTime) attribute));
			}
			else {
				sb.append("<not-null>");
			}
			sb.append("\n");
		}

		return sb.toString();
	}
	
	public boolean hasNSISUserAndLoggedInWithNSISNone() {
		Person person = getPerson();
		if (person != null) {
			return person.hasActivatedNSISUser() && NSISLevel.NONE.equals(getLoginState());
		}
		
		return false;
	}
	
	public long getSessionLifetimePassword(Person person) {
		return getSessionLifetimePassword(person, null, null);
	}
	
	public long getSessionLifetimePassword(Person person, ServiceProvider serviceProvider, LoginRequest loginRequest) {

		// for full service IdP, compute if NSIS levels are needed
		if (commonConfiguration.getFullServiceIdP().isEnabled()) {
			// compute required NSIS Level - if it happens during a loginflow, look at the it-system otherwise let the users domain decide
			NSISLevel nsisLevel = (serviceProvider != null && loginRequest != null)
					? serviceProvider.nsisLevelRequired(loginRequest)
					: (person.getDomain().isNonNsis() ? NSISLevel.NONE : NSISLevel.SUBSTANTIAL);
			
			// check if NSIS is relevant for this loginflow
			if (NSISLevel.LOW.equalOrLesser(nsisLevel)) {
				return commonConfiguration.getFullServiceIdP().getSessionExpirePassword();
			}
		}
		
		// custom settings on SP takes precedence (always 10... might be higher ;))
		if (serviceProvider != null && serviceProvider.hasCustomSessionSettings()) {
			return Math.max(serviceProvider.getPasswordExpiry(), 10);
		}

		return Math.max(sessionSettingService.getSettings(person.getDomain()).getPasswordExpiry(), 10);
	}

	public long getSessionLifetimeMfa(Person person) {
		return getSessionLifetimeMfa(person, null, null);
	}
	
	public long getSessionLifetimeMfa(Person person, ServiceProvider serviceProvider, LoginRequest loginRequest) {

		// for full service IdP, compute if NSIS levels are needed
		if (commonConfiguration.getFullServiceIdP().isEnabled()) {
			// compute required NSIS Level - if it happens during a loginflow, look at the it-system otherwise let the users domain decide
			NSISLevel nsisLevel = (serviceProvider != null && loginRequest != null)
					? serviceProvider.nsisLevelRequired(loginRequest)
					: (person.getDomain().isNonNsis() ? NSISLevel.NONE : NSISLevel.SUBSTANTIAL);
			
			// check if NSIS is relevant for this loginflow
			if (NSISLevel.LOW.equalOrLesser(nsisLevel)) {
				return commonConfiguration.getFullServiceIdP().getSessionExpireMfa();
			}
		}
		
		// custom settings on SP takes precedence (always 10... might be higher ;))
		if (serviceProvider != null && serviceProvider.hasCustomSessionSettings()) {
			return Math.max(serviceProvider.getMfaExpiry(), 10);
		}

		return Math.max(sessionSettingService.getSettings(person.getDomain()).getMfaExpiry(), 10);
	}

	public boolean isInEntraMfaFlow() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return false;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.ENTRAID_MFA_IN_FLOW);
		return (boolean) (attribute != null ? attribute : false);
	}

	// no AuthnRequst, so no exception - ignore by adding sneaky throws
	@SneakyThrows
	public void setInEntraMfaFlow(Person person, LoginRequest loginRequest) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		if (person == null || loginRequest == null) {
			httpServletRequest.getSession().setAttribute(Constants.ENTRAID_MFA_IN_FLOW, false);
			httpServletRequest.getSession().setAttribute(Constants.ENTRAID_MFA_PERSON, null);
			
			if (log.isDebugEnabled()) {
				log.debug("setInEntraMfaFlow: false");
			}

			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.ENTRAID_MFA_IN_FLOW, true);
		httpServletRequest.getSession().setAttribute(Constants.ENTRAID_MFA_PERSON, person.getId());
		setLoginRequest(loginRequest);

		if (log.isDebugEnabled()) {
			log.debug("setInEntraMfaFlow: true");
		}
	}
	
	public void setInPasswordlessMfaFlow(boolean inPasswordlessMfaFlow, String username) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		if (inPasswordlessMfaFlow) {
			httpServletRequest.getSession().setAttribute(Constants.PASSWORDLESS_MFA_FLOW, true);
			httpServletRequest.getSession().setAttribute(Constants.PASSWORDLESS_MFA_FLOW_USERNAME, username);
		}
		else {
			httpServletRequest.getSession().removeAttribute(Constants.PASSWORDLESS_MFA_FLOW);
			httpServletRequest.getSession().removeAttribute(Constants.PASSWORDLESS_MFA_FLOW_USERNAME);
		}

		if (log.isDebugEnabled()) {
			log.debug("InPasswordlessMfaFlow: " + inPasswordlessMfaFlow);
		}
	}

	public boolean isInPasswordlessMfaFlow() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return false;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.PASSWORDLESS_MFA_FLOW);

		return (boolean) (attribute != null ? attribute : false);
	}

	public String getPasswordlessMfaFlowUsername() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return null;
		}

		return (String) httpServletRequest.getSession().getAttribute(Constants.PASSWORDLESS_MFA_FLOW_USERNAME);
	}

	// should be called at the start of a new login, so we clear any residual states
	public void prepareNewLogin() {
		// make sure any previous MFA login from EntraID MFA is cleared
		if (isInEntraMfaFlow()) {
			setMFALevel(null);
		}

		clearFlowStates();
	}
}
