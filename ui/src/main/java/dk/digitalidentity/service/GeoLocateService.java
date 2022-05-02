package dk.digitalidentity.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.service.geo.dto.GeoIP;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class GeoLocateService {

	@Autowired
	private OS2faktorConfiguration configuration;

	public GeoIP lookupIp(String ip) {
		if (!configuration.getGeo().isEnabled()) {
			return null;
		}

		RestTemplate restTemplate = new RestTemplate();

		String resourceUrl = configuration.getGeo().getUrl();
		if (!resourceUrl.endsWith("/")) {
			resourceUrl += "/";
		}
		resourceUrl += "api?ip=" + ip;

		try {
			HttpHeaders headers = new HttpHeaders();
			headers.add("Accept", "application/json");
			HttpEntity<Void> request = new HttpEntity<>(headers);

			ResponseEntity<GeoIP> response = restTemplate.exchange(resourceUrl, HttpMethod.GET, request, GeoIP.class);
			
			if (response.getStatusCodeValue() != 200) {
				log.warn("Failed to lookup ip " + ip + ". Status = " + response.getStatusCodeValue());
				return null;
			}
			
			return response.getBody();
		}
		catch (Exception ex) {
			log.warn("Failed to lookup ip " + ip + ". Message = " + ex.getMessage());
		}
		
		return null;
	}
}