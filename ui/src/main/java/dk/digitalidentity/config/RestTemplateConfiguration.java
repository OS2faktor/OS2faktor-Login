package dk.digitalidentity.config;

import java.io.IOException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;

import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.TrustStrategy;
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
				.setConnectionRequestTimeout(30000)
				.setConnectTimeout(30000)
				.setSocketTimeout(60000)
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
			
			client = HttpClients.custom()
				        .setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build())
						.setSSLContext(sslContext)
						.setDefaultRequestConfig(requestConfig)
						.build();			
		}
		else {
			client = HttpClients.custom()
						.setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build())
						.setDefaultRequestConfig(requestConfig)
						.build();
		}

		HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
		requestFactory.setConnectionRequestTimeout(3 * 60 * 1000);
		requestFactory.setConnectTimeout(3 * 60 * 1000);
		requestFactory.setReadTimeout(3 * 60 * 1000);
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
