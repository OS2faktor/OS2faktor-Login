package dk.digitalidentity.ooapi.certificate;

public interface PocesCertificateFacade extends OcesCertificateFacade {

	/**
	 * @return <code>true</code> if this certificate has the string "Pseudonym" as name
	 */
	boolean hasPseudonym();

	/**
	 * @return <code>true</code> if this certificate is a youth certificate
	 */
	boolean isYouthCertificate();

	/**
	 * Gets the PID of the personal certificate
	 * 
	 * @return The PID of the personal certificate
	 */
	String getPid();

}