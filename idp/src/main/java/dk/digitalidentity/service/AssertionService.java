package dk.digitalidentity.service;

import static dk.digitalidentity.util.XMLUtil.copyXMLObject;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;

import javax.xml.crypto.dsig.CanonicalizationMethod;

import org.apache.xml.security.utils.EncryptionConstants;
import org.joda.time.DateTime;
import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.core.xml.schema.XSAny;
import org.opensaml.core.xml.schema.impl.XSAnyBuilder;
import org.opensaml.messaging.context.MessageContext;
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
import org.opensaml.security.credential.Credential;
import org.opensaml.security.x509.BasicX509Credential;
import org.opensaml.xmlsec.algorithm.descriptors.SignatureRSASHA256;
import org.opensaml.xmlsec.encryption.support.DataEncryptionParameters;
import org.opensaml.xmlsec.encryption.support.EncryptionException;
import org.opensaml.xmlsec.encryption.support.KeyEncryptionParameters;
import org.opensaml.xmlsec.keyinfo.KeyInfoGenerator;
import org.opensaml.xmlsec.keyinfo.impl.X509KeyInfoGeneratorFactory;
import org.opensaml.xmlsec.signature.Signature;
import org.opensaml.xmlsec.signature.support.SignatureException;
import org.opensaml.xmlsec.signature.support.Signer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.service.serviceprovider.ServiceProvider;
import dk.digitalidentity.service.serviceprovider.ServiceProviderFactory;
import dk.digitalidentity.util.Constants;
import dk.digitalidentity.util.LoggingUtil;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import net.shibboleth.utilities.java.support.security.RandomIdentifierGenerationStrategy;

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
	private OS2faktorConfiguration configuration;

	public MessageContext<SAMLObject> createMessageContextWithAssertion(AuthnRequest authnRequest, Person person) throws ResponderException, RequesterException {
		String assertionConsumerServiceURL = authnRequestHelper.getConsumerEndpoint(authnRequest);

		// Create proxy Response
		Response response = createResponse(authnRequest, person);

		// Build Proxy MessageContext and add response
		MessageContext<SAMLObject> proxyMessageContext = new MessageContext<>();
		proxyMessageContext.setMessage(response);

		// Set RelayState
		SAMLBindingSupport.setRelayState(proxyMessageContext, sessionHelper.getRelayState());

		// Set destination
		SAMLPeerEntityContext peerEntityContext = proxyMessageContext.getSubcontext(SAMLPeerEntityContext.class, true);
		SAMLEndpointContext endpointContext = peerEntityContext.getSubcontext(SAMLEndpointContext.class, true);

		SingleSignOnService endpoint = samlHelper.buildSAMLObject(SingleSignOnService.class);
		endpoint.setBinding(SAMLConstants.SAML2_POST_BINDING_URI);
		endpoint.setLocation(assertionConsumerServiceURL);

		endpointContext.setEndpoint(endpoint);

		return proxyMessageContext;
	}

	private Response createResponse(AuthnRequest authnRequest, Person person) throws ResponderException, RequesterException {
		// Get SP metadata
		ServiceProvider serviceProvider = serviceProviderFactory.getServiceProvider(authnRequest);
		String location = serviceProvider
				.getMetadata()
				.getSPSSODescriptor(SAMLConstants.SAML20P_NS)
				.getDefaultAssertionConsumerService()
				.getLocation();

		DateTime issueInstant = new DateTime();

		// Create and sign Assertion
		Assertion assertion = createAssertion(issueInstant, authnRequest, person, serviceProvider);

		auditLogger.login(person, serviceProvider.getName(authnRequest), samlHelper.prettyPrint(assertion));

		signAssertion(assertion);

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

		X509Certificate encryptionCertificate = serviceProvider.getEncryptionCertificate();

		if (serviceProvider.encryptAssertions() && encryptionCertificate != null) {
			EncryptedAssertion proxyEncryptedAssertion = encryptAssertion(assertion, encryptionCertificate);
			response.getEncryptedAssertions().add(proxyEncryptedAssertion);
		}
		else {
			response.getAssertions().add(assertion);
		}

		return response;
	}

	private Assertion createAssertion(DateTime issueInstant, AuthnRequest authnRequest, Person person, ServiceProvider serviceProvider) throws ResponderException, RequesterException {
		// Create random id for assertion
		RandomIdentifierGenerationStrategy secureRandomIdGenerator = new RandomIdentifierGenerationStrategy();
		String id = secureRandomIdGenerator.generateIdentifier();

		// Create assertion
		Assertion assertion = samlHelper.buildSAMLObject(Assertion.class);
		assertion.setIssueInstant(issueInstant);
		assertion.setID(id);

		// Create AuthnStatement
		AuthnStatement authnStatement = samlHelper.buildSAMLObject(AuthnStatement.class);
		assertion.getAuthnStatements().add(authnStatement);
		authnStatement.setSessionIndex(id);

		// Set AuthnInstant (The moment password/mfa/nemid was validated)
		DateTime authnInstant = sessionHelper.getAuthnInstant();

		if (authnInstant == null) {
			throw new ResponderException("Tried to create assertion but there was no AuthnInstant on session");
		}
		authnStatement.setAuthnInstant(authnInstant);

		Map<String, Map<String, String>> spSessions = sessionHelper.getServiceProviderSessions();
		Map<String, String> map = spSessions.get(serviceProvider.getEntityId());

		if (map != null) {
			map.put(Constants.SESSION_INDEX, id);
		}
		sessionHelper.setServiceProviderSessions(spSessions);

		AuthnContext authnContext = samlHelper.buildSAMLObject(AuthnContext.class);
		authnStatement.setAuthnContext(authnContext);

		AuthnContextClassRef authnContextClassRef = samlHelper.buildSAMLObject(AuthnContextClassRef.class);
		authnContext.setAuthnContextClassRef(authnContextClassRef);

		authnContextClassRef.setAuthnContextClassRef("urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport");

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

		nameID.setValue(serviceProvider.getNameId(person));
		nameID.setFormat(serviceProvider.getNameIdFormat());

		SubjectConfirmation subjectConfirmation = samlHelper.buildSAMLObject(SubjectConfirmation.class);
		subject.getSubjectConfirmations().add(subjectConfirmation);

		subjectConfirmation.setMethod("urn:oasis:names:tc:SAML:2.0:cm:bearer");

		SubjectConfirmationData subjectConfirmationData = samlHelper.buildSAMLObject(SubjectConfirmationData.class);
		subjectConfirmationData.setInResponseTo(authnRequest.getID());
		subjectConfirmation.setSubjectConfirmationData(subjectConfirmationData);

		subjectConfirmationData.setNotOnOrAfter(new DateTime(issueInstant).plusMinutes(5));

		String assertionConsumerServiceURL = authnRequestHelper.getConsumerEndpoint(authnRequest);
		subjectConfirmationData.setRecipient(assertionConsumerServiceURL);

		// Create Audience restriction
		Conditions conditions = samlHelper.buildSAMLObject(Conditions.class);
		assertion.setConditions(conditions);

		conditions.setNotBefore(issueInstant);
		conditions.setNotOnOrAfter(new DateTime(issueInstant).plusHours(1));

		AudienceRestriction audienceRestriction = samlHelper.buildSAMLObject(AudienceRestriction.class);
		conditions.getAudienceRestrictions().add(audienceRestriction);

		Audience audience = samlHelper.buildSAMLObject(Audience.class);
		audienceRestriction.getAudiences().add(audience);

		audience.setAudienceURI(authnRequest.getIssuer().getValue());

		// Generate and add attributes based on person and specific SP implementation
		List<AttributeStatement> attributeStatements = assertion.getAttributeStatements();

		AttributeStatement attributeStatement = samlHelper.buildSAMLObject(AttributeStatement.class);
		attributeStatements.add(attributeStatement);

		Map<String, Object> attributes = serviceProvider.getAttributes(authnRequest, person);

		if (sessionHelper.isInSelectClaimsFlow()) {
			sessionHelper.setInSelectClaimsFlow(false);

			Map<String, String> selectedClaims = sessionHelper.getSelectedClaims();
			// Overwrite raw attributes with selected claims in case of SingleValueOnly=True in the SP Config for some field
			attributes.putAll(selectedClaims);
		}

		if (attributes != null) {

			for (Map.Entry<String, Object> entry : attributes.entrySet()) {
				attributeStatement.getAttributes().add(createSimpleAttribute(entry.getKey(), entry.getValue()));
			}
		}
		
		if (serviceProvider.preferNIST()) {
			
			// value "2" if loged in with username/password and value "3" if logged in with 2-faktor
			String NISTValue = "2";
			if (sessionHelper.hasUsedMFA()) {
				NISTValue = "3";
			}
			attributeStatement.getAttributes().add(createSimpleAttribute(Constants.NIST_CLAIM, NISTValue));
		} else {
			NSISLevel nsisLevel = sessionHelper.getLoginState();

			if (nsisLevel != null && nsisLevel.toClaimValue() != null && serviceProvider.supportsNsisLoaClaim()) {
				attributeStatement.getAttributes().add(createSimpleAttribute(Constants.LEVEL_OF_ASSURANCE, nsisLevel.toClaimValue()));
			}
		}

		return assertion;
	}

	private void signAssertion(Assertion assertion) throws ResponderException {
		// Prepare Assertion for Signing
		Signature signature = samlHelper.buildSAMLObject(Signature.class);

		BasicX509Credential x509Credential = credentialService.getBasicX509Credential();
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
			marshaller.marshall(assertion);

			Signer.signObject(signature);
		}
		catch (MarshallingException e) {
			throw new ResponderException("Kunne ikke omforme login besked (Assertion) før signering", e);
		}
		catch (SignatureException e) {
			throw new ResponderException("Kunne ikke signere login besked (Assertion)", e);
		}
	}

	private EncryptedAssertion encryptAssertion(Assertion assertion, java.security.cert.X509Certificate certificate) throws ResponderException {
		Credential keyEncryptionCredential = new BasicX509Credential(certificate);

		DataEncryptionParameters encParams = new DataEncryptionParameters();
		encParams.setAlgorithm(EncryptionConstants.ALGO_ID_BLOCKCIPHER_AES256);

		X509KeyInfoGeneratorFactory keyInfoGeneratorFactory = new X509KeyInfoGeneratorFactory();
		keyInfoGeneratorFactory.setEmitEntityCertificate(true);
		KeyInfoGenerator newKeyInfoGenerator = keyInfoGeneratorFactory.newInstance();

		KeyEncryptionParameters kekParams = new KeyEncryptionParameters();
		kekParams.setEncryptionCredential(keyEncryptionCredential);
		kekParams.setAlgorithm(EncryptionConstants.ALGO_ID_KEYTRANSPORT_RSAOAEP);
		kekParams.setKeyInfoGenerator(newKeyInfoGenerator);

		Encrypter samlEncrypter = new Encrypter(encParams, kekParams);
		samlEncrypter.setKeyPlacement(Encrypter.KeyPlacement.INLINE);

		try {
			return samlEncrypter.encrypt(assertion);
		}
		catch (EncryptionException e) {
			throw new ResponderException("Kunne ikke kryptere login besked (Assertion)", e);
		}
	}

	private Attribute createSimpleAttribute(String attributeName, Object attributeValue) {
		Attribute attribute = samlHelper.buildSAMLObject(Attribute.class);

		attribute.setName(attributeName);
		attribute.setNameFormat(Constants.ATTRIBUTE_VALUE_FORMAT);

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

	public MessageContext<SAMLObject> createBrokerAssertionMessage(Assertion assertion, AuthnRequest authnRequest) throws ResponderException, RequesterException {
		String assertionConsumerServiceURL = authnRequestHelper.getConsumerEndpoint(authnRequest);

		// Create proxy Response
		Response response = createBrokerResponse(assertion, authnRequest);

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

	private Response createBrokerResponse(Assertion assertion, AuthnRequest authnRequest) throws ResponderException, RequesterException {
		// Get SP metadata
		ServiceProvider serviceProvider = serviceProviderFactory.getServiceProvider(authnRequest);
		String location = serviceProvider
				.getMetadata()
				.getSPSSODescriptor(SAMLConstants.SAML20P_NS)
				.getDefaultAssertionConsumerService()
				.getLocation();

		DateTime issueInstant = new DateTime();

		// Create and sign Assertion
		Assertion brokerAssertion = createBrokerAssertion(issueInstant, authnRequest, assertion, serviceProvider);

		signAssertion(brokerAssertion);

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

		loggingUtil.logAssertion(brokerAssertion, Constants.OUTGOING, null);

		X509Certificate encryptionCertificate = serviceProvider.getEncryptionCertificate();

		if (serviceProvider.encryptAssertions() && encryptionCertificate != null) {
			EncryptedAssertion proxyEncryptedAssertion = encryptAssertion(brokerAssertion, encryptionCertificate);
			response.getEncryptedAssertions().add(proxyEncryptedAssertion);
		}
		else {
			response.getAssertions().add(brokerAssertion);
		}

		return response;
	}

	private Assertion createBrokerAssertion(DateTime issueInstant, AuthnRequest authnRequest, Assertion assertion, ServiceProvider serviceProvider) throws ResponderException, RequesterException {
		// Create random id for assertion
		RandomIdentifierGenerationStrategy secureRandomIdGenerator = new RandomIdentifierGenerationStrategy();
		String id = secureRandomIdGenerator.generateIdentifier();

		// Create assertion
		Assertion brokerAssertion = samlHelper.buildSAMLObject(Assertion.class);
		brokerAssertion.setIssueInstant(issueInstant);
		brokerAssertion.setID(id);

		// Create AuthnStatement
		AuthnStatement authnStatement = samlHelper.buildSAMLObject(AuthnStatement.class);
		brokerAssertion.getAuthnStatements().add(authnStatement);
		authnStatement.setSessionIndex(id);

		// Set AuthnInstant (The moment password/mfa/nemid was validated)
		if (assertion.getAuthnStatements() == null) {
			throw new ResponderException("Tried to create assertion but there was no AuthnInstant on session");
		}

		DateTime authnInstant = assertion.getAuthnStatements().get(0).getAuthnInstant();
		authnStatement.setAuthnInstant(authnInstant);

		Map<String, Map<String, String>> spSessions = sessionHelper.getServiceProviderSessions();
		Map<String, String> map = spSessions.get(serviceProvider.getEntityId());

		if (map != null) {
			map.put(Constants.SESSION_INDEX, id);
		}
		sessionHelper.setServiceProviderSessions(spSessions);

		AuthnContext authnContext = samlHelper.buildSAMLObject(AuthnContext.class);
		authnStatement.setAuthnContext(authnContext);

		AuthnContextClassRef authnContextClassRef = samlHelper.buildSAMLObject(AuthnContextClassRef.class);
		authnContext.setAuthnContextClassRef(authnContextClassRef);

		authnContextClassRef.setAuthnContextClassRef("urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport");

		// Create Issuer
		Issuer issuer = samlHelper.buildSAMLObject(Issuer.class);
		brokerAssertion.setIssuer(issuer);

		issuer.setFormat(NameID.ENTITY);
		issuer.setValue(configuration.getEntityId());

		// Create Subject
		Subject subject = samlHelper.buildSAMLObject(Subject.class);
		brokerAssertion.setSubject(subject);

		NameID nameID = samlHelper.buildSAMLObject(NameID.class);
		subject.setNameID(nameID);

		Subject nemLogInSubject = assertion.getSubject();
		NameID nemLogInNameId = nemLogInSubject.getNameID();
		nameID.setValue(nemLogInNameId.getValue());
		nameID.setFormat(nemLogInNameId.getFormat());

		SubjectConfirmation subjectConfirmation = samlHelper.buildSAMLObject(SubjectConfirmation.class);
		subject.getSubjectConfirmations().add(subjectConfirmation);

		// this will throw an exception if we do not have a SubjectConfirmation from the NemLog-in token (we will have that, but we should probably do some safer/saner checking)
		SubjectConfirmation nemLogInSubjectConfirmation = nemLogInSubject.getSubjectConfirmations().get(0);
		subjectConfirmation.setMethod(nemLogInSubjectConfirmation.getMethod());

		SubjectConfirmationData subjectConfirmationData = samlHelper.buildSAMLObject(SubjectConfirmationData.class);
		subjectConfirmationData.setInResponseTo(authnRequest.getID());
		subjectConfirmation.setSubjectConfirmationData(subjectConfirmationData);

		subjectConfirmationData.setNotOnOrAfter(nemLogInSubjectConfirmation.getSubjectConfirmationData().getNotOnOrAfter());

		String assertionConsumerServiceURL = authnRequestHelper.getConsumerEndpoint(authnRequest);
		subjectConfirmationData.setRecipient(assertionConsumerServiceURL);

		// Create Audience restriction
		Conditions conditions = samlHelper.buildSAMLObject(Conditions.class);
		brokerAssertion.setConditions(conditions);

		conditions.setNotBefore(issueInstant);
		conditions.setNotOnOrAfter(new DateTime(issueInstant).plusHours(1));

		AudienceRestriction audienceRestriction = samlHelper.buildSAMLObject(AudienceRestriction.class);
		conditions.getAudienceRestrictions().add(audienceRestriction);

		Audience audience = samlHelper.buildSAMLObject(Audience.class);
		audienceRestriction.getAudiences().add(audience);

		audience.setAudienceURI(authnRequest.getIssuer().getValue());

		// Copy Attributes
		List<AttributeStatement> attributeStatements = brokerAssertion.getAttributeStatements();

		for (AttributeStatement statement : assertion.getAttributeStatements()) {
			AttributeStatement attributeStatement = null;
			attributeStatement = (AttributeStatement) copyXMLObject(statement, new AttributeStatementMarshaller(), new AttributeStatementUnmarshaller());
			attributeStatements.add(attributeStatement);
		}

		return brokerAssertion;
	}
}
