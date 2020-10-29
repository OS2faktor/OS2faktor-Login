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
package dk.digitalidentity.ooapi.certificate;

import dk.digitalidentity.ooapi.ObjectIdentifiers;
import dk.digitalidentity.ooapi.utils.X509CertificatePropertyExtrator;

import java.security.cert.X509Certificate;

/**
 * POCES certificate (aka personal certificate) (danish: personcertifikat)
 */
@SuppressWarnings("serial")
public class PocesCertificate extends OcesCertificate implements PocesCertificateFacade {

	/**
	 * Contructs a POCES certificate with the given <code>CA</code> as parent
	 * 
	 * @param certificate certificate
	 * @param parent parent signing CA
	 */
	PocesCertificate(X509Certificate certificate, CA parent) {
		super(certificate, parent);
	}

	/**
	 * @return <code>true</code> if this certificate has the string "Pseudonym" as name
	 */
	public boolean hasPseudonym() {
		return X509CertificatePropertyExtrator.hasPseudonym(certificate);		
	}
	
	/**
	 * @return <code>true</code> if this certificate is a youth certificate
	 */
	public boolean isYouthCertificate() {
		Object element = X509CertificatePropertyExtrator.getElementInX509Name(certificate, ObjectIdentifiers.ORGANIZATIONAL_UNIT);
		if (element != null && element instanceof String) {
			String s = (String)element;
			return s.equals("Ung mellem 15 og 18 - Kan som udgangspunkt ikke lave juridisk bindende aftaler");
		}
		return false;
	}
	
	/**
	 * Gets the PID of the personal certificate
	 * 
	 * @return The PID of the personal certificate
	 */
	public String getPid() {
		return X509CertificatePropertyExtrator.getPid(certificate);
	}
}
