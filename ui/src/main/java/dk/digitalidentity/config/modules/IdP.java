package dk.digitalidentity.config.modules;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@Setter
public class IdP {
	private String baseUrl;
	
	public String getBaseUrl() {
		if (baseUrl == null) {
			log.error("No baseUrl configured!");
			return "";
		}
		
		if (baseUrl.endsWith("/")) {
			return baseUrl;
		}
		
		return baseUrl + "/";
	}
}
