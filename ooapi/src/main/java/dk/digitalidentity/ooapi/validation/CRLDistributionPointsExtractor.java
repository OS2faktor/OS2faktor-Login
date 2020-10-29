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
import java.io.IOException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.x500.AttributeTypeAndValue;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.*;

import dk.digitalidentity.ooapi.exceptions.NonOcesCertificateException;

public class CRLDistributionPointsExtractor {
	/**
	 * Extracts the CRL distribution points out of a certificate
	 * @param certificate
	 * @return the extracted CRL distribution points
	 */
    public static CRLDistributionPoints extractCRLDistributionPoints(X509Certificate certificate) {
        CRLDistPoint distributionPointsExtension = extractCrlDistributionPointsExtension(certificate);

        String fullCrlDistributionPoint = extractFullCrlDistributionPoint(distributionPointsExtension);
        String partitionedCRLDistributionPoint = extractPartitionedCrlDistributionPoint(distributionPointsExtension);

        return new CRLDistributionPoints(fullCrlDistributionPoint, partitionedCRLDistributionPoint);
    }

    @SuppressWarnings("unchecked")
    private static CRLDistPoint extractCrlDistributionPointsExtension(X509Certificate certificate) {
        Extensions extensions = extractExtensions(certificate);
        Enumeration<ASN1ObjectIdentifier> extensionOids = extensions.oids();
        while (extensionOids.hasMoreElements()) {
            ASN1ObjectIdentifier extensionOid = extensionOids.nextElement();

            if (extensionOid.equals(Extension.cRLDistributionPoints)) {
                return convertToCRLDistPoint(extensions, extensionOid);
            }
        }

        throw new NonOcesCertificateException("Not an OCES certificate: Missing CRL distribution points extension");
    }

    private static String extractFullCrlDistributionPoint(CRLDistPoint distributionPointsExtension) {
        DERIA5String crlDistributionPointGeneralName = (DERIA5String) extractGeneralName(distributionPointsExtension, GeneralName.uniformResourceIdentifier);
        return crlDistributionPointGeneralName != null ? crlDistributionPointGeneralName.getString() : null;
    }

    private static String extractPartitionedCrlDistributionPoint(CRLDistPoint distributionPointsExtension) {
        ASN1Encodable directoryNames = extractGeneralName(distributionPointsExtension, GeneralName.directoryName);
        return directoryNames == null ? null : extractPartitionedCrlDistributionPoint(directoryNames); 
    }

    private static String extractPartitionedCrlDistributionPoint(ASN1Encodable directoryNames) {
        X500Name name = (X500Name)directoryNames;

        String partitionedCrlDistributionPoint = "";
        String comma = "";
        for(RDN rdn: name.getRDNs()) {
            final AttributeTypeAndValue typeAndValue = rdn.getFirst();
            final String relativeDnName = BCStyle.INSTANCE.oidToDisplayName(typeAndValue.getType());
            final String relativeDnValue = ((ASN1String)typeAndValue.getValue()).getString();

            partitionedCrlDistributionPoint = relativeDnName + "=" + relativeDnValue + comma + partitionedCrlDistributionPoint;
            comma = ",";
        }

        return partitionedCrlDistributionPoint;
    }

    private static Extensions extractExtensions(X509Certificate certificate) {
        ASN1Sequence certificateAsAsn1 = toAsn1(certificate);
        return extractExtensions(certificateAsAsn1);
    }

    private static CRLDistPoint convertToCRLDistPoint(Extensions extensions, ASN1ObjectIdentifier extensionOid) {
        try {
            Extension extension = extensions.getExtension(extensionOid);
            ASN1OctetString asOctetString = extension.getExtnValue();
            ASN1InputStream asInputStream = new ASN1InputStream(new ByteArrayInputStream(asOctetString.getOctets()));
            return CRLDistPoint.getInstance(asInputStream.readObject());
        } catch (IOException e) {
            throw new IllegalStateException("IO error while extracting CRL Distribution points", e);
        }
    }

    @SuppressWarnings("resource")
    private static ASN1Sequence toAsn1(X509Certificate certificate) {
        try {
            ASN1InputStream inputStream = new ASN1InputStream(certificate.getEncoded());
            ASN1Primitive certificateAsAsn1 = inputStream.readObject();
            return (ASN1Sequence) certificateAsAsn1;
        } catch (IOException e) {
            throw new IllegalStateException("IO error while extracting CRL Distribution points", e);
        } catch (CertificateEncodingException e) {
            throw new IllegalStateException("Error while extracting CRL Distribution points", e);
        }
    }

    private static Extensions extractExtensions(ASN1Sequence certificateAsAsn1) {
        final Certificate certificate = Certificate.getInstance(certificateAsAsn1);
        final TBSCertificate toBeSignedPart = certificate.getTBSCertificate();
        final Extensions extensions = toBeSignedPart.getExtensions();
        if (extensions == null) {
            throw new NonOcesCertificateException("No X509 extensions found");
        }
        return extensions;
    }

    private static ASN1Encodable extractGeneralName(CRLDistPoint distributionPointsExtension, int uniformResourceIdentifier) {
        for (DistributionPoint distributionPoint : distributionPointsExtension.getDistributionPoints()) {
            DistributionPointName distributionPointName = distributionPoint.getDistributionPoint();
            if (distributionPointName.getType() == DistributionPointName.FULL_NAME) {
                for (GeneralName generalName : GeneralNames.getInstance(distributionPointName.getName()).getNames()) {
                    if (generalName.getTagNo() == uniformResourceIdentifier) {
                        return generalName.getName();
                    }
                }
            }
        }
        return null;
    }
}
