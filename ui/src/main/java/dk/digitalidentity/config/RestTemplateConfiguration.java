package dk.digitalidentity.config;

import java.io.IOException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.StandardCookieSpec;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.ResourceUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import dk.digitalidentity.common.config.CommonConfiguration;

@Configuration
public class RestTemplateConfiguration {
	
	@Autowired
	private CommonConfiguration config;
	
	@Bean(name = "nemLoginRestTemplate")
	public RestTemplate nemLoginRestTemplate() throws Exception {
		TrustStrategy acceptingTrustStrategy = (X509Certificate[] chain, String authType) -> true;

		RequestConfig requestConfig = RequestConfig.custom()
			.setCookieSpec(StandardCookieSpec.RELAXED)
			.setConnectionRequestTimeout(Timeout.ofSeconds(180))
			.setResponseTimeout(Timeout.ofSeconds(180))
			.build();

		CloseableHttpClient client = null;
		if (config.getNemLoginApi().isEnabled()) {
			SSLContext sslContext = SSLContextBuilder.create()
		        .loadKeyMaterial(
	        		ResourceUtils.getFile(config.getNemLoginApi().getKeystoreLocation()),
	        		config.getNemLoginApi().getKeystorePassword().toCharArray(),
	        		config.getNemLoginApi().getKeystorePassword().toCharArray())
		        .loadTrustMaterial(acceptingTrustStrategy)
		        .build();

	        SSLConnectionSocketFactory sslSocketFactory = SSLConnectionSocketFactoryBuilder.create()
	                .setSslContext(sslContext)
	                .build();

	        HttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
	                .setSSLSocketFactory(sslSocketFactory)
	                .build();

			client = HttpClients.custom()
				.setConnectionManager(connectionManager)
				.setDefaultRequestConfig(requestConfig)
				.build();			
		}
		else {
			client = HttpClients.custom()
				.setDefaultRequestConfig(requestConfig)
				.build();
		}

		HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
		requestFactory.setConnectionRequestTimeout(3 * 60 * 1000);
		requestFactory.setConnectTimeout(3 * 60 * 1000);
		requestFactory.setHttpClient(client);

		RestTemplate restTemplate = new RestTemplate(requestFactory);
		restTemplate.setErrorHandler(new ResponseErrorHandler() {
			
			@Override
			public boolean hasError(ClientHttpResponse response) throws IOException {
				return false;
			}
			
			@Override
			public void handleError(ClientHttpResponse response) throws IOException {
			}
		});

		return restTemplate;
	}
}
