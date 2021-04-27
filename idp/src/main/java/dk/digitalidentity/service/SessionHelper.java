package dk.digitalidentity.service;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.core.xml.io.UnmarshallingException;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.LogoutRequest;
import org.opensaml.saml.saml2.core.impl.AuthnRequestMarshaller;
import org.opensaml.saml.saml2.core.impl.AuthnRequestUnmarshaller;
import org.opensaml.saml.saml2.core.impl.LogoutRequestMarshaller;
import org.opensaml.saml.saml2.core.impl.LogoutRequestUnmarshaller;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.w3c.dom.Element;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.SessionSettingService;
import dk.digitalidentity.common.service.mfa.model.MfaClient;
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.util.Constants;
import dk.digitalidentity.util.ResponderException;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class SessionHelper {

	@Autowired
	private HttpServletRequest httpServletRequest;

	@Autowired
	private PersonService personService;

	@Autowired
	private OS2faktorConfiguration os2faktorConfiguration;

	@Autowired
	private SessionSettingService sessionService;

	private SecretKeySpec secretKey;

	public void saveIncomingAuthnRequest(AuthnRequest authnRequest, String relayState) throws ResponderException {
		setAuthnRequest(authnRequest);
		setRelayState(relayState);
	}

	public NSISLevel getLoginState() {
		Person person = getPerson();
		if (person == null) {
			return null;
		}

		NSISLevel passwordLevel = getPasswordLevel();
		NSISLevel mfaLevel = getMFALevel();
		NSISLevel personLevel = person.getNsisLevel();

		log.debug("passwordLevel = " + passwordLevel);
		log.debug("mfaLevel = " + mfaLevel);
		log.debug("personLevel = " + personLevel);

		if (NSISLevel.HIGH.equalOrLesser(personLevel) && NSISLevel.HIGH.equalOrLesser(passwordLevel)
				&& NSISLevel.HIGH.equalOrLesser(mfaLevel)) {
			log.debug("LoginState evaluated to HIGH");
			return NSISLevel.HIGH;
		}

		if (NSISLevel.SUBSTANTIAL.equalOrLesser(personLevel) && NSISLevel.SUBSTANTIAL.equalOrLesser(passwordLevel)
				&& NSISLevel.SUBSTANTIAL.equalOrLesser(mfaLevel)) {
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

	public void clearSession() {
		setPasswordLevel(null);
		setMFALevel(null);
		setPerson(null);
		setMFAClients(null);
		setServiceProviderSessions(null);
		setAuthenticatedWithADPassword(false);
	}

	public NSISLevel getPasswordLevel() {
		Object attribute = httpServletRequest.getSession().getAttribute(Constants.PASSWORD_AUTHENTIFICATION_LEVEL);
		LocalDateTime timestamp = getPasswordLevelTimestamp();
		Person person = getPerson();

		if (attribute != null && timestamp != null && person != null) {
			Long passwordExpiry = sessionService.getSettings(person.getDomain()).getPasswordExpiry();
			if (LocalDateTime.now().minusMinutes(passwordExpiry).isAfter(timestamp)) {
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
	}

	public LocalDateTime getPasswordLevelTimestamp() {
		Object timestamp = httpServletRequest.getSession()
				.getAttribute(Constants.PASSWORD_AUTHENTIFICATION_LEVEL_TIMESTAMP);
		if (timestamp != null) {
			return (LocalDateTime) timestamp;
		}

		return null;
	}

	private void setPasswordLevelTimestamp(LocalDateTime timestamp) {
		if (timestamp == null) {
			httpServletRequest.getSession().setAttribute(Constants.PASSWORD_AUTHENTIFICATION_LEVEL_TIMESTAMP, null);
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.PASSWORD_AUTHENTIFICATION_LEVEL_TIMESTAMP, timestamp);
	}

	public NSISLevel getMFALevel() {
		Object attribute = httpServletRequest.getSession().getAttribute(Constants.MFA_AUTHENTICATION_LEVEL);
		LocalDateTime timestamp = getMFALevelTimestamp();
		Person person = getPerson();

		if (attribute != null && timestamp != null && person != null) {
			Long mfaExpiry = sessionService.getSettings(person.getDomain()).getMfaExpiry();
			if (LocalDateTime.now().minusMinutes(mfaExpiry).isAfter(timestamp)) {
				setMFALevelTimestamp(null);
				setMFALevel(null);
				return null;
			}

			return (NSISLevel) attribute;
		}

		return null;
	}

	// Will only elevate permissions or delete them
	public void setMFALevel(NSISLevel nsisLevel) {
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
	}

	public LocalDateTime getMFALevelTimestamp() {
		Object timestamp = httpServletRequest.getSession().getAttribute(Constants.MFA_AUTHENTICATION_LEVEL_TIMESTAMP);
		if (timestamp != null) {
			return (LocalDateTime) timestamp;
		}

		return null;
	}

	private void setMFALevelTimestamp(LocalDateTime timestamp) {
		if (timestamp == null) {
			httpServletRequest.getSession().setAttribute(Constants.MFA_AUTHENTICATION_LEVEL_TIMESTAMP, null);
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.MFA_AUTHENTICATION_LEVEL_TIMESTAMP, timestamp);
	}

	public LogoutRequest getLogoutRequest() throws ResponderException {
		try {
			Element marshalledLogoutRequest = (Element) httpServletRequest.getSession()
					.getAttribute(Constants.LOGOUT_REQUEST);
			return (LogoutRequest) new LogoutRequestUnmarshaller().unmarshall(marshalledLogoutRequest);
		} catch (UnmarshallingException ex) {
			throw new ResponderException("Kunne ikke afkode logout forespørgsel (LogoutRequest)", ex);
		}
	}

	public void setLogoutRequest(LogoutRequest logoutRequest) throws ResponderException {
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
		Object attribute = httpServletRequest.getSession().getAttribute(Constants.SERVICE_PROVIDER);
		if (attribute != null) {
			return (Map<String, Map<String, String>>) attribute;
		} else {
			return new HashMap<>();
		}
	}

	public void setServiceProviderSessions(Map<String, Map<String, String>> serviceProviders) {
		httpServletRequest.getSession().setAttribute(Constants.SERVICE_PROVIDER, serviceProviders);
	}

	public AuthnRequest getAuthnRequest() throws ResponderException {
		HttpSession session = httpServletRequest.getSession();
		Object attribute = session.getAttribute(Constants.AUTHN_REQUEST);
		if (attribute == null) {
			return null;
		}

		try {
			Element marshalledAuthnRequest = (Element) attribute;
			return (AuthnRequest) new AuthnRequestUnmarshaller().unmarshall(marshalledAuthnRequest);
		} catch (UnmarshallingException ex) {
			throw new ResponderException("Kunne ikke afkode login forespørgsel, Fejl url ikke kendt", ex);
		}
	}

	public void setAuthnRequest(AuthnRequest authnRequest) throws ResponderException {
		if (authnRequest == null) {
			httpServletRequest.getSession().setAttribute(Constants.AUTHN_REQUEST, null);
			return;
		}

		try {
			Element marshall = new AuthnRequestMarshaller().marshall(authnRequest);
			httpServletRequest.getSession().setAttribute(Constants.AUTHN_REQUEST, marshall);
		} catch (MarshallingException ex) {
			throw new ResponderException("Kunne ikke omforme login forespørgsel (AuthnRequest)", ex);
		}
	}

	public String getRelayState() {
		return (String) httpServletRequest.getSession().getAttribute(Constants.RELAY_STATE);
	}

	public void setRelayState(String relayState) {
		httpServletRequest.getSession().setAttribute(Constants.RELAY_STATE, relayState);
	}

	public Person getPerson() {
		Object attribute = httpServletRequest.getSession().getAttribute(Constants.PERSON_ID);

		if (attribute != null) {
			long personId = (long) attribute;
			return personService.getById(personId);
		}
		return null;
	}

	public void setPerson(Person person) {
		httpServletRequest.getSession().setAttribute(Constants.PERSON_ID, person == null ? null : person.getId());
	}

	@SuppressWarnings("unchecked")
	public List<MfaClient> getMFAClients() {
		Object attribute = httpServletRequest.getSession().getAttribute(Constants.MFA_CLIENTS);
		if (attribute != null) {
			try {
				return (List<MfaClient>) attribute;
			} catch (Exception ex) {
				log.error("Could not cast what was stored in the session as a List<MfaClient>", ex);

				Person person = getPerson();
				log.warn("Class:" + attribute.getClass() + ", Person on session:" + (person != null ? person.getUuid() : "<null>"));
			}
		}
		return null;
	}

	public void setMFAClients(List<MfaClient> mfaDevices) {
		httpServletRequest.getSession().setAttribute(Constants.MFA_CLIENTS, mfaDevices);
	}

	public MfaClient getSelectedMFAClient() {
		Object attribute = httpServletRequest.getSession().getAttribute(Constants.MFA_SELECTED_CLIENT);
		if (attribute != null) {
			return (MfaClient) attribute;
		}
		return null;
	}

	public void setSelectedMFAClient(MfaClient mfaClient) {
		httpServletRequest.getSession().setAttribute(Constants.MFA_SELECTED_CLIENT, mfaClient);
	}

	public String getSubscriptionKey() {
		return (String) httpServletRequest.getSession().getAttribute(Constants.SUBSCRIPTION_KEY);
	}

	public void setSubscriptionKey(String subscriptionKey) {
		httpServletRequest.getSession().setAttribute(Constants.SUBSCRIPTION_KEY, subscriptionKey);
	}

	public boolean isAuthenticatedWithADPassword() {
		Object attribute = httpServletRequest.getSession().getAttribute(Constants.AUTHENTICATED_WITH_AD_PASSWORD);
		return (boolean) (attribute != null ? attribute : false);
	}

	public void setAuthenticatedWithADPassword(boolean b) {
		httpServletRequest.getSession().setAttribute(Constants.AUTHENTICATED_WITH_AD_PASSWORD, b);
	}

	public boolean isAuthenticatedWithNemId() {
		Object attribute = httpServletRequest.getSession().getAttribute(Constants.AUTHENTICATED_WITH_NEMID);
		return (boolean) (attribute != null ? attribute : false);
	}

	public void setAuthenticatedWithNemId(Boolean b) {
		httpServletRequest.getSession().setAttribute(Constants.AUTHENTICATED_WITH_NEMID, b);
	}

	public String getPassword() {
		String encryptedPassword = (String) httpServletRequest.getSession().getAttribute(Constants.PASSWORD);
		return decryptString(encryptedPassword);
	}

	public void setPassword(String password) {
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
		if (StringUtils.isEmpty(encryptedString)) {
			return null;
		}

		try {
			SecretKeySpec key = getKey(os2faktorConfiguration.getPassword().getSecret());
			Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5PADDING");
			cipher.init(Cipher.DECRYPT_MODE, key);
			return new String(cipher.doFinal(Base64.getDecoder().decode(encryptedString)));
		} catch (Exception e) {
			log.error("Error while decrypting string", e);
		}
		return null;
	}

	private String encryptString(String rawString) {
		if (StringUtils.isEmpty(rawString)) {
			return null;
		}

		try {
			SecretKeySpec key = getKey(os2faktorConfiguration.getPassword().getSecret());
			Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
			cipher.init(Cipher.ENCRYPT_MODE, key);
			return Base64.getEncoder().encodeToString(cipher.doFinal(rawString.getBytes("UTF-8")));
		} catch (Exception e) {
			log.error("Error while encrypting string", e);
		}
		return rawString;
	}

	public String getNemIDPid() {
		return (String) httpServletRequest.getSession().getAttribute(Constants.NEMID_PID);
	}

	public void setNemIDPid(String pid) {
		httpServletRequest.getSession().setAttribute(Constants.NEMID_PID, pid);
	}

	public Person getADPerson() {
		Object attribute = httpServletRequest.getSession().getAttribute(Constants.AD_PERSON_ID);

		if (attribute != null) {
			long personId = (long) attribute;
			return personService.getById(personId);
		}
		return null;
	}

	public void setADPerson(Person person) {
		httpServletRequest.getSession().setAttribute(Constants.AD_PERSON_ID, person == null ? null : person.getId());
	}

	@SuppressWarnings("unchecked")
	public List<Person> getAvailablePeople() {
		List<Long> attribute = (List<Long>) httpServletRequest.getSession().getAttribute(Constants.AVAILABLE_PEOPLE);
		return attribute.stream().map(l -> personService.getById(l)).collect(Collectors.toList());
	}

	public void setAvailablePeople(List<Person> people) {
		List<Long> peopleIds = people.stream().map(Person::getId).collect(Collectors.toList());
		httpServletRequest.getSession().setAttribute(Constants.AVAILABLE_PEOPLE, peopleIds);
	}

	public void setInActivateAccountFlow(boolean b) {
		httpServletRequest.getSession().setAttribute(Constants.ACTIVATE_ACCOUNT_FLOW, b);
	}

	public boolean isInActivateAccountFlow() {
		Object attribute = httpServletRequest.getSession().getAttribute(Constants.ACTIVATE_ACCOUNT_FLOW);
		return (boolean) (attribute != null ? attribute : false);
	}

	public void setInApproveConditionsFlow(boolean inApproveConditionsFlow) {
		httpServletRequest.getSession().setAttribute(Constants.APPROVE_CONDITIONS_FLOW, inApproveConditionsFlow);
	}

	public boolean isInApproveConditionsFlow() {
		Object attribute = httpServletRequest.getSession().getAttribute(Constants.APPROVE_CONDITIONS_FLOW);
		return (boolean) (attribute != null ? attribute : false);
	}

	public void setInPasswordChangeFlow(boolean inPasswordChangeFlow) {
		httpServletRequest.getSession().setAttribute(Constants.PASSWORD_CHANGE_FLOW, inPasswordChangeFlow);
	}

	public boolean isInPasswordChangeFlow() {
		Object attribute = httpServletRequest.getSession().getAttribute(Constants.PASSWORD_CHANGE_FLOW);
		return (boolean) (attribute != null ? attribute : false);
	}

	public void setInPasswordExpiryFlow(boolean inPasswordExpiryFlow) {
		httpServletRequest.getSession().setAttribute(Constants.PASSWORD_EXPIRY_FLOW, inPasswordExpiryFlow);
	}

	public boolean isInPasswordExpiryFlow() {
		Object attribute = httpServletRequest.getSession().getAttribute(Constants.PASSWORD_EXPIRY_FLOW);
		return (boolean) (attribute != null ? attribute : false);
	}

	public void setDeclineUserActivation(boolean inPasswordExpiryFlow) {
		httpServletRequest.getSession().setAttribute(Constants.DECLINE_USER_ACTIVATION, inPasswordExpiryFlow);
	}

	public boolean isDeclineUserActivation() {
		Object attribute = httpServletRequest.getSession().getAttribute(Constants.DECLINE_USER_ACTIVATION);
		return (boolean) (attribute != null ? attribute : false);
	}

	public String getPasswordChangeSuccessRedirect() {
		Object redirectUrl = httpServletRequest.getSession().getAttribute(Constants.PASSWORD_CHANGE_SUCCESS_REDIRECT);
		if (redirectUrl == null) {
			return null;
		}

		return (String) redirectUrl;
	}

	public void setPasswordChangeSuccessRedirect(String redirectUrl) {
		httpServletRequest.getSession().setAttribute(Constants.PASSWORD_CHANGE_SUCCESS_REDIRECT, redirectUrl);
	}

	public void logout(LogoutRequest logoutRequest) throws ResponderException {
		// Delete everything not needed for logout procedure
		// We need Person and ServiceProviderSessions
		setAuthnRequest(null);

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

		// Other
		setAuthenticatedWithADPassword(false);
		setAuthenticatedWithNemId(false);
		setADPerson(null);
		setNemIDPid(null);
		setAvailablePeople(new ArrayList<>()); // This does not handle null case
		setInActivateAccountFlow(false);
		setInPasswordChangeFlow(false);
		setInPasswordExpiryFlow(false);
		setInApproveConditionsFlow(false);

		// Save LogoutRequest to session if one is provided
		if (logoutRequest != null) {
			setLogoutRequest(logoutRequest);
		}
	}

	public void invalidateSession() {
		httpServletRequest.getSession().invalidate();
	}
}
