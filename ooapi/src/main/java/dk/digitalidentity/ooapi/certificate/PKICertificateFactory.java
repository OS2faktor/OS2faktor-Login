package dk.digitalidentity.ooapi.certificate;

import java.security.cert.X509Certificate;
import java.util.List;

import dk.digitalidentity.ooapi.exceptions.TrustCouldNotBeVerifiedException;

public interface PKICertificateFactory {

	Certificate generate(List<X509Certificate> certificates) throws TrustCouldNotBeVerifiedException;
	

}
