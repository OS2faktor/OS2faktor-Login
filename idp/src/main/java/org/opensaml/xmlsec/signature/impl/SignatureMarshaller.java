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

package org.opensaml.xmlsec.signature.impl;

import java.security.Provider;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import net.shibboleth.utilities.java.support.xml.ElementSupport;

import org.apache.xml.security.Init;
import org.apache.xml.security.exceptions.XMLSecurityException;
import org.apache.xml.security.signature.XMLSignature;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.io.Marshaller;
import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.xmlsec.algorithm.AlgorithmSupport;
import org.opensaml.xmlsec.signature.KeyInfo;
import org.opensaml.xmlsec.signature.Signature;
import org.opensaml.xmlsec.signature.support.ContentReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

//OBS! This is a copy from OpenSAML 3.4.6
//the only modification in this class is
//
//

/**
 * A marshaller for {@link org.opensaml.xmlsec.signature.Signature} objects. This marshaller is really a no-op class.
 * All the creation of the signature DOM elements is handled by {@link org.opensaml.xmlsec.signature.support.Signer}
 * when it signs the object.
 */
public class SignatureMarshaller implements Marshaller {

    /** Class logger. */
    private final Logger log = LoggerFactory.getLogger(SignatureMarshaller.class);

    /** Constructor. */
    public SignatureMarshaller() {
        if (!Init.isInitialized()) {
            log.debug("Initializing XML security library");
            Init.init();
        }
    }

    /** {@inheritDoc} */
    public Element marshall(final XMLObject xmlObject) throws MarshallingException {
        try {
            final Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            return marshall(xmlObject, document);
        } catch (final ParserConfigurationException e) {
            throw new MarshallingException("Unable to create Document to place marshalled elements in", e);
        }
    }

    // OBS! new method
    /** {@inheritDoc} */
    public Element marshall(final XMLObject xmlObject, final Element parentElement, Provider provider) throws MarshallingException {
        final Element signatureElement = createSignatureElement((SignatureImpl) xmlObject, parentElement.getOwnerDocument(), provider);
        ElementSupport.appendChildElement(parentElement, signatureElement);
        return signatureElement;
    }
    
    // OBS! wrapper for above
    /** {@inheritDoc} */
    public Element marshall(final XMLObject xmlObject, final Element parentElement) throws MarshallingException {
    	return marshall(xmlObject, parentElement, null);
    }

    /** {@inheritDoc} */
    public Element marshall(final XMLObject xmlObject, final Document document) throws MarshallingException {
        final Element signatureElement = createSignatureElement((SignatureImpl) xmlObject, document, null);

        final Element documentRoot = document.getDocumentElement();
        if (documentRoot != null) {
            document.replaceChild(signatureElement, documentRoot);
        } else {
            document.appendChild(signatureElement);
        }

        return signatureElement;
    }

    /**
     * Creates the signature elements but does not compute the signature.
     * 
     * @param signature the XMLObject to be signed
     * @param document the owning document
     * 
     * @return the Signature element
     * 
     * @throws MarshallingException thrown if the signature can not be constructed
     */
    private Element createSignatureElement(final Signature signature, final Document document, Provider provider) throws MarshallingException {
        log.debug("Starting to marshall {}", signature.getElementQName());

        // OBS! add this to track where we are called from, if the provider is needed but not provided
        // new Exception("provider is " + provider).printStackTrace();
        
        try {
            log.debug("Creating XMLSignature object");
            XMLSignature dsig = null;
            if (signature.getHMACOutputLength() != null && AlgorithmSupport.isHMAC(signature.getSignatureAlgorithm())) {
                dsig = new XMLSignature(document, "", signature.getSignatureAlgorithm(), signature
                        .getHMACOutputLength(), signature.getCanonicalizationAlgorithm());
            } else {
            	// OBS! we added the provider argument here
                dsig = new XMLSignature(document, "", signature.getSignatureAlgorithm(), signature.getCanonicalizationAlgorithm(), provider);
            }

            log.debug("Adding content to XMLSignature.");
            for (final ContentReference contentReference : signature.getContentReferences()) {
                contentReference.createReference(dsig);
            }

            log.debug("Creating Signature DOM element");
            final Element signatureElement = dsig.getElement();

            if (signature.getKeyInfo() != null) {
                final Marshaller keyInfoMarshaller = XMLObjectProviderRegistrySupport.getMarshallerFactory()
                        .getMarshaller(KeyInfo.DEFAULT_ELEMENT_NAME);
                keyInfoMarshaller.marshall(signature.getKeyInfo(), signatureElement);
            }

            ((SignatureImpl) signature).setXMLSignature(dsig);
            signature.setDOM(signatureElement);
            signature.releaseParentDOM(true);
            return signatureElement;

        } catch (final XMLSecurityException e) {
            final String msg = "Unable to construct signature Element " + signature.getElementQName(); 
            log.error(msg, e);
            throw new MarshallingException(msg, e);
        }
    }

}