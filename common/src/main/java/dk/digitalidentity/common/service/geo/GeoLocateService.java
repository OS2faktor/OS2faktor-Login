package dk.digitalidentity.common.service.geo;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.AuditLog;
import dk.digitalidentity.common.service.AuditLogService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class GeoLocateService {

	@Autowired
	private CommonConfiguration configuration;
	
	@Autowired
	private AuditLogService auditLogService;

	public GeoIP lookupIp(String ip) {
		if (!configuration.getGeo().isEnabled()) {
			return null;
		}

		GeoIP geo = new GeoIP();
		geo.setRetry(true);
		
		if (!StringUtils.hasLength(ip) || "127.0.0.1".equals(ip)) {
			geo.setRetry(false);
			geo.setCountry("Danmark");
			geo.setCity("Systemhandling");
			
			return geo;
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
			
			if (response.getStatusCode().value() != 200) {
				log.warn("Failed to lookup ip " + ip + ". Status = " + response.getStatusCode().value());
				
				if (response.getStatusCode().value() == 400) {
					geo.setRetry(false);
				}
				return geo;
			}
			
			return response.getBody();
		}
		catch (Exception ex) {
			log.warn("Failed to lookup ip " + ip + ". Message = " + ex.getMessage());
			
			if (ex.getMessage().contains("400")) {
				geo.setRetry(false);
			}
		}

		return geo;
	}
	
	public void setLocationFromIP() {
		LocalDateTime oneWeekAgo = LocalDateTime.now().minusDays(7);
		List<AuditLog> logs = auditLogService.get500WhereLocationNull();

		for (AuditLog auditLog : logs) {
			if (auditLog.getIpAddress() == null) {
				auditLog.setLocation("UNKNOWN");
				continue;
			}

			GeoIP geoIp = lookupIp(auditLog.getIpAddress());
			
			if (geoIp != null && geoIp.getCountry() != null) {
				auditLog.setLocation(geoIp.getCountry());
			}
			else if (!geoIp.isRetry()) {
				auditLog.setLocation("UNKNOWN");
			}
			else if (auditLog.getTts().isBefore(oneWeekAgo)) {
				auditLog.setLocation("UNKNOWN");

				log.error("Unable to set location on auditlog: " + auditLog.getId());
			}
		}

		auditLogService.saveAll(logs);		
	}
}