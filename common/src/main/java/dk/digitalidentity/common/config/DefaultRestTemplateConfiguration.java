package dk.digitalidentity.common.config;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.StandardCookieSpec;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class DefaultRestTemplateConfiguration {

	@Bean(name = "defaultRestTemplate")
	public RestTemplate defaultRestTemplate() {
		CloseableHttpClient client = HttpClients.custom()
			.setDefaultRequestConfig(RequestConfig.custom()
				.setCookieSpec(StandardCookieSpec.RELAXED)
				.build())
			.build();

		HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
		requestFactory.setConnectionRequestTimeout(3 * 60 * 1000);
		requestFactory.setConnectTimeout(3 * 60 * 1000);
		requestFactory.setHttpClient(client);

		return new RestTemplate(requestFactory);
	}
}
