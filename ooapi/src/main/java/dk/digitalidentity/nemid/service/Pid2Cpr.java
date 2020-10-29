package dk.digitalidentity.nemid.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import dk.digitalidentity.nemid.config.NemIdConfiguration;
import dk.digitalidentity.nemid.service.model.PidMethod;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class Pid2Cpr {

	@Autowired
	@Qualifier("pidRestTemplate")
	private RestTemplate restTemplate;
	
	@Autowired
	private NemIdConfiguration configuration;

	public String lookup(String pid) {
		try {
			HttpHeaders headers = new HttpHeaders();
			headers.add("Content-Type", "text/xml");		
			
			PidMethod requestPayload = new PidMethod();
			requestPayload.getRequest().setServiceId(configuration.getPid().getServiceProviderId());
			requestPayload.getRequest().setPid(pid);
	
			HttpEntity<PidMethod> request = new HttpEntity<PidMethod>(requestPayload, headers);
	
			ResponseEntity<PidMethod> response = restTemplate.postForEntity(configuration.getPid().getUrl(), request, PidMethod.class);
	
			if (response.getBody() != null && response.getBody().getResponse() != null && response.getBody().getResponse().getStatus() != null && response.getBody().getResponse().getStatus().getStatusCode().equals("0")) {
				return response.getBody().getResponse().getCpr();
			}
			
			// failure scenario
			if (response.getBody() != null && response.getBody().getResponse() != null && response.getBody().getResponse().getStatus() != null) {
				String msg = "";
				if (response.getBody().getResponse().getStatus().getStatusText() != null && response.getBody().getResponse().getStatus().getStatusText().size() > 0) {
					msg = response.getBody().getResponse().getStatus().getStatusText().get(0);
				}

				log.error("Call to pid2cpr failed with code=" + response.getBody().getResponse().getStatus().getStatusCode() + ", msg=" + msg);
			}
			else {
				log.error("Empty response from pid2cpr service");
			}
		}
		catch (Exception ex) {
			log.error("Call to pid2cpr failed with exception", ex);
		}

		return null;
	}
}
