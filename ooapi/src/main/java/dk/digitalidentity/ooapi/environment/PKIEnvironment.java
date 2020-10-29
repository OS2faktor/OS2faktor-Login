package dk.digitalidentity.ooapi.environment;

import dk.digitalidentity.ooapi.certificate.PKICertificateFactory;
import dk.digitalidentity.ooapi.validation.PKIRevocationChecker;

public interface PKIEnvironment extends Comparable<PKIEnvironment> {
	public PKICertificateFactory getPKICertificateFactory();
	public PKIRevocationChecker getPKIRevocationChecker();
}
