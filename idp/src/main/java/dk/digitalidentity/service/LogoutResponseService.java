package dk.digitalidentity.service;

import java.security.PublicKey;
import java.security.Security;

import javax.xml.crypto.dsig.CanonicalizationMethod;

import org.joda.time.DateTime;
import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.messaging.context.MessageContext;
import org.opensaml.saml.common.SAMLObject;
import org.opensaml.saml.common.messaging.context.SAMLEndpointContext;
import org.opensaml.saml.common.messaging.context.SAMLPeerEntityContext;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.LogoutRequest;
import org.opensaml.saml.saml2.core.LogoutResponse;
import org.opensaml.saml.saml2.core.Status;
import org.opensaml.saml.saml2.core.StatusCode;
import org.opensaml.saml.saml2.core.impl.LogoutResponseMarshaller;
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

import dk.digitalidentity.aws.kms.jce.provider.rsa.KmsRSAPrivateKey;
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.service.validation.LogoutResponseValidationService;
import dk.digitalidentity.util.HttpRedirectUtil;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import jakarta.servlet.http.HttpServletRequest;
import net.shibboleth.utilities.java.support.security.RandomIdentifierGenerationStrategy;

@Service
public class LogoutResponseService {

	@Autowired
	private OpenSAMLHelperService samlHelper;

	@Autowired
	private LogoutResponseValidationService validationService;

	@Autowired
	private CredentialService credentialService;

	@Autowired
	private OS2faktorConfiguration configuration;

	public MessageContext<SAMLObject> getMessageContext(HttpServletRequest request) throws RequesterException, ResponderException {
		return HttpRedirectUtil.getMessageContext(request);
	}

	public LogoutResponse getLogoutResponse(MessageContext<SAMLObject> messageContext) {
		return (LogoutResponse) messageContext.getMessage();
	}

	public void validateLogoutResponse(HttpServletRequest request, MessageContext<SAMLObject> messageContext, String metadataEntityID, PublicKey publicKey, LogoutRequest logoutRequest)
			throws ResponderException {
		validationService.validate(request, messageContext, metadataEntityID, publicKey, logoutRequest);
	}

	public MessageContext<SAMLObject> createMessageContextWithLogoutResponse(LogoutRequest logoutRequest, String destination, String binding) throws ResponderException {
		// Create message context
		MessageContext<SAMLObject> messageContext = new MessageContext<>();

		// Create LogoutResponse
		LogoutResponse logoutResponse = createLogoutResponse(destination, logoutRequest, SAMLConstants.SAML2_POST_BINDING_URI.equals(binding));
		messageContext.setMessage(logoutResponse);

		// Destination
		SAMLPeerEntityContext peerEntityContext = messageContext.getSubcontext(SAMLPeerEntityContext.class, true);
		SAMLEndpointContext endpointContext = peerEntityContext.getSubcontext(SAMLEndpointContext.class, true);

		SingleSignOnService endpoint = samlHelper.buildSAMLObject(SingleSignOnService.class);
		endpoint.setBinding(SAMLConstants.SAML2_REDIRECT_BINDING_URI);
		endpoint.setLocation(destination);

		endpointContext.setEndpoint(endpoint);

		// Signing info
		SignatureSigningParameters signatureSigningParameters = new SignatureSigningParameters();
		signatureSigningParameters.setSigningCredential(credentialService.getBasicX509Credential());
		signatureSigningParameters.setSignatureAlgorithm(SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA256);
		messageContext.getSubcontext(SecurityParametersContext.class, true).setSignatureSigningParameters(signatureSigningParameters);

		return messageContext;
	}

	private LogoutResponse createLogoutResponse(String destination, LogoutRequest logoutRequest, boolean signLogoutResponseObject) throws ResponderException {
		LogoutResponse logoutResponse = samlHelper.buildSAMLObject(LogoutResponse.class);

		RandomIdentifierGenerationStrategy randomIdentifierGenerator = new RandomIdentifierGenerationStrategy();
		String id = randomIdentifierGenerator.generateIdentifier();

		logoutResponse.setID(id);
		logoutResponse.setDestination(destination);
		logoutResponse.setIssueInstant(new DateTime());
		logoutResponse.setInResponseTo(logoutRequest.getID());

		// Create Issuer
		Issuer issuer = samlHelper.buildSAMLObject(Issuer.class);
		logoutResponse.setIssuer(issuer);
		issuer.setValue(configuration.getEntityId());

		Status status = samlHelper.buildSAMLObject(Status.class);
		logoutResponse.setStatus(status);

		StatusCode statusCode = samlHelper.buildSAMLObject(StatusCode.class);
		status.setStatusCode(statusCode);
		statusCode.setValue("urn:oasis:names:tc:SAML:2.0:status:Success");

		// Sign LogoutResponse, we only do this for post, for HTTP-Redirect the message is signed
		if (signLogoutResponseObject) {
			signLogoutResponse(logoutResponse);
		}

		return logoutResponse;
	}

	private void signLogoutResponse(LogoutResponse logoutResponse) throws ResponderException {
		// Prepare Assertion for Signing
		Signature signature = samlHelper.buildSAMLObject(Signature.class);

		BasicX509Credential x509Credential = credentialService.getBasicX509Credential();
		SignatureRSASHA256 signatureRSASHA256 = new SignatureRSASHA256();

		signature.setSigningCredential(x509Credential);
		signature.setCanonicalizationAlgorithm(CanonicalizationMethod.EXCLUSIVE);
		signature.setSignatureAlgorithm(signatureRSASHA256.getURI());
		signature.setKeyInfo(credentialService.getPublicKeyInfo());
		logoutResponse.setSignature(signature);

		// Sign Logout Response
		try {
			// If the object hasnt been marshalled first it can't be signed
			LogoutResponseMarshaller marshaller = new LogoutResponseMarshaller();
			
			// when using KMS keys, make sure to use the KMS provider
			if (x509Credential.getPrivateKey() instanceof KmsRSAPrivateKey) {
				marshaller.marshall(logoutResponse, Security.getProvider("KMS"));
			}
			else {
				marshaller.marshall(logoutResponse);
			}

			Signer.signObject(signature);
		}
		catch (MarshallingException e) {
			throw new ResponderException("Kunne ikke omforme Logud svar før signering", e);
		}
		catch (SignatureException e) {
			throw new ResponderException("Kunne ikke signere Logud svar", e);
		}
	}
}
