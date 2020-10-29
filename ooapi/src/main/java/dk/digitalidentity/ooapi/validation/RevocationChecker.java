/*
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
package dk.digitalidentity.ooapi.validation;

import java.security.cert.X509CRLEntry;

import dk.digitalidentity.ooapi.certificate.OcesCertificateFacade;

/**
 * A RevocationChecker can check whether an certificate has been revoked. 
 */
public interface RevocationChecker {
	/**
	 * Checks whether the chain element has been revoked or not
	 * 
	 * @param certificate The chain element to check
	 * @return Whether the chain element has been revoked or not
	*/
    public boolean isRevoked(OcesCertificateFacade certificate);

    /**
     * Get additional details about a revocation of the certificate, including date of revocation
     *
     * @param certificate
     * @return A X509CRLEntry instance for the certificate, or null if no such entry exists in this CRL.
     */
    public X509CRLEntry getRevocationDetails(OcesCertificateFacade certificate);

}
