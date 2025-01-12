package dk.digitalidentity.service;

import static dk.digitalidentity.util.XMLUtil.copyXMLObject;

import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.xml.crypto.dsig.CanonicalizationMethod;

import org.apache.xml.security.utils.EncryptionConstants;
import org.joda.time.DateTime;
import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.core.xml.schema.XSAny;
import org.opensaml.core.xml.schema.impl.XSAnyBuilder;
import org.opensaml.messaging.context.MessageContext;
import org.opensaml.messaging.encoder.MessageEncodingException;
import org.opensaml.saml.common.SAMLObject;
import org.opensaml.saml.common.binding.SAMLBindingSupport;
import org.opensaml.saml.common.messaging.context.SAMLEndpointContext;
import org.opensaml.saml.common.messaging.context.SAMLPeerEntityContext;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.Attribute;
import org.opensaml.saml.saml2.core.AttributeStatement;
import org.opensaml.saml.saml2.core.AttributeValue;
import org.opensaml.saml.saml2.core.Audience;
import org.opensaml.saml.saml2.core.AudienceRestriction;
import org.opensaml.saml.saml2.core.AuthnContext;
import org.opensaml.saml.saml2.core.AuthnContextClassRef;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.AuthnStatement;
import org.opensaml.saml.saml2.core.Conditions;
import org.opensaml.saml.saml2.core.EncryptedAssertion;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.NameID;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.saml.saml2.core.Status;
import org.opensaml.saml.saml2.core.StatusCode;
import org.opensaml.saml.saml2.core.Subject;
import org.opensaml.saml.saml2.core.SubjectConfirmation;
import org.opensaml.saml.saml2.core.SubjectConfirmationData;
import org.opensaml.saml.saml2.core.impl.AssertionMarshaller;
import org.opensaml.saml.saml2.core.impl.AttributeStatementMarshaller;
import org.opensaml.saml.saml2.core.impl.AttributeStatementUnmarshaller;
import org.opensaml.saml.saml2.encryption.Encrypter;
import org.opensaml.saml.saml2.metadata.SingleSignOnService;
import org.opensaml.security.x509.BasicX509Credential;
import org.opensaml.xmlsec.algorithm.descriptors.SignatureRSASHA256;
import org.opensaml.xmlsec.encryption.support.DataEncryptionParameters;
import org.opensaml.xmlsec.encryption.support.EncryptionException;
import org.opensaml.xmlsec.encryption.support.KeyEncryptionParameters;
import org.opensaml.xmlsec.encryption.support.RSAOAEPParameters;
import org.opensaml.xmlsec.keyinfo.KeyInfoGenerator;
import org.opensaml.xmlsec.keyinfo.impl.X509KeyInfoGeneratorFactory;
import org.opensaml.xmlsec.signature.Signature;
import org.opensaml.xmlsec.signature.support.SignatureException;
import org.opensaml.xmlsec.signature.support.Signer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.ModelAndView;

import dk.digitalidentity.aws.kms.jce.provider.rsa.KmsRSAPrivateKey;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.KnownCertificateAliases;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.dao.model.enums.Protocol;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.controller.dto.LoginRequest;
import dk.digitalidentity.opensaml.CustomHTTPPostEncoder;
import dk.digitalidentity.service.serviceprovider.ServiceProvider;
import dk.digitalidentity.service.serviceprovider.ServiceProviderFactory;
import dk.digitalidentity.service.serviceprovider.SqlServiceProvider;
import dk.digitalidentity.util.Constants;
import dk.digitalidentity.util.LoggingUtil;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.security.RandomIdentifierGenerationStrategy;
import net.shibboleth.utilities.java.support.velocity.VelocityEngine;

@Slf4j
@Service
public class AssertionService {

	@Autowired
	private ServiceProviderFactory serviceProviderFactory;

	@Autowired
	private CredentialService credentialService;

	@Autowired
	private OpenSAMLHelperService samlHelper;

	@Autowired
	private AuthnRequestHelper authnRequestHelper;

	@Autowired
	private LoggingUtil loggingUtil;

	@Autowired
	private SessionHelper sessionHelper;

	@Autowired
	private AuditLogger auditLogger;

	@Autowired
	private ErrorResponseService errorResponseService;

	@Autowired
	private OS2faktorConfiguration configuration;

	public void createAndSendAssertion(HttpServletResponse httpServletResponse, Person person, ServiceProvider serviceProvider, LoginRequest loginRequest) throws ResponderException, RequesterException {

		// attempt to clear any residual incoming authnRequest, to avoid strange behavior on
		// any following actions that might not be related to an authnRequest
		try {
			sessionHelper.setLoginRequest(null);
		}
		catch (Exception ex) {
			; // ignore
		}

		try {
			Map<String, Map<String, String>> spSessions = sessionHelper.getServiceProviderSessions();
			spSessions.put(serviceProvider.getEntityId(), new HashMap<>());
			sessionHelper.setServiceProviderSessions(spSessions);

			// Create assertion
			MessageContext<SAMLObject> message = createMessage(loginRequest, person);

			sessionHelper.setPassword(null);
			sessionHelper.refreshSession();

			loggingUtil.logResponse((Response) message.getMessage(), Constants.OUTGOING);

			// Send assertion
			CustomHTTPPostEncoder encoder = new CustomHTTPPostEncoder();
			encoder.setHttpServletResponse(httpServletResponse);
			encoder.setMessageContext(message);
			encoder.setVelocityEngine(VelocityEngine.newVelocityEngine());

			try {
				encoder.initialize();
				encoder.encode();
			}
			catch (ComponentInitializationException | MessageEncodingException e) {
				throw new ResponderException("Encoding error", e);
			}
		}
		catch (RequesterException | ResponderException ex) {
			AuthnRequest authnRequest = loginRequest.getAuthnRequest();

			// TODO change: i dont like that the assertion service class is concerning itself with returning errors, it should create (and *maybe* send assertions) and throw exceptions otherwise
			errorResponseService.sendError(httpServletResponse, authnRequestHelper.getConsumerEndpoint(authnRequest), authnRequest.getID(), ex);
		}
	}

	public ModelAndView createAndSendBrokeredAssertion(HttpServletResponse httpServletResponse, LoginRequest loginRequest, Assertion assertion) throws ResponderException, RequesterException {
		AuthnRequest authnRequest = loginRequest.getAuthnRequest();
		if (authnRequest == null) {
			errorResponseService.sendError(httpServletResponse, loginRequest, new ResponderException("Intet AuthnRequest på sessionen"));
		}

		MessageContext<SAMLObject> brokerAssertionMessage = createBrokeredMessage(assertion, authnRequest);

		// Send assertion
		CustomHTTPPostEncoder encoder = new CustomHTTPPostEncoder();
		encoder.setHttpServletResponse(httpServletResponse);
		encoder.setMessageContext(brokerAssertionMessage);
		encoder.setVelocityEngine(VelocityEngine.newVelocityEngine());

		try {
			encoder.initialize();
			encoder.encode();
		}
		catch (ComponentInitializationException | MessageEncodingException e) {
			throw new ResponderException("Encoding error", e);
		}

		return null;
	}

	public void signAssertion(Assertion assertion, ServiceProvider serviceProvider) throws ResponderException {
		// Prepare Assertion for Signing
		Signature signature = samlHelper.buildSAMLObject(Signature.class);

		BasicX509Credential x509Credential = null;
		if (serviceProvider instanceof SqlServiceProvider sp && Objects.equals(sp.getCertificateAlias(), KnownCertificateAliases.SELFSIGNED.toString())) {
			x509Credential = credentialService.getSelfsignedX509Credential();
		}
		else {
			x509Credential = credentialService.getBasicX509Credential();
		}

		SignatureRSASHA256 signatureRSASHA256 = new SignatureRSASHA256();

		signature.setSigningCredential(x509Credential);
		signature.setCanonicalizationAlgorithm(CanonicalizationMethod.EXCLUSIVE);
		signature.setSignatureAlgorithm(signatureRSASHA256.getURI());
		signature.setKeyInfo(credentialService.getPublicKeyInfo());
		assertion.setSignature(signature);

		// Sign Assertion
		try {
			// If the object hasnt been marshalled first it can't be signed
			AssertionMarshaller marshaller = new AssertionMarshaller();
	
			// when using KMS keys, make sure to use the KMS provider
			if (x509Credential.getPrivateKey() instanceof KmsRSAPrivateKey) {
				marshaller.marshall(assertion, Security.getProvider("KMS"));
			}
			else {
				marshaller.marshall(assertion);
			}
	
			Signer.signObject(signature);
		}
		catch (MarshallingException e) {
			throw new ResponderException("Kunne ikke omforme login besked (Assertion) før signering", e);
		}
		catch (SignatureException e) {
			throw new ResponderException("Kunne ikke signere login besked (Assertion)", e);
		}
	}

	private EncryptedAssertion encryptAssertion(Assertion assertion, java.security.cert.X509Certificate certificate, boolean oiosaml3) throws ResponderException {
		BasicX509Credential keyEncryptionCredential = new BasicX509Credential(certificate);

		DataEncryptionParameters encParams = new DataEncryptionParameters();
		if (oiosaml3) {
			encParams.setAlgorithm(EncryptionConstants.ALGO_ID_BLOCKCIPHER_AES256_GCM);
		}
		else {
			encParams.setAlgorithm(EncryptionConstants.ALGO_ID_BLOCKCIPHER_AES256);
		}
		
		X509KeyInfoGeneratorFactory keyInfoGeneratorFactory = new X509KeyInfoGeneratorFactory();
		keyInfoGeneratorFactory.setEmitEntityCertificate(true);
		KeyInfoGenerator newKeyInfoGenerator = keyInfoGeneratorFactory.newInstance();

		KeyEncryptionParameters kekParams = new KeyEncryptionParameters();
		kekParams.setEncryptionCredential(keyEncryptionCredential);
		kekParams.setKeyInfoGenerator(newKeyInfoGenerator);
		
		if (oiosaml3) {
			RSAOAEPParameters rsaOaepParams = new RSAOAEPParameters("http://www.w3.org/2001/04/xmlenc#sha256", "http://www.w3.org/2009/xmlenc11#mgf1sha256", null);
			kekParams.setRSAOAEPParameters(rsaOaepParams);
			kekParams.setAlgorithm(EncryptionConstants.ALGO_ID_KEYTRANSPORT_RSAOAEP_11);
		}
		else {
			kekParams.setAlgorithm(EncryptionConstants.ALGO_ID_KEYTRANSPORT_RSAOAEP);
		}

		Encrypter samlEncrypter = new Encrypter(encParams, kekParams);
		samlEncrypter.setKeyPlacement(Encrypter.KeyPlacement.INLINE);

		try {
			return samlEncrypter.encrypt(assertion);
		}
		catch (EncryptionException e) {
			throw new ResponderException("Kunne ikke kryptere login besked (Assertion)", e);
		}
	}

	private MessageContext<SAMLObject> createMessage(LoginRequest loginRequest, Person person) throws ResponderException, RequesterException {
		return createMessage(createResponse(loginRequest, person), authnRequestHelper.getConsumerEndpoint(loginRequest.getAuthnRequest()));
	}

	private MessageContext<SAMLObject> createBrokeredMessage(Assertion assertion, AuthnRequest authnRequest) throws ResponderException, RequesterException {
		return createMessage(createBrokerResponse(assertion, authnRequest), authnRequestHelper.getConsumerEndpoint(authnRequest));
	}

	private MessageContext<SAMLObject> createMessage(Response response, String assertionConsumerServiceURL) {

		// Build Proxy MessageContext and add response
		MessageContext<SAMLObject> messageContext = new MessageContext<>();
		messageContext.setMessage(response);

		// Set RelayState
		SAMLBindingSupport.setRelayState(messageContext, sessionHelper.getRelayState());

		// Set destination
		SAMLPeerEntityContext peerEntityContext = messageContext.getSubcontext(SAMLPeerEntityContext.class, true);
		SAMLEndpointContext endpointContext = peerEntityContext.getSubcontext(SAMLEndpointContext.class, true);

		SingleSignOnService endpoint = samlHelper.buildSAMLObject(SingleSignOnService.class);
		endpoint.setBinding(SAMLConstants.SAML2_POST_BINDING_URI);
		endpoint.setLocation(assertionConsumerServiceURL);

		endpointContext.setEndpoint(endpoint);

		return messageContext;
	}

	private Response createResponse(LoginRequest loginRequest, Person person) throws ResponderException, RequesterException {
		AuthnRequest authnRequest = loginRequest.getAuthnRequest();

		// Get SP metadata
		ServiceProvider serviceProvider = serviceProviderFactory.getServiceProvider(authnRequest);
		if (serviceProvider == null) {
			throw new RequesterException("Tjenesteudbyderen findes ikke eller har en ugyldig opsætning");
		}

		if (serviceProvider.getMetadata() == null) {
			throw new RequesterException("Tjenesteudbyderens metadata kan ikke læses");
		}

		if (serviceProvider.getMetadata().getSPSSODescriptor(SAMLConstants.SAML20P_NS) == null) {
			throw new RequesterException("Tjenesteudbyderens metadata indeholder ikke et SPSSODescriptor element");
		}

		if (serviceProvider.getMetadata().getSPSSODescriptor(SAMLConstants.SAML20P_NS).getDefaultAssertionConsumerService() == null) {
			throw new RequesterException("Tjenesteudbyderens metadata indeholder ikke et AssertionConsumerService element");
		}

		if (serviceProvider.getMetadata().getSPSSODescriptor(SAMLConstants.SAML20P_NS).getDefaultAssertionConsumerService().getLocation() == null) {
			throw new RequesterException("Tjenesteudbyderens metadata indeholder ikke en Location værdi i AssertionConsumerService elementet");
		}

		String location = authnRequestHelper.getConsumerEndpoint(authnRequest);

		DateTime issueInstant = new DateTime();

		// Create and sign Assertion
		Assertion assertion = createAssertion(issueInstant, loginRequest, person, serviceProvider);

		auditLogger.login(person, serviceProvider.getName(loginRequest), samlHelper.prettyPrint(assertion), loginRequest.getUserAgent());

		signAssertion(assertion, serviceProvider);

		return createResponse(assertion, serviceProvider, authnRequest, location, issueInstant, person);
	}

	private Response createBrokerResponse(Assertion assertion, AuthnRequest authnRequest) throws ResponderException, RequesterException {
		// Get SP metadata
		ServiceProvider serviceProvider = serviceProviderFactory.getServiceProvider(authnRequest);
		if (serviceProvider == null) {
			throw new RequesterException("Tjenesteudbyderen findes ikke eller har en ugyldig opsætning");
		}

		String location = authnRequestHelper.getConsumerEndpoint(authnRequest);
		
		DateTime issueInstant = new DateTime();

		// Create random id for assertion
		RandomIdentifierGenerationStrategy secureRandomIdGenerator = new RandomIdentifierGenerationStrategy();
		String id = secureRandomIdGenerator.generateIdentifier();

		// Create and sign Assertion
		Assertion brokerAssertion = createBrokeredAssertion(id, issueInstant, authnRequest, assertion, serviceProvider);

		signAssertion(brokerAssertion, serviceProvider);

		return createResponse(brokerAssertion, serviceProvider, authnRequest, location, issueInstant, null);
	}

	private Response createResponse(Assertion assertion, ServiceProvider serviceProvider, AuthnRequest authnRequest, String location, DateTime issueInstant, Person person) throws ResponderException, RequesterException {
		// Create Response
		Response response = samlHelper.buildSAMLObject(Response.class);
		response.setConsent("urn:oasis:names:tc:SAML:2.0:consent:unspecified");
		response.setDestination(location);
		response.setInResponseTo(authnRequest.getID());
		response.setIssueInstant(issueInstant);

		RandomIdentifierGenerationStrategy secureRandomIdGenerator = new RandomIdentifierGenerationStrategy();
		String id = secureRandomIdGenerator.generateIdentifier();
		response.setID(id);

		// Create issuer
		Issuer issuer = samlHelper.buildSAMLObject(Issuer.class);
		response.setIssuer(issuer);

		issuer.setValue(configuration.getEntityId());

		// Create status
		Status status = samlHelper.buildSAMLObject(Status.class);
		StatusCode statusCode = samlHelper.buildSAMLObject(StatusCode.class);
		statusCode.setValue(StatusCode.SUCCESS);
		status.setStatusCode(statusCode);
		response.setStatus(status);

		loggingUtil.logAssertion(assertion, Constants.OUTGOING, person);

		if (serviceProvider.encryptAssertions()) {
			X509Certificate encryptionCertificate = serviceProvider.getEncryptionCertificate();

			if (encryptionCertificate != null) {
				EncryptedAssertion encryptedAssertion = encryptAssertion(assertion, encryptionCertificate, serviceProvider.requireOiosaml3Profile());
				response.getEncryptedAssertions().add(encryptedAssertion);
			}
			else {
				log.warn("No encryption certificate found, but encrypt assertions was set to true");
				response.getAssertions().add(assertion);
			}
		}
		else {
			response.getAssertions().add(assertion);
		}

		return response;
	}

	public Assertion createAssertion(DateTime issueInstant, LoginRequest loginRequest, Person person, ServiceProvider serviceProvider) throws ResponderException, RequesterException {
		// Create random id for assertion
		RandomIdentifierGenerationStrategy secureRandomIdGenerator = new RandomIdentifierGenerationStrategy();
		String id = secureRandomIdGenerator.generateIdentifier();

		AuthnRequest authnRequest = loginRequest.getAuthnRequest();
		String assertionConsumerServiceURL = authnRequestHelper.getConsumerEndpoint(authnRequest);
		String audienceValue = authnRequest.getIssuer().getValue();

		String requestedAuthnContextClassRef = null;
		if (authnRequest.getRequestedAuthnContext() != null && authnRequest.getRequestedAuthnContext().getAuthnContextClassRefs() != null && authnRequest.getRequestedAuthnContext().getAuthnContextClassRefs().size() > 0) {
			requestedAuthnContextClassRef = authnRequest.getRequestedAuthnContext().getAuthnContextClassRefs().get(0).getAuthnContextClassRef();
		}

		return createAssertion(id, issueInstant, loginRequest, person, serviceProvider, assertionConsumerServiceURL, audienceValue, authnRequest.getID(), requestedAuthnContextClassRef);
	}

	public Assertion createAssertion(String assertionId, DateTime issueInstant, LoginRequest loginRequest, Person person, ServiceProvider serviceProvider, String assertionConsumerServiceURL, String audienceValue, String inResponseTo, String requestedAuthnContextClassRef) throws ResponderException {
		// authnInstant
		DateTime authnInstant = sessionHelper.getAuthnInstant();
		if (authnInstant == null) {
			throw new ResponderException("Tried to create assertion but there was no AuthnInstant on session");
		}

		// authnContextClassRef (default to PasswordProtectedTransport)
		String authnContextClassRef = "urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport";
		if (serviceProvider.nsisLevelRequired(loginRequest).isGreater(NSISLevel.NONE) && !Protocol.WSFED.equals(loginRequest.getProtocol())) {
			authnContextClassRef = "https://data.gov.dk/concept/core/nsis";
		}
		else if (StringUtils.hasLength(requestedAuthnContextClassRef)) {
			// it is not NSIS, and the requester asked for something specific, so we will just proxy it to be kind to their validation
			authnContextClassRef = requestedAuthnContextClassRef;
		}

		// NameID
		String nameIdFormat = serviceProvider.getNameIdFormat();
		String nameIdValue = serviceProvider.getNameId(person);

		// SubjectConfirmation
		String subjectConfirmationMethod = "urn:oasis:names:tc:SAML:2.0:cm:bearer";

		// SubjectConfirmationData
		DateTime notOnOrAfter = new DateTime(issueInstant).plusMinutes(5);

		// Attributes
		// Generate and add attributes based on person and specific SP implementation

		List<AttributeStatement> attributeStatements = generateAttributes(loginRequest, person, serviceProvider);

		return createAssertion(assertionId, issueInstant, authnInstant, serviceProvider, assertionConsumerServiceURL, audienceValue, inResponseTo, authnContextClassRef, nameIdFormat, nameIdValue, subjectConfirmationMethod, notOnOrAfter, attributeStatements);
	}

	public Assertion createBrokeredAssertion(String assertionId, DateTime issueInstant, AuthnRequest authnRequest, Assertion assertion, ServiceProvider serviceProvider) throws ResponderException, RequesterException {
		// authnInstant
		if (assertion.getAuthnStatements() == null) {
			throw new ResponderException("Tried to create assertion but there was no AuthnInstant on session");
		}
		DateTime authnInstant = assertion.getAuthnStatements().get(0).getAuthnInstant();

		// authnContextClassRef
		String authnContextClassRef = "urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport";

		// NameID
		Subject nemLogInSubject = assertion.getSubject();
		if (nemLogInSubject == null || nemLogInSubject.getNameID() == null || !StringUtils.hasLength(nemLogInSubject.getNameID().getValue())) {
			throw new ResponderException("Brokered assertion from NemLog-In does not contain a NameID");
		}
		NameID nemLogInNameId = nemLogInSubject.getNameID();
		String nameIdFormat = nemLogInNameId.getFormat();
		String nameIdValue = nemLogInNameId.getValue();

		// SubjectConfirmation
		if (nemLogInSubject.getSubjectConfirmations() == null || nemLogInSubject.getSubjectConfirmations().isEmpty()) {
			throw new ResponderException("Brokered assertion from NemLog-In does not contain SubjectConfirmation");
		}
		SubjectConfirmation nemLogInSubjectConfirmation = nemLogInSubject.getSubjectConfirmations().get(0);
		String subjectConfirmationMethod = nemLogInSubjectConfirmation.getMethod();

		// SubjectConfirmationData
		DateTime notOnOrAfter = nemLogInSubjectConfirmation.getSubjectConfirmationData().getNotOnOrAfter();
		if (nemLogInSubjectConfirmation.getSubjectConfirmationData() == null) {
			throw new ResponderException("Brokered assertion from NemLog-In does not contain SubjectConfirmationData");
		}

		String inResponseTo = authnRequest.getID();
		String assertionConsumerServiceURL = authnRequestHelper.getConsumerEndpoint(authnRequest);
		String audienceValue = authnRequest.getIssuer().getValue();

		// AttributeStatements
		// Copy Attributes
		List<AttributeStatement> attributeStatements = new ArrayList<>();
		for (AttributeStatement statement : assertion.getAttributeStatements()) {
			AttributeStatement attributeStatement = null;
			attributeStatement = (AttributeStatement) copyXMLObject(statement, new AttributeStatementMarshaller(), new AttributeStatementUnmarshaller());
			attributeStatements.add(attributeStatement);
		}

		return createAssertion(assertionId, issueInstant, authnInstant, serviceProvider, assertionConsumerServiceURL, audienceValue, inResponseTo, authnContextClassRef, nameIdFormat, nameIdValue, subjectConfirmationMethod, notOnOrAfter, attributeStatements);
	}

	 protected Assertion createAssertion(String assertionId, DateTime issueInstant, DateTime authnInstant, ServiceProvider serviceProvider, String assertionConsumerServiceURL, String audienceValue, String inResponseTo, String authnContextClassRefValue, String nameIdFormat, String nameIdValue, String subjectConfirmationMethod, DateTime notOnOrAfter, List<AttributeStatement> calculatedAttributeStatements) throws ResponderException {
		// Create assertion
		Assertion assertion = samlHelper.buildSAMLObject(Assertion.class);
		assertion.setIssueInstant(issueInstant);
		assertion.setID(assertionId);

		// Create AuthnStatement
		AuthnStatement authnStatement = samlHelper.buildSAMLObject(AuthnStatement.class);
		assertion.getAuthnStatements().add(authnStatement);
		authnStatement.setSessionIndex(assertionId);

		// Set AuthnInstant (The moment Password/MFA/MitID was validated)
		if (authnInstant == null) {
			throw new ResponderException("Tried to create assertion but there was no AuthnInstant on session");
		}
		authnStatement.setAuthnInstant(authnInstant);

		// Save ServiceProvider as logged in on session
		Map<String, Map<String, String>> spSessions = sessionHelper.getServiceProviderSessions();
		Map<String, String> map = spSessions.get(serviceProvider.getEntityId());

		if (map != null) {
			map.put(Constants.SESSION_INDEX, assertionId);
		}
		sessionHelper.setServiceProviderSessions(spSessions);

		// Create AuthnContextClassRef
		AuthnContext authnContext = samlHelper.buildSAMLObject(AuthnContext.class);
		authnStatement.setAuthnContext(authnContext);

		AuthnContextClassRef authnContextClassRef = samlHelper.buildSAMLObject(AuthnContextClassRef.class);
		authnContext.setAuthnContextClassRef(authnContextClassRef);

		authnContextClassRef.setAuthnContextClassRef(authnContextClassRefValue);

		// Create Issuer
		Issuer issuer = samlHelper.buildSAMLObject(Issuer.class);
		assertion.setIssuer(issuer);

		issuer.setFormat(NameID.ENTITY);
		issuer.setValue(configuration.getEntityId());

		// Create Subject
		Subject subject = samlHelper.buildSAMLObject(Subject.class);
		assertion.setSubject(subject);

		NameID nameID = samlHelper.buildSAMLObject(NameID.class);
		subject.setNameID(nameID);
		nameID.setFormat(nameIdFormat);
		nameID.setValue(nameIdValue);

		SubjectConfirmation subjectConfirmation = samlHelper.buildSAMLObject(SubjectConfirmation.class);
		subject.getSubjectConfirmations().add(subjectConfirmation);
		subjectConfirmation.setMethod(subjectConfirmationMethod);

		// Create SubjectConfirmationData
		if (!serviceProvider.disableSubjectConfirmation()) {
			SubjectConfirmationData subjectConfirmationData = samlHelper.buildSAMLObject(SubjectConfirmationData.class);
			subjectConfirmation.setSubjectConfirmationData(subjectConfirmationData);
			subjectConfirmationData.setInResponseTo(inResponseTo);
			subjectConfirmationData.setNotOnOrAfter(notOnOrAfter);
			subjectConfirmationData.setRecipient(assertionConsumerServiceURL);
		}

		// Create Audience restriction
		Conditions conditions = samlHelper.buildSAMLObject(Conditions.class);
		assertion.setConditions(conditions);
		conditions.setNotBefore(issueInstant);
		conditions.setNotOnOrAfter(new DateTime(issueInstant).plusMinutes(10));

		AudienceRestriction audienceRestriction = samlHelper.buildSAMLObject(AudienceRestriction.class);
		conditions.getAudienceRestrictions().add(audienceRestriction);

		Audience audience = samlHelper.buildSAMLObject(Audience.class);
		audienceRestriction.getAudiences().add(audience);
		audience.setAudienceURI(audienceValue);

		// Claims
		List<AttributeStatement> attributeStatements = assertion.getAttributeStatements();
		attributeStatements.addAll(calculatedAttributeStatements);

		return assertion;
	}

	private List<AttributeStatement> generateAttributes(LoginRequest loginRequest, Person person, ServiceProvider serviceProvider) {
		List<AttributeStatement> attributeStatements = new ArrayList<>();

		AttributeStatement attributeStatement = samlHelper.buildSAMLObject(AttributeStatement.class);
		attributeStatements.add(attributeStatement);

		Map<String, Object> attributes = serviceProvider.getAttributes(loginRequest, person, true);

		if (sessionHelper.isInSelectClaimsFlow()) {
			sessionHelper.setInSelectClaimsFlow(false);

			Map<String, String> selectedClaims = sessionHelper.getSelectedClaims();
			// Overwrite raw attributes with selected claims in case of SingleValueOnly=True in the SP Config for some field
			attributes.putAll(selectedClaims);
		}

		if (attributes != null) {
			for (Map.Entry<String, Object> entry : attributes.entrySet()) {
				attributeStatement.getAttributes().add(createSimpleAttribute(entry.getKey(), entry.getValue(), serviceProvider.requireOiosaml3Profile()));
			}
		}

		if (!Protocol.WSFED.equals(loginRequest.getProtocol())) {
			if (serviceProvider.preferNIST()) {
				addNistClaim(attributeStatement, serviceProvider);
			}
			else if (serviceProvider.nsisLevelRequired(loginRequest).isGreater(NSISLevel.NONE) || serviceProvider.supportsNsisLoaClaim()) {
				NSISLevel nsisLevel = sessionHelper.getLoginState(serviceProvider, loginRequest);
	
				if (nsisLevel != null && nsisLevel.toClaimValue() != null) {
					attributeStatement.getAttributes().add(createSimpleAttribute(Constants.LEVEL_OF_ASSURANCE, nsisLevel.toClaimValue(), serviceProvider.requireOiosaml3Profile()));
				}
				else {
					addNistClaim(attributeStatement, serviceProvider);
				}
			}
		}

		return attributeStatements;
	}

	private void addNistClaim(AttributeStatement attributeStatement, ServiceProvider serviceProvider) {

		// only set the claim if it is not already set
		for (Attribute attribute : attributeStatement.getAttributes()) {
			if (Objects.equals(Constants.NIST_CLAIM, attribute.getName())) {
				return;
			}
		}

		// value "2" if logged in with username/password and value "3" if logged in with 2-faktor
		String NISTValue = "2";
		if (sessionHelper.hasUsedMFA()) {
			NISTValue = "3";
		}

		attributeStatement.getAttributes().add(createSimpleAttribute(Constants.NIST_CLAIM, NISTValue, serviceProvider.requireOiosaml3Profile()));
	}

	private Attribute createSimpleAttribute(String attributeName, Object attributeValue, boolean oiosaml3) {
		Attribute attribute = samlHelper.buildSAMLObject(Attribute.class);

		attribute.setName(attributeName);
		attribute.setNameFormat((oiosaml3) ? Constants.ATTRIBUTE_VALUE_FORMAT_URI : Constants.ATTRIBUTE_VALUE_FORMAT_BASIC);

		XSAnyBuilder xsAnyBuilder = new XSAnyBuilder();

		if (attributeValue instanceof String) {
			XSAny value = xsAnyBuilder.buildObject(SAMLConstants.SAML20_NS, AttributeValue.DEFAULT_ELEMENT_LOCAL_NAME, SAMLConstants.SAML20_PREFIX);
			value.setTextContent((String) attributeValue);
			attribute.getAttributeValues().add(value);
		}
		else if (attributeValue instanceof List) {
			@SuppressWarnings("rawtypes")
			List list = (List) attributeValue;

			for (Object o : list) {

				if (o instanceof String) {
					XSAny value = xsAnyBuilder.buildObject(SAMLConstants.SAML20_NS, AttributeValue.DEFAULT_ELEMENT_LOCAL_NAME, SAMLConstants.SAML20_PREFIX);
					value.setTextContent((String) o);
					attribute.getAttributeValues().add(value);
				}
			}
		}

		return attribute;
	}
}
