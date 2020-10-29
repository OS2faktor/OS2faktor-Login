/*
    Copyright 2005 Carsten Raskgaard
    Copyright 2010 Nets DanID


    This file is part of OpenOcesAPI.

    OpenOcesAPI is free software; you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation; either version 2.1 of the License, or
    (at your option) any later version.

    OpenOcesAPI is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with OpenOcesAPI; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA


    Note to developers:
    If you add code to this file, please take a minute to add an additional
    @author statement below.
*/

/* $Id: OpensignAbstractSignature.java,v 1.9 2006/12/25 20:48:41 cara Exp $ */

package dk.digitalidentity.ooapi.signatures;

import org.apache.xml.security.exceptions.Base64DecodingException;
import org.apache.xml.security.exceptions.XMLSecurityException;
import org.apache.xml.security.signature.XMLSignature;
import org.apache.xml.security.signature.XMLSignatureException;
import org.apache.xml.security.utils.Base64;
import org.apache.xml.security.utils.IgnoreAllErrorHandler;
import org.apache.xml.security.utils.XMLUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import dk.digitalidentity.ooapi.certificate.OcesCertificate;
import dk.digitalidentity.ooapi.certificate.OcesCertificateFactory;
import dk.digitalidentity.ooapi.exceptions.InternalException;
import dk.digitalidentity.ooapi.exceptions.TrustCouldNotBeVerifiedException;
import dk.digitalidentity.ooapi.utils.SimpleNamspaceContext;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class is a high-level representation of the parts of an Opensign generated xmldsig signature, that is common for
 * Openlogon and Opensign
 *
 * @author Carsten Raskgaard  <carsten@raskgaard.dk>
 */

public abstract class OpensignAbstractSignature {
	protected final static String NAMESPACEURI_OPENOCES_R1 = "http://www.openoces.org/2003/10/signature#";
	protected final static String NAMESPACEURI_OPENOCES_R2 = "http://www.openoces.org/2006/07/signature#";

	protected Document doc = null;
    protected Element nscontext;
    protected Element sigElement = null;
    protected XMLSignature signature;    
    protected XPath xpath;

    protected OpensignAbstractSignature(Document doc) throws TransformerException, XMLSignatureException, XMLSecurityException, XPathExpressionException {
        this.doc = doc;
        nscontext = XMLUtils.createDSctx(doc, "ds", org.apache.xml.security.utils.Constants.SignatureSpecNS);
        XPathFactory factory = XPathFactory.newInstance();
        
        xpath = factory.newXPath();
        xpath.setNamespaceContext(new SimpleNamspaceContext());
        
        XPathExpression expr = xpath.compile("//ds:Signature[1]");
        sigElement = (Element) expr.evaluate(doc, XPathConstants.NODE);

        signature = new XMLSignature(sigElement, null);
    }

    public OcesCertificate getSigningCertificate() throws InternalException {
        try {
        	List<X509Certificate> certificates = new ArrayList<X509Certificate>();
        	for (int i = 0; i < signature.getKeyInfo().lengthX509Data(); i++) {
        		for (int j = 0; j < signature.getKeyInfo().itemX509Data(i).lengthCertificate(); j++) {
        			certificates.add(signature.getKeyInfo().itemX509Data(i).itemCertificate(j).getX509Certificate());
        		}
        	}
        	return OcesCertificateFactory.getInstance().generate(certificates);
        } catch (XMLSecurityException e) {
            throw new InternalException(e);
        } catch (TrustCouldNotBeVerifiedException e) {
        	throw new InternalException(e);
        }
    }

    public boolean verify() throws InternalException {
        try {
        	X509Certificate cert = getSigningCertificate().exportCertificate();
            boolean[] keyUsage = cert.getKeyUsage();
            if (keyUsage != null && !keyUsage[0]) {
                return false;
            } else {
            	return signature.checkSignatureValue(cert);
            }
        } catch (XMLSecurityException e) {
            throw new InternalException(e);
        }
    }

    public Map<String, SignatureProperty> getSignatureProperties() throws InternalException {
        try {
            String namespace = doc.getFirstChild().getNamespaceURI();
            if (namespace.equals(NAMESPACEURI_OPENOCES_R1)) {
            	return getPropertiesR1();
            } else if (namespace.equals(NAMESPACEURI_OPENOCES_R2)) {
            	return getPropertiesR2();
            } else {
            	throw new InternalException("Unsupported namespace " + namespace);
            }
        } catch (Exception e) {
            throw new InternalException(e);
        }
    }
    
    private Map<String, SignatureProperty> getPropertiesR1() throws InternalException, XPathExpressionException {
    	Map<String, SignatureProperty> properties = new HashMap<String, SignatureProperty>();
    	int signedContentLength = signature.getSignedInfo().getSignedContentLength();
		if (signedContentLength != 1) {
    		throw new InternalException("Expected signed content length 1, but found " + signedContentLength);
    	}

    	try {
            XPathExpression expr = xpath.compile("//ds:SignatureProperties/ds:SignatureProperty");
            NodeList nodes = (NodeList)expr.evaluate(doc, XPathConstants.NODESET);
            properties = extractPropertiesFromNodes(nodes, "Name", "Value");
    		return properties;
    	} catch (IOException e) {
    		throw new InternalException(e);
		} catch (TransformerException e) {
			throw new InternalException(e);
		} catch (Base64DecodingException e) {
			throw new InternalException(e);
		}
    }
        
    private Map<String, SignatureProperty> getPropertiesR2() throws InternalException {
    	try {
    	Map<String, SignatureProperty> properties = new HashMap<String, SignatureProperty>();
        for (int i = 0; i < signature.getSignedInfo().getSignedContentLength(); i++) {
            byte[] bs = signature.getSignedInfo().getSignedContentItem(i);
            Document contents = getDocument(bs);

            if ("ToBeSigned".equals(contents.getFirstChild().getAttributes().getNamedItem("Id").getNodeValue()))
            {
            	XPathExpression expr = xpath.compile("//ds:SignatureProperty");
                NodeList nodes = (NodeList)expr.evaluate(doc, XPathConstants.NODESET);

                properties.putAll(extractPropertiesFromNodes(nodes, "openoces:Name", "openoces:Value"));
            }
        }
        return properties;
    	} catch (Exception e) {
    		throw new InternalException(e);
    	}
    }

	protected static Document getDocument(byte[] bs) throws ParserConfigurationException, IOException, SAXException {
	    org.apache.xml.security.Init.init();
	    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
	    dbf.setNamespaceAware(true);

        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

	    DocumentBuilder db = dbf.newDocumentBuilder();
	    db.setErrorHandler(new IgnoreAllErrorHandler());
	    return db.parse(new ByteArrayInputStream(bs));
	}

	private Map<String, SignatureProperty> extractPropertiesFromNodes(NodeList nodes, String nameIdentifier, String valueIdentifier)
			throws TransformerException, UnsupportedEncodingException, Base64DecodingException, XPathExpressionException {
		Map<String, SignatureProperty> properties = new HashMap<String, SignatureProperty>();
		for (int i = 0; i < nodes.getLength(); i++) {
		    Node n = nodes.item(i);
		    Node nameNode, valueNode;
		    
		    XPathExpression nameExpr = xpath.compile(nameIdentifier);
	    	nameNode = (Element) nameExpr.evaluate(n, XPathConstants.NODE);
	    	XPathExpression valueExpr = xpath.compile(valueIdentifier);
	    	valueNode = (Element) valueExpr.evaluate(n, XPathConstants.NODE);

		    String name = nameNode.getFirstChild().getNodeValue();

            String xmlValue = valueNode.getFirstChild().getNodeValue();
            String value;

            Node encodingAtrribute =  valueNode.getAttributes().getNamedItem("Encoding");
            String encoding =  encodingAtrribute != null ? encodingAtrribute.getNodeValue() : null;
            //Never decode the rememberUseridToken clientside. It is encrypted and needs to stay b64 encoded.
            if(encoding != null && encoding.equalsIgnoreCase("base64") && !name.equals("rememberUseridToken")) {
            	value = new String(Base64.decode(xmlValue.getBytes("UTF-8")), "UTF-8");
            } else {
            	value = xmlValue;
            }

            boolean visibleToSigner = "yes".equals(valueNode.getAttributes().getNamedItem("VisibleToSigner").getNodeValue());

		    properties.put(name, new SignatureProperty(name, value, visibleToSigner));
		}
		return properties;
	}
}