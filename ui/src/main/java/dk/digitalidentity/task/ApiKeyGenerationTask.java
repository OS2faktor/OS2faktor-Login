package dk.digitalidentity.task;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.Client;
import dk.digitalidentity.common.dao.model.enums.ApiRole;
import dk.digitalidentity.common.service.ClientService;
import dk.digitalidentity.config.OS2faktorConfiguration;

@Component
@EnableScheduling
public class ApiKeyGenerationTask {

	@Autowired
	private OS2faktorConfiguration os2faktorConfiguration;

	@Autowired
	private CommonConfiguration commonConfiguration;

	@Autowired
	private ClientService clientService;

	@EventListener(ApplicationReadyEvent.class)
	public void runOnStartup() {
		if (!os2faktorConfiguration.getScheduled().isEnabled()) {
			return;
		}
		
		List<Client> existingClients = clientService.findAll();
		
		for (ApiRole apiRole : ApiRole.values()) {
			Client client = existingClients.stream().filter(c -> c.getRole().equals(apiRole)).findFirst().orElse(null);

			if (client == null) {
				// migrate logic - can be removed once all customers have been migrated, and then we can remove
				// the keys from the configuration files and configuration classes
				
				String apiKey = null;
				String description = null;
				switch (apiRole) {
					case AUDITLOG:
						apiKey = os2faktorConfiguration.getAuditLog().getApiKey();
						description = "API til at udlæse hændelsesloggen fra OS2faktor til lokalt logopsamlingsystem";
						break;
					// TODO: hvis vi udstiller api nøgler i brugergrænsefladen, så skal denne ikke vises
					case CERTMANAGER:
						apiKey = os2faktorConfiguration.getCertManagerApi().getApiKey();
						description = "API til at administrere certifikater i OS2faktor";
						break;
					case COREDATA:
						apiKey = os2faktorConfiguration.getCoreData().getApiKey();
						description = "API til at indlæse brugerdata i SO2faktor";
						break;
					case HARDWARETOKEN:
						apiKey = null;
						description = "API til at udlæse hardwarenøgler og deres stamdata";
						break;
					case MFA:
						apiKey = os2faktorConfiguration.getMfaPassthrough().getApiKey();
						description = "API til at lave MFA opslag fra lokale IdP'er, fx AD FS";
						break;
					// TODO: hvis vi udstiller api nøgler i brugergrænsefladen, så skal denne ikke vises
					case PASSWORD_CHANGE_QUEUE:
						apiKey = os2faktorConfiguration.getPasswordChangeQueueApi().getApiKey();
						description = "API til at synkronisere kodeordsskifte til eksterne systemer";
						break;
					case STIL:
						apiKey = commonConfiguration.getStilStudent().getApiKey();
						description = "API til at indlæse skole-stamdata på skoleelever og personale";
						break;
					case USERADMINISTRATION:
						apiKey = os2faktorConfiguration.getUserAdministration().getApiKey();
						description = "API til at udføre basale brugeradministrationshandlinger";
						break;
					case INTERNAL:
						apiKey = UUID.randomUUID().toString();
						description = "API til at lave interne kald til systemet";
						break;
				}
				
				if (!StringUtils.hasLength(apiKey)) {
					apiKey = UUID.randomUUID().toString();
				}
				
				client = new Client();
				client.setApiKey(apiKey);
				client.setDescription(description);
				client.setRole(apiRole);
				
				clientService.save(client);
			}
		}
	}
}
