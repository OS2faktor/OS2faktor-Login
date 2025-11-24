package dk.digitalidentity.service;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.xml.crypto.dsig.CanonicalizationMethod;

import org.joda.time.DateTime;
import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.messaging.context.MessageContext;
import org.opensaml.messaging.decoder.MessageDecodingException;
import org.opensaml.messaging.decoder.servlet.BaseHttpServletRequestXMLMessageDecoder;
import org.opensaml.saml.common.SAMLObject;
import org.opensaml.saml.common.messaging.context.SAMLEndpointContext;
import org.opensaml.saml.common.messaging.context.SAMLPeerEntityContext;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.binding.decoding.impl.HTTPPostDecoder;
import org.opensaml.saml.saml2.binding.decoding.impl.HTTPRedirectDeflateDecoder;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.LogoutRequest;
import org.opensaml.saml.saml2.core.NameID;
import org.opensaml.saml.saml2.core.SessionIndex;
import org.opensaml.saml.saml2.core.impl.LogoutRequestMarshaller;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml.saml2.metadata.SingleSignOnService;
import org.opensaml.security.x509.BasicX509Credential;
import org.opensaml.xmlsec.SignatureSigningParameters;
import org.opensaml.xmlsec.algorithm.descriptors.SignatureRSASHA256;
import org.opensaml.xmlsec.context.SecurityParametersContext;
import org.opensaml.xmlsec.signature.Signature;
import org.opensaml.xmlsec.signature.support.SignatureConstants;
import org.opensaml.xmlsec.signature.support.SignatureException;
import org.opensaml.xmlsec.signature.support.Signer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import dk.digitalidentity.common.dao.model.enums.KnownCertificateAliases;
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.service.serviceprovider.ServiceProvider;
import dk.digitalidentity.service.serviceprovider.SqlServiceProvider;
import dk.digitalidentity.service.validation.LogoutRequestValidationService;
import dk.digitalidentity.util.Constants;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import jakarta.servlet.http.HttpServletRequest;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.xml.BasicParserPool;

@Service
public class LogoutRequestService {

	@Autowired
	private OpenSAMLHelperService samlHelper;

	@Autowired
	private LogoutRequestValidationService validationService;

	@Autowired
	private CredentialService credentialService;

	@Autowired
	private SessionHelper sessionHelper;

	@Autowired
	private OS2faktorConfiguration configuration;

	public MessageContext<SAMLObject> getMessageContext(HttpServletRequest request) throws ResponderException, RequesterException {

		try {
			BaseHttpServletRequestXMLMessageDecoder<SAMLObject> decoder = "POST".equals(request.getMethod()) ? new HTTPPostDecoder() : new HTTPRedirectDeflateDecoder();

			decoder.setHttpServletRequest(request);

			BasicParserPool parserPool = new BasicParserPool();
			parserPool.initialize();
			decoder.setParserPool(parserPool);

			decoder.initialize();
			decoder.decode();

			MessageContext<SAMLObject> msgContext = decoder.getMessageContext();
			decoder.destroy();

			return msgContext;
		}
		catch (ComponentInitializationException e) {
			throw new ResponderException("Kunne ikke initialisere afkoder", e);
		}
		catch (MessageDecodingException e) {
			throw new RequesterException("Kunne ikke afkode forespørgsel", e);
		}
	}

	public LogoutRequest getLogoutRequest(MessageContext<SAMLObject> messageContext) throws RequesterException {
		// Sometimes we receive LogoutResponses on the LogoutRequest endpoint,
		// this will show a nice error message if that happens
		SAMLObject message = messageContext.getMessage();

		if (!(message instanceof LogoutRequest)) {
			String errMsg = "Besked indhold burde være LogoutRequest";

			if (message != null && message.getClass() != null) {
				errMsg += " men er af typen: " + message.getClass().getName();
			}
			throw new RequesterException(errMsg);
		}

		return (LogoutRequest) message;
	}

	public void validateLogoutRequest(HttpServletRequest request, MessageContext<SAMLObject> messageContext, EntityDescriptor metadata, ServiceProvider serviceProvider) throws RequesterException, ResponderException {
		validationService.validate(request, messageContext, metadata, serviceProvider);
	}

	public MessageContext<SAMLObject> createMessageContextWithLogoutRequest(LogoutRequest logoutRequest, String destination, ServiceProvider serviceProvider) throws ResponderException, RequesterException {
		// Create message context
		MessageContext<SAMLObject> messageContext = new MessageContext<>();

		// Create AuthnRequest
		LogoutRequest outgoingLogoutRequest = createLogoutRequest(logoutRequest, destination, serviceProvider);
		messageContext.setMessage(outgoingLogoutRequest);

		// Destination
		SAMLPeerEntityContext peerEntityContext = messageContext.getSubcontext(SAMLPeerEntityContext.class, true);
		SAMLEndpointContext endpointContext = peerEntityContext.getSubcontext(SAMLEndpointContext.class, true);

		SingleSignOnService endpoint = samlHelper.buildSAMLObject(SingleSignOnService.class);
		endpointContext.setEndpoint(endpoint);

		endpoint.setBinding(SAMLConstants.SAML2_REDIRECT_BINDING_URI);
		endpoint.setLocation(destination);

		// Signing info
		BasicX509Credential x509Credential = null;
		if (serviceProvider instanceof SqlServiceProvider sp && Objects.equals(sp.getCertificateAlias(), KnownCertificateAliases.SELFSIGNED.toString())) {
			x509Credential = credentialService.getSelfsignedX509Credential();
		}
		else {
			x509Credential = credentialService.getBasicX509Credential();
		}

		SignatureSigningParameters signatureSigningParameters = new SignatureSigningParameters();
		signatureSigningParameters.setSigningCredential(x509Credential);
		signatureSigningParameters.setSignatureAlgorithm(SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA256);
		messageContext.getSubcontext(SecurityParametersContext.class, true).setSignatureSigningParameters(signatureSigningParameters);

		return messageContext;
	}

	private LogoutRequest createLogoutRequest(LogoutRequest logoutRequest, String destination, ServiceProvider serviceProvider) throws ResponderException, RequesterException {
		LogoutRequest outgoingLR = samlHelper.buildSAMLObject(LogoutRequest.class);

		if (logoutRequest != null) {
			outgoingLR.setID(logoutRequest.getID());
		}
		else {
			outgoingLR.setID(UUID.randomUUID().toString());
		}

		outgoingLR.setDestination(destination);
		outgoingLR.setIssueInstant(new DateTime());

		if (logoutRequest != null && StringUtils.hasLength(logoutRequest.getReason())) {
			outgoingLR.setReason(logoutRequest.getReason());
		}
		else if (logoutRequest == null) {
			outgoingLR.setReason(LogoutRequest.USER_REASON);
		}

		// Create Issuer
		Issuer issuer = samlHelper.buildSAMLObject(Issuer.class);
		outgoingLR.setIssuer(issuer);

		issuer.setValue(configuration.getEntityId());

		// Copy NameID
		NameID nameID = samlHelper.buildSAMLObject(NameID.class);
		outgoingLR.setNameID(nameID);

		nameID.setFormat(serviceProvider.getNameIdFormat());
		nameID.setValue(serviceProvider.getNameId(sessionHelper.getPerson()));

		Map<String, String> map = sessionHelper.getServiceProviderSessions().get(serviceProvider.getEntityId());

		if (map != null) {
			SessionIndex sessionIndex = samlHelper.buildSAMLObject(SessionIndex.class);
			outgoingLR.getSessionIndexes().add(sessionIndex);
			sessionIndex.setSessionIndex(map.get(Constants.SESSION_INDEX));
		}
		
		signLogoutRequest(outgoingLR, serviceProvider);

		return outgoingLR;
	}

	// TODO: should sign with SP specific certificate
	private void signLogoutRequest(LogoutRequest logoutRequest, ServiceProvider serviceProvider) throws ResponderException {
		Signature signature = samlHelper.buildSAMLObject(Signature.class);

		BasicX509Credential x509Credential = null;
		if (serviceProvider != null && serviceProvider instanceof SqlServiceProvider sp && Objects.equals(sp.getCertificateAlias(), KnownCertificateAliases.SELFSIGNED.toString())) {
			x509Credential = credentialService.getSelfsignedX509Credential();
		}
		else {
			x509Credential = credentialService.getBasicX509Credential();
		}

		SignatureRSASHA256 signatureRSASHA256 = new SignatureRSASHA256();

		signature.setSigningCredential(x509Credential);
		signature.setCanonicalizationAlgorithm(CanonicalizationMethod.EXCLUSIVE);
		signature.setSignatureAlgorithm(signatureRSASHA256.getURI());
		signature.setKeyInfo(credentialService.getPublicKeyInfo(x509Credential));
		logoutRequest.setSignature(signature);

		try {
			LogoutRequestMarshaller marshaller = new LogoutRequestMarshaller();
			marshaller.marshall(logoutRequest);

			Signer.signObject(signature);
		}
		catch (MarshallingException e) {
			throw new ResponderException("Kunne ikke omforme Logud anmodning før signering", e);
		}
		catch (SignatureException e) {
			throw new ResponderException("Kunne ikke signere Logud anmodning", e);
		}
	}
}
