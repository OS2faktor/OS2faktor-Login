package dk.digitalidentity.ooapi.validation;

import java.security.cert.X509CRLEntry;

import dk.digitalidentity.ooapi.certificate.Certificate;

public interface PKIRevocationChecker {
    public RevocationStatus isRevoked(Certificate certificate);
    public X509CRLEntry getRevocationDetails(Certificate certificate);
}