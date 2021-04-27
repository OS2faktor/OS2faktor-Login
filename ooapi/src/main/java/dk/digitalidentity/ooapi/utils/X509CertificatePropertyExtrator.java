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
import java.io.IOException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Hashtable;
import java.util.List;

import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.x509.*;

import dk.digitalidentity.ooapi.ObjectIdentifiers;
import dk.digitalidentity.ooapi.exceptions.InvalidCaIssuerUrlException;
import dk.digitalidentity.ooapi.exceptions.NonOcesCertificateException;

/**
 * Implements a property extractor for X509 certificates.
 */
public class X509CertificatePropertyExtrator {

	public static String getEmailAddress(X509Certificate certificate) {
		String email = null;
		try {
			Collection<List<?>> sans = certificate.getSubjectAlternativeNames();
			if (sans != null) {
				for (List<?> san : sans) {
					if ( san.size()==2 && ((Integer)san.get(0)).intValue()==1) {
						email = (String)san.get(1);
						break;
					}
				}
			}
		} catch (CertificateParsingException e) {
			throw new IllegalArgumentException(e);
		}
		return email;
	}
	
	public static String getPid(X509Certificate certificate) {
		return removePidColonFromPid(certificate);
	}

	private static String removePidColonFromPid(X509Certificate certificate) {
		return extractSerialNumber(certificate).substring("PID:".length());
	}

	private static String extractSerialNumber(X509Certificate certificate) {
		return ((String) getElementInX509Name(certificate, ObjectIdentifiers.SERIAL_NUMBER));
	}

	@SuppressWarnings("deprecation")
	public static Object getElementInX509Name(X509Certificate certificate,String element) {
		X509Name name = getParsedSubjectDN(certificate);
        return getElementInX509Name(name, element);
	}
    
    @SuppressWarnings("deprecation")
	public static Object getElementInX509Name(X509Name name, String element) {
        List<?> oids = name.getOIDs();
        for (int i = 0; i < oids.size(); i++) {
            Object o = oids.get(i);
            if (element.equals(o.toString())) {
                return name.getValues().elementAt(i);
            }
        }
        return null;
    }

	@SuppressWarnings("rawtypes")
    private final static Hashtable defaultLookupWithSerialNumber = makeLookup();
	
    @SuppressWarnings({ "rawtypes", "unchecked", "deprecation" })
	private static Hashtable makeLookup() {
		Hashtable ht = X509Name.DefaultLookUp;
		DERObjectIdentifier SERIALNUMBER = new DERObjectIdentifier("2.5.4.5");
	    ht.put("serialnumber", SERIALNUMBER);
	    return ht;
	}
	
	@SuppressWarnings("deprecation")
	public static X509Name getParsedSubjectDN(X509Certificate certificate) {
	    return new X509Name(false, defaultLookupWithSerialNumber, certificate.getSubjectDN().getName());
	}

    @SuppressWarnings("deprecation")
	public static X509Name getParsedIssuerDN(X509Certificate certificate) {
        return new X509Name(false, defaultLookupWithSerialNumber, certificate.getIssuerDN().getName());
    }

	public static String getSubjectCommonName(X509Certificate certificate) {
		Object cn = getElementInX509Name(certificate, ObjectIdentifiers.COMMON_NAME);
		return cn == null ? null : cn.toString();
	}

    public static String getIssuerCommonName(X509Certificate certificate) {
        Object cn = getElementInX509Name(getParsedIssuerDN(certificate), ObjectIdentifiers.COMMON_NAME);
        return cn == null ? null : cn.toString();
    }

    public static String getSubjectOrganizationalUnit(X509Certificate certificate) {
        Object ou = getElementInX509Name(certificate, ObjectIdentifiers.ORGANIZATIONAL_UNIT);
        return ou == null ? null : ou.toString();
    }


	public static String getSubjectOrganisation(X509Certificate certificate) {
		Object o = getElementInX509Name(certificate, ObjectIdentifiers.ORGANISATION);
		return o == null ? null : o.toString();
	}

	public static boolean hasPseudonym(X509Certificate certificate) {
		return "Pseudonym".equals(getSubjectCommonName(certificate));
	}

    public static String getCertificatePolicyOID(X509Certificate certificate) {
        try {
            Extensions extensions = getX509Extensions(certificate);

            Extension e = extensions.getExtension(Extension.certificatePolicies);
            try (ASN1InputStream extIn = new ASN1InputStream(new ByteArrayInputStream(e.getExtnValue().getOctets()))) {
	            ASN1Sequence piSeq = (ASN1Sequence) extIn.readObject();
	            if (piSeq.size() != 1) {
	                throw new NonOcesCertificateException("Could not find Certificate PolicyOID");
	            }
	            PolicyInformation pi = PolicyInformation.getInstance(piSeq.getObjectAt(0));
	            return pi.getPolicyIdentifier().getId();
            }
        } catch (IOException e) {
            throw new NonOcesCertificateException("Could not find Certificate PolicyOID", e);
        }

    }


    public static String getSubjectSerialNumber(X509Certificate certificate) {
		Object sn = getElementInX509Name(certificate, ObjectIdentifiers.SERIAL_NUMBER);
		return sn == null ? null : sn.toString();
	}

	public static String getOcspUrl(X509Certificate certificate) {
        Extensions extensions = getX509Extensions(certificate);
        final byte[] authInfoBytes = extensions.getExtension(Extension.authorityInfoAccess).getExtnValue().getOctets();
        AccessDescription[] authorityInformationAccess = AuthorityInformationAccess.getInstance(authInfoBytes).getAccessDescriptions();
        if(authorityInformationAccess == null) {
			throw new IllegalArgumentException("Could not find ocsp url for certificate " + certificate);
		}

        String ocspUrl = getAccessDescriptionUrlForOid(AccessDescription.id_ad_ocsp, authorityInformationAccess);

        if(ocspUrl == null || ocspUrl.isEmpty()) {
			throw new IllegalArgumentException("Could not find ocsp url for certificate " + certificate);
		}
		return ocspUrl;
	}

    public static String getCaIssuerUrl(X509Certificate certificate) {
        Extensions extensions = getX509Extensions(certificate);
        final byte[] authInfoBytes = extensions.getExtension(Extension.authorityInfoAccess).getExtnValue().getOctets();
        AccessDescription[] authorityInformationAccess = AuthorityInformationAccess.getInstance(authInfoBytes).getAccessDescriptions();
        if(authorityInformationAccess == null) {
			throw new InvalidCaIssuerUrlException("Could not find CA issuer url for certificate " + certificate);
		}

        String caIssuerUrl = getAccessDescriptionUrlForOid(AccessDescription.id_ad_caIssuers, authorityInformationAccess);
        if(caIssuerUrl == null || caIssuerUrl.isEmpty()) {
			throw new InvalidCaIssuerUrlException("Could not find CA issuer for certificate " + certificate);
		}
		return caIssuerUrl;
    }


    private static String getAccessDescriptionUrlForOid(ASN1ObjectIdentifier oid, AccessDescription[] authorityInformationAccessArray) {
        for (int i = 0; i < authorityInformationAccessArray.length; i++) {
            AccessDescription authorityInformationAcces = authorityInformationAccessArray[i];
            if(oid.equals(authorityInformationAcces.getAccessMethod())) {
                GeneralName  name = authorityInformationAcces.getAccessLocation();
                return ((DERIA5String)name.getName()).getString();
            }
        }
        return null;
    }

    @SuppressWarnings("resource")
    private static Extensions getX509Extensions(X509Certificate certificate) {
        try {
            ASN1InputStream inputStream = new ASN1InputStream(certificate.getEncoded());
            ASN1Primitive certificateAsAsn1 = inputStream.readObject();
            final Certificate certificateStructure = Certificate.getInstance(certificateAsAsn1);
            TBSCertificate toBeSignedPart = certificateStructure.getTBSCertificate();
            Extensions extensions = toBeSignedPart.getExtensions();
            if (extensions == null) {
                throw new NonOcesCertificateException("No X509 extensions found");
            }
            return extensions;
        } catch (IOException e) {
            throw new IllegalStateException("IO error while extracting CRL Distribution points", e);
        } catch (CertificateEncodingException e) {
            throw new IllegalStateException("Error while extracting CRL Distribution points", e);
        }
    }

}
