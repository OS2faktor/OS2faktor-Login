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
package dk.digitalidentity.ooapi.utils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.cert.CRLException;
import java.security.cert.CertPath;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.util.List;

public class X509CertificateFactory {

	private static X509CertificateFactory instance = new X509CertificateFactory();
	private CertificateFactory certificateFactory;
	
	private X509CertificateFactory() {
		try {
			certificateFactory = CertificateFactory.getInstance("X509");
		} catch (CertificateException e) {
			throw new IllegalStateException(e);
		}
	}
	
	public static X509CertificateFactory getInstance() {
		return instance;
	}
	
	public X509Certificate generateCertificate(InputStream inStream) throws CertificateException {
		return (X509Certificate)certificateFactory.generateCertificate(inStream);
	}
	
	public X509Certificate generateCertificate(byte[] bytes) throws CertificateException {
		return generateCertificate(new ByteArrayInputStream(bytes));
	}
	
	public X509Certificate generateCertificate(String data) throws CertificateException {
		return (X509Certificate)certificateFactory.generateCertificate(new ByteArrayInputStream(data.getBytes()));
	}
	
	public CertPath generateCertPath(List<X509Certificate> certificates) throws CertificateException {
		return certificateFactory.generateCertPath(certificates);
	}
	
	public X509CRL generateCRL(InputStream inStream) throws CRLException {
		return (X509CRL)certificateFactory.generateCRL(inStream);
	}
}
