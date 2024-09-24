package dk.digitalidentity.mvc.admin.dto;

import dk.digitalidentity.common.dao.model.SessionSetting;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SessionConfigurationForm {
	private Long domainId;
	private Long passwordExpiry;
	private Long mfaExpiry;
	private boolean nsisDomain;

	public SessionConfigurationForm(SessionSetting settings, boolean nsisDomain) {
		this.domainId = settings.getDomain().getId();
		this.passwordExpiry = settings.getPasswordExpiry();
		this.mfaExpiry = settings.getMfaExpiry();
		this.nsisDomain = nsisDomain;
	}

	public SessionConfigurationForm(long passwordExpiry, long mfaExpiry, boolean nsisDomain) {
		this.passwordExpiry = passwordExpiry;
		this.mfaExpiry = mfaExpiry;
		this.nsisDomain = nsisDomain;
	}
}
