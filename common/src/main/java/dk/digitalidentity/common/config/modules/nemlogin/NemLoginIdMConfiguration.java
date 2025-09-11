package dk.digitalidentity.common.config.modules.nemlogin;

import dk.digitalidentity.common.config.FeatureDocumentation;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NemLoginIdMConfiguration {
	
	@FeatureDocumentation(name = "NemLog-in IdM", description = "Integration til NemLog-in IdM (MitID Erhverv)")
	private boolean enabled = false;
	
	@FeatureDocumentation(name = "Privat MitID / NemLog-in", description = "Overfør anvendelsen af privat MitID som loginmiddel i MitID Erhverv")
	private boolean privateMitIdEnabled = false;

	@FeatureDocumentation(name = "Kvalificeret underskrift", description = "Styring af hvilke brugere som må udføre underskrifter i MitID Erhverv")
	private boolean qualifiedSignatureEnabled = false;
	private boolean qualifiedSignatureActivationEnabled = false;

	@FeatureDocumentation(name = "Navnebeskyttelse i MitID Erhverv", description = "Sæt navnebeskyttelse på brugere i MitID Erhverv hvis de har navnebeskyttelse i CPR registeret (OBS! skal også slås til inde i MitID Erhverv før funktionen kan bruges)")
	private boolean pseudonymEnabled = false;

	private String keystoreLocation;
	private String keystorePassword;
	private String invoiceMethodUuid;
	private String defaultEmail;
	private boolean disableSendingRid = false;

	private String baseUrl = "https://services.nemlog-in.dk";
	
	// enable ONCE and only ONCE
	private boolean migrateExistingUsers = false;
}
