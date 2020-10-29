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

/* $Id: OpensignSignature.java,v 1.2 2005/09/14 21:11:02 cara Exp $ */

package dk.digitalidentity.ooapi.signatures;

import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPathExpressionException;

import org.apache.xml.security.exceptions.XMLSecurityException;
import org.apache.xml.security.utils.XMLUtils;
import org.apache.xpath.XPathAPI;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import dk.digitalidentity.ooapi.exceptions.InternalException;

/**
 * This class is a high-level representation of an Opensign generated xmldsig signature
 *
 * @author Carsten Raskgaard  <carsten@raskgaard.dk>
 */
public class OpensignSignature extends OpensignAbstractSignature implements OpensignSignatureFacade {

    protected OpensignSignature(Document doc) throws TransformerException, XMLSecurityException, XPathExpressionException {
        super(doc);
    }

    public String getSigntext() throws InternalException {
        return getSignatureProperties().get("signtext").getValue();
    }

    public String getStylesheetDigest() throws InternalException {
        try {
            Element nscontext = XMLUtils.createDSctx(doc, "ds", org.apache.xml.security.utils.Constants.SignatureSpecNS);
            NodeList nodes = XPathAPI.selectNodeList(doc, "//ds:SignatureProperty[@Target=\"signature\"]", nscontext);
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                NodeList children = node.getChildNodes();
                Node nameElm = children.item(0);
                String signatureProperty = nameElm.getFirstChild().getNodeValue();
                if (signatureProperty != null && signatureProperty.equalsIgnoreCase("stylesheetDigest")) {
                    return children.item(1).getFirstChild().getNodeValue();
                }
            }
        } catch (TransformerException e) {
            throw new InternalException(e);
        }

        return null;
    }

    public String getSignatureAlgorithm() throws InternalException {
        Element element = getDsElement("SignatureMethod");
        return element.getAttribute("Algorithm");
    }

    public String getSignatureValue() throws InternalException {
        Element element = getDsElement("SignatureValue");
        return element.getFirstChild().getNodeValue();
    }

    public String getDigestAlgoritm() throws InternalException {
        Element element = getDsElement("DigestMethod");
        return element.getAttribute("Algorithm");
    }

    public String getDigestValue() throws InternalException {
        Element element = getDsElement("DigestValue");
        return element.getFirstChild().getNodeValue();
    }

    private Element getDsElement(String elemName) throws InternalException {
        try {
            Element nscontext = XMLUtils.createDSctx(doc, "ds", org.apache.xml.security.utils.Constants.SignatureSpecNS);
            return (Element) XPathAPI.selectSingleNode(doc, "//ds:" + elemName + "[1]", nscontext);
        } catch (TransformerException e) {
            throw new InternalException(e);
        }
    }
}


