package dk.digitalidentity.mvc.admin.dto;

import dk.digitalidentity.common.dao.model.WindowCredentialProviderClient;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WindowCredentialProviderClientDTO {
	private long id;
	private String name;
	private String apiKey;
	private boolean disabled;
	private String domain;

	public WindowCredentialProviderClientDTO(WindowCredentialProviderClient entity) {
		this.id = entity.getId();
		this.name = entity.getName();
		this.apiKey = entity.getApiKey();
		this.disabled = entity.isDisabled();
		this.domain = entity.getDomain().getName();
	}
}
