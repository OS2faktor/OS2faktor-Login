/*
 * Licensed to the University Corporation for Advanced Internet Development,
 * Inc. (UCAID) under one or more contributor license agreements.  See the
 * NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The UCAID licenses this file to You under the Apache
 * License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opensaml.core.xml.io;

import java.security.Provider;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.xml.namespace.QName;

import net.shibboleth.utilities.java.support.primitive.StringSupport;
import net.shibboleth.utilities.java.support.xml.ElementSupport;
import net.shibboleth.utilities.java.support.xml.NamespaceSupport;
import net.shibboleth.utilities.java.support.xml.QNameSupport;
import net.shibboleth.utilities.java.support.xml.XMLParserException;
import net.shibboleth.utilities.java.support.xml.XMLConstants;

import org.opensaml.core.xml.AttributeExtensibleXMLObject;
import org.opensaml.core.xml.Namespace;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.util.XMLObjectSupport;
import org.opensaml.xmlsec.signature.impl.SignatureMarshaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.google.common.base.Strings;

//OBS! This is a copy from OpenSAML 3.4.6
// the only modification in this class is
// - new methods for marshalling with a JCE Provider 
// - everything marked with OBS!

/**
 * A thread safe, abstract implementation of the {@link org.opensaml.core.xml.io.Marshaller} interface. 
 * This class handles most of the boilerplate code:
 * <ul>
 * <li>Ensuring elements to be marshalled are of either the correct xsi:type or element QName</li>
 * <li>Setting the appropriate namespace and prefix for the marshalled element</li>
 * <li>Setting the xsi:type for the element if the element has an explicit type</li>
 * <li>Setting namespaces attributes declared for the element</li>
 * <li>Marshalling of child elements</li>
 * </ul>
 */
public abstract class AbstractXMLObjectMarshaller implements Marshaller {

    /** Class logger. */
    @Nonnull private final Logger log = LoggerFactory.getLogger(AbstractXMLObjectMarshaller.class);

    /** Factory for XMLObject Marshallers. */
    @Nonnull private final MarshallerFactory marshallerFactory;

    /** Constructor. */
    protected AbstractXMLObjectMarshaller() {
        marshallerFactory = XMLObjectProviderRegistrySupport.getMarshallerFactory();
    }

    /** {@inheritDoc} */
    @Nonnull public Element marshall(@Nonnull final XMLObject xmlObject) throws MarshallingException {
        try {
            final Document document = XMLObjectProviderRegistrySupport.getParserPool().newDocument();
            return marshall(xmlObject, document);
        } catch (final XMLParserException e) {
            throw new MarshallingException("Unable to create Document to place marshalled elements in", e);
        }
    }
    
    // OBS! new method
    /** {@inheritDoc} */
    @Nonnull public Element marshall(@Nonnull final XMLObject xmlObject, Provider provider) throws MarshallingException {
        try {
            final Document document = XMLObjectProviderRegistrySupport.getParserPool().newDocument();
            return marshall(xmlObject, document, provider);
        } catch (final XMLParserException e) {
            throw new MarshallingException("Unable to create Document to place marshalled elements in", e);
        }
    }

    // OBS! existing method, wrapping new method
    /** {@inheritDoc} */
    @Nonnull public Element marshall(@Nonnull final XMLObject xmlObject, @Nonnull final Document document)
            throws MarshallingException {
    	return marshall(xmlObject, document, null);
    }
    
    // OBS! new method
    /** {@inheritDoc} */
    @Nonnull public Element marshall(@Nonnull final XMLObject xmlObject, @Nonnull final Document document, Provider provider)
            throws MarshallingException {
        Element domElement;

        log.trace("Starting to marshall {}", xmlObject.getElementQName());

        if (document == null) {
            throw new MarshallingException("Given document may not be null");
        }

        log.trace("Checking if {} contains a cached DOM representation", xmlObject.getElementQName());
        domElement = xmlObject.getDOM();
        if (domElement != null) {

            prepareForAdoption(xmlObject);

            if (domElement.getOwnerDocument() != document) {
                log.trace("Adopting DOM of XMLObject into given Document");
                ElementSupport.adoptElement(document, domElement);
            }

            log.trace("Setting DOM of XMLObject as document element of given Document");
            setDocumentElement(document, domElement);

            return domElement;
        }

        log.trace("{} does not contain a cached DOM representation. Creating Element to marshall into.", xmlObject
                .getElementQName());
        domElement = ElementSupport.constructElement(document, xmlObject.getElementQName());

        log.trace("Setting created element as document root");
        // we need to do this before the rest of the marshalling so that signing and other ID dependent operations have
        // a path to the document root
        setDocumentElement(document, domElement);

        domElement = marshallInto(xmlObject, domElement, provider);

        log.trace("Setting created element to DOM cache for XMLObject {}", xmlObject.getElementQName());
        xmlObject.setDOM(domElement);
        xmlObject.releaseParentDOM(true);

        return domElement;
    }

    /** {@inheritDoc} */
    @Nonnull public Element marshall(@Nonnull final XMLObject xmlObject, @Nonnull final Element parentElement)
            throws MarshallingException {
        Element domElement;

        log.trace("Starting to marshall {} as child of {}", xmlObject.getElementQName(), QNameSupport
                .getNodeQName(parentElement));

        if (parentElement == null) {
            throw new MarshallingException("Given parent element is null");
        }

        log.trace("Checking if {} contains a cached DOM representation", xmlObject.getElementQName());
        domElement = xmlObject.getDOM();
        if (domElement != null) {
            log.trace("{} contains a cached DOM representation", xmlObject.getElementQName());

            prepareForAdoption(xmlObject);

            log.trace("Appending DOM of XMLObject {} as child of parent element {}", xmlObject.getElementQName(),
                    QNameSupport.getNodeQName(parentElement));
            ElementSupport.appendChildElement(parentElement, domElement);

            return domElement;
        }

        log.trace("{} does not contain a cached DOM representation. Creating Element to marshall into.", xmlObject
                .getElementQName());
        final Document owningDocument = parentElement.getOwnerDocument();
        domElement = ElementSupport.constructElement(owningDocument, xmlObject.getElementQName());

        log.trace("Appending newly created element to given parent element");
        // we need to do this before the rest of the marshalling so that signing and other ID dependent operations have
        // a path to the document root
        ElementSupport.appendChildElement(parentElement, domElement);
        domElement = marshallInto(xmlObject, domElement);

        log.trace("Setting created element to DOM cache for XMLObject {}", xmlObject.getElementQName());
        xmlObject.setDOM(domElement);
        xmlObject.releaseParentDOM(true);

        return domElement;

    }

    /**
     * Sets the given element as the Document Element of the given Document. If the document already has a Document
     * Element it is replaced by the given element.
     * 
     * @param document the document
     * @param element the Element that will serve as the Document Element
     */
    protected void setDocumentElement(@Nonnull final Document document, @Nonnull final Element element) {
        final Element documentRoot = document.getDocumentElement();
        if (documentRoot != null) {
            document.replaceChild(element, documentRoot);
        } else {
            document.appendChild(element);
        }
    }

    // OBS! wrapper for old method
    @Nonnull protected Element marshallInto(@Nonnull final XMLObject xmlObject, @Nonnull final Element targetElement)
            throws MarshallingException {
    	
    	return marshallInto(xmlObject, targetElement, null);
    }
    
    
    // OBS! old method, with provider added
    /**
     * Marshalls the given XMLObject into the given DOM Element. The DOM Element must be within a DOM tree whose root is
     * the Document Element of the Document that owns the given DOM Element.
     * 
     * @param xmlObject the XMLObject to marshall
     * @param targetElement the Element into which the XMLObject is marshalled into
     * 
     * @return the DOM element the {@link XMLObject} is marshalled into
     * 
     * @throws MarshallingException thrown if there is a problem marshalling the object
     */
    @Nonnull protected Element marshallInto(@Nonnull final XMLObject xmlObject, @Nonnull final Element targetElement, Provider provider)
            throws MarshallingException {
        log.trace("Setting namespace prefix for {} for XMLObject {}", xmlObject.getElementQName().getPrefix(),
                xmlObject.getElementQName());

        marshallNamespacePrefix(xmlObject, targetElement);

        marshallSchemaInstanceAttributes(xmlObject, targetElement);

        marshallNamespaces(xmlObject, targetElement);

        marshallAttributes(xmlObject, targetElement);

        marshallChildElements(xmlObject, targetElement, provider);

        marshallElementContent(xmlObject, targetElement);

        return targetElement;
    }

    /**
     * Marshalls the namespace prefix of the XMLObject into the DOM element.
     * 
     * @param xmlObject the XMLObject being marshalled
     * @param domElement the DOM element the XMLObject is being marshalled into
     */
    protected void marshallNamespacePrefix(@Nonnull final XMLObject xmlObject, @Nonnull final Element domElement) {
        final String prefix = StringSupport.trimOrNull(xmlObject.getElementQName().getPrefix());

        if (prefix != null) {
            domElement.setPrefix(prefix);
        }
    }

    // OBS! wrapper for old method
    protected void marshallChildElements(@Nonnull final XMLObject xmlObject, @Nonnull final Element domElement)
            throws MarshallingException {
    	
    	marshallChildElements(xmlObject, domElement, null);
    }
    
    // OBS! old method with Provider argument
    /**
     * Marshalls the child elements of the given XMLObject.
     * 
     * @param xmlObject the XMLObject whose children will be marshalled
     * @param domElement the DOM element that will recieved the marshalled children
     * 
     * @throws MarshallingException thrown if there is a problem marshalling a child element
     */
    protected void marshallChildElements(@Nonnull final XMLObject xmlObject, @Nonnull final Element domElement, Provider provider)
            throws MarshallingException {
        log.trace("Marshalling child elements for XMLObject {}", xmlObject.getElementQName());

        final List<XMLObject> childXMLObjects = xmlObject.getOrderedChildren();
        if (childXMLObjects != null && childXMLObjects.size() > 0) {
            for (final XMLObject childXMLObject : childXMLObjects) {
                if (childXMLObject == null) {
                    continue;
                }

                log.trace("Getting marshaller for child XMLObject {}", childXMLObject.getElementQName());
                Marshaller marshaller = marshallerFactory.getMarshaller(childXMLObject);

                if (marshaller == null) {
                    marshaller = 
                            marshallerFactory.getMarshaller(XMLObjectProviderRegistrySupport.getDefaultProviderQName());

                    if (marshaller == null) {
                        final String errorMsg = "No marshaller available for " + childXMLObject.getElementQName()
                                + ", child of " + xmlObject.getElementQName();
                        log.error(errorMsg);
                        throw new MarshallingException(errorMsg);
                    } else {
                        log.trace("No marshaller was registered for {}, child of {}. Using default marshaller",
                                childXMLObject.getElementQName(), xmlObject.getElementQName());
                    }
                }

                log.trace("Marshalling {} and adding it to DOM", childXMLObject.getElementQName());
                
                if (marshaller instanceof SignatureMarshaller) {
                	((SignatureMarshaller)marshaller).marshall(childXMLObject, domElement, provider);
                }
                else {
                	marshaller.marshall(childXMLObject, domElement);
                }
            }
        } else {
            log.trace("No child elements to marshall for XMLObject {}", xmlObject.getElementQName());
        }
    }

    /**
     * Creates the xmlns attributes for any namespaces set on the given XMLObject.
     * 
     * @param xmlObject the XMLObject
     * @param domElement the DOM element the namespaces will be added to
     */
    protected void marshallNamespaces(@Nonnull final XMLObject xmlObject, @Nonnull final Element domElement) {
        log.trace("Marshalling namespace attributes for XMLObject {}", xmlObject.getElementQName());
        final Set<Namespace> namespaces = xmlObject.getNamespaces();

        for (final Namespace namespace : namespaces) {
            log.trace("Candidate namespace from getNamespaces(): {}", namespace.toString());
            if (!xmlObject.getNamespaceManager().getNamespaceDeclarations().contains(namespace)) {
                log.trace("NamespaceManager getNamespaceDeclarations() did NOT contain namespace: {}",
                        namespace.toString());
                if (Objects.equals(namespace.getNamespacePrefix(), XMLConstants.XML_PREFIX)
                        || Objects.equals(namespace.getNamespaceURI(), XMLConstants.XML_NS)) {
                    //the "xml" namespace never needs to be declared
                    continue;
                }
                
                final String declared = NamespaceSupport.lookupNamespaceURI(domElement, null, 
                        namespace.getNamespacePrefix());
                log.trace("Lookup of prefix '{}' returned '{}'", namespace.getNamespacePrefix(),  declared);
                if (declared != null && namespace.getNamespaceURI().equals(declared)) {
                    log.trace("Namespace {} has already been declared on an ancestor of {} no need to add it here", 
                            namespace, xmlObject.getElementQName());
                    continue;
                }
            }
            log.trace("Adding namespace declaration {} to {}", namespace, xmlObject.getElementQName());
            final String nsURI = StringSupport.trimOrNull(namespace.getNamespaceURI());
            final String nsPrefix = StringSupport.trimOrNull(namespace.getNamespacePrefix());

            NamespaceSupport.appendNamespaceDeclaration(domElement, nsURI, nsPrefix);
        }
    }

    /**
     * Creates the XSI type, schemaLocation, and noNamespaceSchemaLocation attributes for an XMLObject.
     * 
     * @param xmlObject the XMLObject
     * @param domElement the DOM element the namespaces will be added to
     * 
     * @throws MarshallingException thrown if the schema type information is invalid
     */
    protected void marshallSchemaInstanceAttributes(@Nonnull final XMLObject xmlObject,
            @Nonnull final Element domElement) throws MarshallingException {

        if (!Strings.isNullOrEmpty(xmlObject.getSchemaLocation())) {
            log.trace("Setting xsi:schemaLocation for XMLObject {} to {}", xmlObject.getElementQName(), xmlObject
                    .getSchemaLocation());
            domElement.setAttributeNS(XMLConstants.XSI_NS, XMLConstants.XSI_PREFIX + ":schemaLocation", xmlObject
                    .getSchemaLocation());
        }

        if (!Strings.isNullOrEmpty(xmlObject.getNoNamespaceSchemaLocation())) {
            log.trace("Setting xsi:noNamespaceSchemaLocation for XMLObject {} to {}", xmlObject.getElementQName(),
                    xmlObject.getNoNamespaceSchemaLocation());
            domElement.setAttributeNS(XMLConstants.XSI_NS, XMLConstants.XSI_PREFIX + ":noNamespaceSchemaLocation",
                    xmlObject.getNoNamespaceSchemaLocation());
        }
        
        if (xmlObject.isNilXSBoolean() != null && xmlObject.isNil()) {
            log.trace("Setting xsi:nil for XMLObject {} to true", xmlObject.getElementQName());
            domElement.setAttributeNS(XMLConstants.XSI_NS, XMLConstants.XSI_PREFIX + ":nil",
                    xmlObject.isNilXSBoolean().toString());
        }

        final QName type = xmlObject.getSchemaType();
        if (type == null) {
            return;
        }

        log.trace("Setting xsi:type attribute with for XMLObject {}", xmlObject.getElementQName());
        final String typeLocalName = StringSupport.trimOrNull(type.getLocalPart());
        final String typePrefix = StringSupport.trimOrNull(type.getPrefix());

        if (typeLocalName == null) {
            throw new MarshallingException("The type QName on XMLObject " + xmlObject.getElementQName()
                    + " may not have a null local name");
        }

        if (type.getNamespaceURI() == null) {
            throw new MarshallingException("The type URI QName on XMLObject " + xmlObject.getElementQName()
                    + " may not have a null namespace URI");
        }

        final String attributeValue;
        if (typePrefix == null) {
            attributeValue = typeLocalName;
        } else {
            attributeValue = typePrefix + ":" + typeLocalName;
        }

        domElement.setAttributeNS(XMLConstants.XSI_NS, XMLConstants.XSI_PREFIX + ":type", attributeValue);
    }

    /**
     * Marshalls a given XMLObject into a W3C Element. The given signing context should be blindly passed to the
     * marshaller for child elements. The XMLObject passed to this method is guaranteed to be of the target name
     * specified during this unmarshaller's construction.
     * 
     * This default implementation of this method is a no-op
     * 
     * @param xmlObject the XMLObject to marshall
     * @param domElement the W3C DOM element
     * 
     * @throws MarshallingException thrown if there is a problem marshalling the element
     */
    protected void marshallAttributes(@Nonnull final XMLObject xmlObject, @Nonnull final Element domElement)
            throws MarshallingException{
        
    }

    /**
     * Marshalls data from the XMLObject into content of the DOM Element.
     * 
     * The default implementation of this method is a no-op.
     * 
     * @param xmlObject the XMLObject
     * @param domElement the DOM element recieving the content
     * 
     * @throws MarshallingException thrown if the textual content can not be added to the DOM element
     */
    protected void marshallElementContent(@Nonnull final XMLObject xmlObject, @Nonnull final Element domElement)
            throws MarshallingException{
        
    }

    /**
     * Prepares the given DOM caching XMLObject for adoption into another document. If the XMLObject has a parent then
     * all visible namespaces used by the given XMLObject and its descendants are declared within that subtree and the
     * parent's DOM is invalidated.
     * 
     * @param domCachingObject the XMLObject to prepare for adoption
     * 
     * @throws MarshallingException thrown if a namespace within the XMLObject's DOM subtree can not be resolved.
     */
    private void prepareForAdoption(@Nonnull final XMLObject domCachingObject) throws MarshallingException {
        if (domCachingObject.getParent() != null) {
            log.trace("Rooting all visible namespaces of XMLObject {} before adding it to new parent Element",
                    domCachingObject.getElementQName());
            try {
                NamespaceSupport.rootNamespaces(domCachingObject.getDOM());
            } catch (final DOMException e) {
                final String errorMsg = "Unable to root namespaces of cached DOM element, "
                        + domCachingObject.getElementQName();
                log.error(errorMsg, e);
                throw new MarshallingException(errorMsg, e);
            }

            log.trace("Release DOM of XMLObject parent");
            domCachingObject.releaseParentDOM(true);
        }
    }
    
    /**
     * Marshalls unknown attributes.
     * @param xmlObject the Object which has the unknown attributes/
     * @param domElement where to marshall them to.
     */
    protected void marshallUnknownAttributes(@Nonnull final AttributeExtensibleXMLObject xmlObject,
            @Nonnull final Element domElement) {
        XMLObjectSupport.marshallAttributeMap(xmlObject.getUnknownAttributes(), domElement);
    }
 
}
