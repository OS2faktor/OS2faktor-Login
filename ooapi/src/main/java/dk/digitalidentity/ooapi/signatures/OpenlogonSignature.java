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

/* $Id: OpenlogonSignature.java,v 1.1 2005/08/22 21:33:57 cara Exp $ */

package dk.digitalidentity.ooapi.signatures;

import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPathExpressionException;

import org.apache.xml.security.exceptions.XMLSecurityException;
import org.w3c.dom.Document;

/**
 * This class is a high-level representation of an Openlogon generated xmldsig signature
 *
 * @author Carsten Raskgaard  <carsten@raskgaard.dk>
 */
public class OpenlogonSignature extends OpensignAbstractSignature {

    protected OpenlogonSignature(Document doc) throws TransformerException, XMLSecurityException, XPathExpressionException {
        super(doc);
    }
}
