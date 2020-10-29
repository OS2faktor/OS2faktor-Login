package dk.digitalidentity.nemid.config;

import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@Getter
@Setter
public class PidConfiguration {
	private String url = "https://pidws.certifikat.dk/pid_serviceprovider_server/pidxml/";
	private String serviceProviderId;
}
