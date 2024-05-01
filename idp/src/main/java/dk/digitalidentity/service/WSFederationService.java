package dk.digitalidentity.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.AttributeStatement;
import org.opensaml.saml.saml2.core.NameID;
import org.opensaml.saml.saml2.core.Subject;
import org.opensaml.saml.saml2.core.SubjectConfirmation;
import org.opensaml.saml.saml2.core.impl.AttributeStatementMarshaller;
import org.opensaml.saml.saml2.core.impl.AttributeStatementUnmarshaller;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.ModelAndView;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.controller.dto.LoginRequest;
import dk.digitalidentity.controller.wsfederation.dto.WSFedRequestDTO;
import dk.digitalidentity.service.serviceprovider.ServiceProvider;
import dk.digitalidentity.service.serviceprovider.ServiceProviderFactory;
import dk.digitalidentity.service.serviceprovider.SqlServiceProvider;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import lombok.extern.slf4j.Slf4j;
import net.shibboleth.utilities.java.support.security.RandomIdentifierGenerationStrategy;

import static dk.digitalidentity.util.XMLUtil.copyXMLObject;

@Slf4j
@Service
@EnableCaching
@EnableScheduling
public class WSFederationService {
	private static final String WS_TRUST_NS = "http://docs.oasis-open.org/ws-sx/ws-trust/200512";
	private static final String WS_UTILITY_NS = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd";
	private static final String WS_ADDRESSING_NS = "http://www.w3.org/2005/08/addressing";

	@Autowired
	private AssertionService assertionService;

	@Autowired
	private SessionHelper sessionHelper;

	@Autowired
	private AuditLogger auditLogger;

	@Autowired
	private ServiceProviderFactory serviceProviderFactory;

    @Autowired
    private WSFederationService self;

	public ModelAndView sendSecurityTokenResponse(Model model, Document requestSecurityTokenResponseDoc, Person person, ServiceProvider serviceProvider, LoginRequest loginRequest) throws ResponderException {
		try {
			String requestSecurityTokenResponse = stringifyDoc(requestSecurityTokenResponseDoc);
			model.addAttribute("wresult", requestSecurityTokenResponse);
			auditLogger.wsFederationLogin(person, serviceProvider.getName(loginRequest), requestSecurityTokenResponse);

			model.addAttribute("wreply", loginRequest.getDestination());
			model.addAttribute("wctx", sessionHelper.getRelayState());
		}
		catch (TransformerException ex) {
			log.warn("Could not send SecurityToken for WSFed", ex);
			throw new ResponderException("Could not send SecurityToken for WSFed", ex);
		}

		return new ModelAndView("wsFederation/loginResponse.html", model.asMap());
	}

	public ModelAndView createAndSendSecurityTokenResponse(Model model, Person person, ServiceProvider serviceProvider, LoginRequest loginRequest) throws ResponderException {
		// we "just" need to supply the model with either a SOAP Fault or a Security Token and then put it in a HTML form that is autoposted towards the reply url supplied by the ServiceProvider OR in the loginRequest
		try {
			Document response = createRequestSecurityTokenResponse(person, serviceProvider, loginRequest);
			return sendSecurityTokenResponse(model, response, person, serviceProvider, loginRequest);
		}
		catch (MarshallingException | RequesterException | IOException | ResponderException ex) {
			log.warn("Could not create SecurityToken for WSFed", ex);
			throw new ResponderException("Could not create SecurityToken for WSFed", ex);
		}
	}

	public ModelAndView createAndSendBrokeredSecurityTokenResponse(Model model, LoginRequest loginRequest, Assertion assertion, ServiceProvider serviceProvider) throws ResponderException {
		try {
			Document response = createBrokeredRequestSecurityTokenResponse(assertion, serviceProvider, loginRequest);
			return sendSecurityTokenResponse(model, response, null, serviceProvider, loginRequest);
		}
		catch (MarshallingException | RequesterException | IOException | ResponderException ex) {
			log.warn("Could not create SecurityToken for WSFed", ex);
			throw new ResponderException("Could not create SecurityToken for WSFed", ex);
		}
	}

	public void validateLoginParameters(WSFedRequestDTO loginParameters) throws RequesterException, ResponderException {
		// wa is a required field - it must be set to wsignin1.0
		if (!"wsignin1.0".equals(loginParameters.getWa())) {
			throw new RequesterException("Wrong action and endpoint");
		}

		// wtrealm is required for this profile - it should be the unique id of the ServiceProvider (entityId, often a url)
		if (!StringUtils.hasLength(loginParameters.getWtrealm())) {
			throw new RequesterException("No ServiceProvider supplied");
		}

		// this will throw RequesterException if no ServiceProvider matches the supplied id
		ServiceProvider serviceProvider = serviceProviderFactory.getServiceProvider(loginParameters.getWtrealm());

		// wreply is optional
		// It is used for supplying the destination URL, if defined it should probably be contained in the metadata of the SP otherwise anyone can receive a token not just the trusted source.
		// If not defined a URL is determined from metadata
		if (StringUtils.hasLength(loginParameters.getWreply())) {
			Set<String> approvedEndpoints = getApprovedEndpoints(serviceProvider);

			if (!approvedEndpoints.contains(loginParameters.getWreply())) {
				log.warn("Found " + approvedEndpoints.size() + " approved endpoints (" + approvedEndpoints.toString() + "), but " + loginParameters.getWreply() + " is not one of them");
				throw new RequesterException("Unauthorized WReply endpoint: " + loginParameters.getWreply());
			}
		}
	}

	@Cacheable(value = "approved_endpoints", key = "#serviceProvider.getEntityId()")
	public Set<String> getApprovedEndpoints(ServiceProvider serviceProvider) throws ResponderException {
		Element metadata = null;
		try {
			metadata = fetchXMLMetadata(serviceProvider);
		}
		catch (ParserConfigurationException | SAXException | IOException e) {
			log.warn("Failed to parse metadata", e);
			throw new ResponderException("Could not fetch metadata");
		}

		Node roleDescriptor = getRoleDescriptor(metadata);
		if (roleDescriptor == null) {
			throw new ResponderException("No matching role descriptor found");
		}

		return getAllowedEndpoints(roleDescriptor);
	}

	@CacheEvict(value = { "approved_endpoints"}, allEntries = true)
	public void cacheEvict() {
		;
	}

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void processChanges() {
    	self.cacheEvict();
    }

	private Set<String> getAllowedEndpoints(Node roleDescriptor) {
		Set<String> allowedEndpoints = new HashSet<>();

		NodeList roleDescriptorChildNodes = roleDescriptor.getChildNodes();
		for (int i = 0; i < roleDescriptorChildNodes.getLength(); i++) {
			Node passiveRequestorEndpoint = roleDescriptorChildNodes.item(i);
			if (!"PassiveRequestorEndpoint".equals(passiveRequestorEndpoint.getLocalName())) {
				continue;
			}

			NodeList passiveRequestorEndpointChildNodes = passiveRequestorEndpoint.getChildNodes();
			for (int j = 0; j < passiveRequestorEndpointChildNodes.getLength(); j++) {
				Node endpointReference = passiveRequestorEndpointChildNodes.item(j);
				if (!"EndpointReference".equals(endpointReference.getLocalName())) {
					continue;
				}

				NodeList endpointReferenceNodes = endpointReference.getChildNodes();
				for (int k = 0; k < endpointReferenceNodes.getLength(); k++) {
					Node address = endpointReferenceNodes.item(k);
					if ("Address".equals(address.getLocalName())) {
						allowedEndpoints.add(address.getTextContent());
					}
				}
			}
		}

		return allowedEndpoints;
	}

	private Node getRoleDescriptor(Element metadata) throws ResponderException {
		NodeList roleDescriptors = metadata.getElementsByTagName("RoleDescriptor");
		if (roleDescriptors.getLength() <= 0) {
			throw new ResponderException("no role descriptor found");
		}

		Node roleDescriptor = null;
		Node roleDescriptorFallback = null;
		for (int i = 0; i < roleDescriptors.getLength(); i++) {
			Node item = roleDescriptors.item(i);
			NamedNodeMap itemAttributes = item.getAttributes();
			if (itemAttributes == null || itemAttributes.getLength() < 1) {
				continue;
			}

			Node namedItem = itemAttributes.getNamedItem("xsi:type");
			if (namedItem == null) {
				continue;
			}

			if (namedItem.getTextContent().endsWith("SecurityTokenServiceType")) {
				roleDescriptorFallback = item;
			}
			else if (namedItem.getTextContent().endsWith("ApplicationServiceType")) {
				roleDescriptor = item;
			}
		}

		if (roleDescriptor != null) {
			return roleDescriptor;
		}
		
		return roleDescriptorFallback;
	}

	private Element fetchXMLMetadata(ServiceProvider serviceProvider) throws ParserConfigurationException, SAXException, IOException, ResponderException {
		Element response = null;

		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setNamespaceAware(true);
		
		DocumentBuilder builder = factory.newDocumentBuilder();

		if (serviceProvider instanceof SqlServiceProvider) {
			SqlServiceProvider sp = (SqlServiceProvider) serviceProvider;

			String metadataUrl = sp.getMetadataUrl();
			if (StringUtils.hasLength(metadataUrl)) {
				response = builder.parse(URI.create(metadataUrl).toURL().openStream()).getDocumentElement();
				response.normalize();
				return response;
			}
			else {
				response = builder.parse(new ByteArrayInputStream(sp.getMetadataContent().getBytes())).getDocumentElement();
				response.normalize();
				return response;
			}
		}
		
		throw new ResponderException("Could not fetch metadata - none configured");
	}

	private String stringifyDoc(Document response) throws TransformerException {
		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		Transformer transformer = transformerFactory.newTransformer();
		transformer.setOutputProperty("omit-xml-declaration", "yes");
		StringWriter stringWriter = new StringWriter();
		transformer.transform(new DOMSource(response), new StreamResult(stringWriter));

		return stringWriter.toString();
	}

	private Document createRequestSecurityTokenResponse(Person person, ServiceProvider serviceProvider, LoginRequest loginRequest) throws IOException, MarshallingException, RequesterException, ResponderException {
		Element response = getSuccessResponseFile();

		// modify body to include required information
		insertLifetime(response);
		insertAppliesTo(response, serviceProvider.getEntityId());

		// Create assertion and insert into response
		RandomIdentifierGenerationStrategy secureRandomIdGenerator = new RandomIdentifierGenerationStrategy();
		String assertionId = secureRandomIdGenerator.generateIdentifier();
		DateTime issueInstant = DateTime.now();
		Assertion assertion = assertionService.createAssertion(assertionId, issueInstant, loginRequest, person, serviceProvider, loginRequest.getReturnURL(), loginRequest.getServiceProviderId(), null, null);
		assertionService.signAssertion(assertion);

		insertAssertion(response, assertionId, assertion);

		return response.getOwnerDocument();
	}

	private Document createBrokeredRequestSecurityTokenResponse(Assertion nemLogInAssertion, ServiceProvider serviceProvider, LoginRequest loginRequest) throws IOException, MarshallingException, RequesterException, ResponderException {
		Element response = getSuccessResponseFile();

		// modify body to include required information
		insertLifetime(response);
		insertAppliesTo(response, serviceProvider.getEntityId());

		// Create assertion and insert into response
		RandomIdentifierGenerationStrategy secureRandomIdGenerator = new RandomIdentifierGenerationStrategy();
		String assertionId = secureRandomIdGenerator.generateIdentifier();
		DateTime issueInstant = DateTime.now();
		Assertion assertion = createBrokeredAssertionForWSFed(loginRequest, assertionId, issueInstant, nemLogInAssertion, serviceProvider);
		assertionService.signAssertion(assertion);

		insertAssertion(response, assertionId, assertion);

		return response.getOwnerDocument();
	}

	private Assertion createBrokeredAssertionForWSFed(LoginRequest loginRequest, String assertionId, DateTime issueInstant, Assertion assertion, ServiceProvider serviceProvider) throws ResponderException {
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
		
		// AttributeStatements
		// Copy Attributes
		List<AttributeStatement> attributeStatements = new ArrayList<>();
		for (AttributeStatement statement : assertion.getAttributeStatements()) {
			AttributeStatement attributeStatement = null;
			attributeStatement = (AttributeStatement) copyXMLObject(statement, new AttributeStatementMarshaller(), new AttributeStatementUnmarshaller());
			attributeStatements.add(attributeStatement);
		}

		return assertionService.createAssertion(assertionId, issueInstant, authnInstant, serviceProvider, loginRequest.getReturnURL(), loginRequest.getServiceProviderId(), null, authnContextClassRef, nameIdFormat, nameIdValue, subjectConfirmationMethod, notOnOrAfter, attributeStatements);
	}

	private Element getSuccessResponseFile() throws IOException, ResponderException {
		// get Base SOAP message that needs to be filled out
		Element response;
		try (InputStream baseSuccessResponseFile = this.getClass().getClassLoader().getResourceAsStream("baseSuccessResponse.xml")) {
			try {
				DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
				factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
				factory.setNamespaceAware(true);

				DocumentBuilder builder = factory.newDocumentBuilder();
				response = builder.parse(baseSuccessResponseFile).getDocumentElement();
				response.normalize();
			}
			catch (SAXException | IOException | ParserConfigurationException e) {
				throw new ResponderException("Failed to get base xml file for login");
			}
		}
		return response;
	}

	private void insertAssertion(Element response, String assertionId, Assertion assertion) throws MarshallingException, IOException, RequesterException, ResponderException {
		// add Assertion to response message
		NodeList securityTokenList = response.getElementsByTagNameNS(WS_TRUST_NS, "RequestedSecurityToken");
		if (securityTokenList.getLength() != 1) {
			throw new IOException("Base file was not as expected");
		}
		Node requestedSecurityToken = securityTokenList.item(0);


		// insert assertion in Document
		Element marshalledAssertion = OpenSAMLHelperService.marshallObject(assertion);
		Node importedAssertionNode = response.getOwnerDocument().importNode(marshalledAssertion, true);
		requestedSecurityToken.appendChild(importedAssertionNode);

		// add AttachedReference KeyIdentifiers
		NodeList keyIdentifiers = response.getElementsByTagName("KeyIdentifier");
		for (int i = 0; i < keyIdentifiers.getLength(); i++) {
			keyIdentifiers.item(i).setTextContent(assertionId);
		}
	}

	private void insertLifetime(Element response) throws IOException {
		// add Assertion to response message
		NodeList createdList = response.getElementsByTagNameNS(WS_UTILITY_NS, "Created");
		if (createdList.getLength() != 1) {
			throw new IOException("Base file was not as expected");
		}
		Node created = createdList.item(0);

		DateTime dt = new DateTime(DateTimeZone.UTC);
		created.setTextContent(dt.toString());

		// add Assertion to response message
		NodeList expiresList = response.getElementsByTagNameNS(WS_UTILITY_NS, "Expires");
		if (expiresList.getLength() != 1) {
			throw new IOException("Base file was not as expected");
		}
		Node expires = expiresList.item(0);

		expires.setTextContent(dt.plusHours(1).toString());
	}

	private void insertAppliesTo(Element response, String audience) throws IOException {
		// add Assertion to response message
		NodeList appliesToList = response.getElementsByTagNameNS(WS_ADDRESSING_NS, "Address");
		if (appliesToList.getLength() != 1) {
			throw new IOException("Base file was not as expected");
		}
		
		Node appliesTo = appliesToList.item(0);
		appliesTo.setTextContent(audience);
	}
}
