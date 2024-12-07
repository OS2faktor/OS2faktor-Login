package dk.digitalidentity.mvc.admin.dto;

import java.util.Locale;

import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.util.StringUtils;

import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.log.IdentificationDetails;
import dk.digitalidentity.common.service.mfa.model.ClientType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ActivationDTO {
	private long personId;
	private String note;
	
	@NotNull
	private IdentificationType identificationType;
	
	@NotNull
	@Min(value = 2)
	private String identification;
	
	@NotNull
	private NSISLevel nsisLevel;
	
	// for Manuel MFA Client registation only
	private String name;
	private ClientType type;
	private String deviceId;
	
	// only used for auditlogging
	private boolean adminSeenCredentials;
	
	public IdentificationDetails toIdentificationDetails(ResourceBundleMessageSource resourceBundle) {
		// cleanup the notes a bit
		String notes = note;
		if (notes != null) {
			notes = notes.replace('\r', ' ');
			notes = notes.replace('\n', ' ');
		}

		IdentificationDetails details = new IdentificationDetails();
		details.setIdSerial(identification);
		if (identificationType != null) {
			details.setIdType(resourceBundle.getMessage(identificationType.getMessage(), null, Locale.ENGLISH));
		}
		details.setNsisLevel(resourceBundle.getMessage(nsisLevel.getMessage(), null, Locale.ENGLISH));
		details.setNotes(notes);

		if (StringUtils.hasLength(deviceId)) {
			details.setMfaId(deviceId);
			details.setMfaType(resourceBundle.getMessage(type.getMessage(), null, Locale.ENGLISH));
		}
		else {
			details.setAdminSeenPassword(adminSeenCredentials ? "Ja" : "Nej");
		}
		
		return details;
	}
}
