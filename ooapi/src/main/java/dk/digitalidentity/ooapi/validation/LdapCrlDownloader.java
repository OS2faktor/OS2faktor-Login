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

import java.io.ByteArrayInputStream;
import java.security.cert.CRLException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;

import dk.digitalidentity.ooapi.environment.Environments.Environment;
import dk.digitalidentity.ooapi.ldap.LDAPFactory;
import dk.digitalidentity.ooapi.utils.X509CertificateFactory;

public class LdapCrlDownloader {
    
	private static final String CERTIFICATE_REVOCATION_LIST_BINARY = "certificateRevocationList;binary";
	private static final String CACERTIFICATE_BINARY = "cACertificate;binary";
		
	public CRL download(Environment environment, String ldapPath) {
		DirContext ctx = LDAPFactory.createLdapContext(environment, CERTIFICATE_REVOCATION_LIST_BINARY);
		try {
			return downloadCrl(ctx, ldapPath);
		} finally {
			close(ctx);
		}
	}

    public CRL download(String ldapHost, String ldapPath) {
		DirContext ctx = LDAPFactory.createLdapContext(ldapHost, CERTIFICATE_REVOCATION_LIST_BINARY);
		try {
			return downloadCrl(ctx, ldapPath);
		} finally {
			close(ctx);
		}
    }

	public X509Certificate downloadCertificate(String ldapHost, String ldapPath) {
		DirContext ctx = LDAPFactory.createLdapContext(ldapHost, CACERTIFICATE_BINARY);
		try {
			return downloadCaCertificate(ctx, ldapPath);
		} finally {
			close(ctx);
		}
	}

	private void close(DirContext ctx) {
		try {
			ctx.close();
		} catch (NamingException e) {
			throw new IllegalStateException(e);
		}
	}

    private CRL downloadCrl(DirContext ctx, String ldapPath) {
		try {
			ByteArrayInputStream inStream = downloadAttribute(ctx, ldapPath, CERTIFICATE_REVOCATION_LIST_BINARY);
			return new CRL(X509CertificateFactory.getInstance().generateCRL(inStream));
		} catch(NamingException e) {
			throw new IllegalStateException(e);
		} catch(CRLException e) {
			throw new IllegalStateException(e);
		}
    }
	
	private X509Certificate downloadCaCertificate(DirContext ctx, String ldapPath) {
		try {
			ByteArrayInputStream inStream = downloadAttribute(ctx, ldapPath, CACERTIFICATE_BINARY);
			return X509CertificateFactory.getInstance().generateCertificate(inStream);
		} catch(NamingException e) {
			throw new IllegalStateException(e);
		} catch (CertificateException e) {
			throw new IllegalStateException(e);
		}
	}

	private ByteArrayInputStream downloadAttribute(DirContext ctx, String ldapPath, String attributeToDownload)
			throws NamingException {
		Attributes attributes = ctx.getAttributes(ldapPath, new String[]{attributeToDownload});
		Attribute attribute = attributes.get(attributeToDownload);

		if (attribute == null) {
			throw new IllegalStateException("Cannot lookup: " + attributeToDownload + " in directory context: " + ctx);
		}

		return new ByteArrayInputStream((byte[]) attribute.get());
	}
}
