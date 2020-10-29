package dk.digitalidentity.ooapi.validation;

public enum RevocationStatus {
		UNRECOGNIZED, // Certificate is not recognized by the PKI, so no certificate revocation status is available
		UNKNOWN,	  // Certificate is recognized by the PKI, but the revocation status is unknown
		REVOKED,	  // Certificate is recognized by the PKI, and the certificate is revoked
		GOOD		  // Certificate is recognized by the PKI, and the certificate is not revoked
}