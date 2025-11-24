package dk.digitalidentity.config;

import java.io.IOException;
import java.time.Duration;

import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.StandardCookieSpec;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
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
import org.springframework.web.client.RestClient;

import dk.digitalidentity.common.config.CommonConfiguration;

@Configuration
public class RestTemplateConfiguration {
	
	@Autowired
	private CommonConfiguration config;
	
	@Bean(name = "nemLoginRestClient")
	public RestClient nemLoginRestClient() throws Exception {
	    TrustStrategy acceptingTrustStrategy = (_, _) -> true;

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

	        TlsSocketStrategy tlsStrategy = ClientTlsStrategyBuilder.create()
	            .setSslContext(sslContext)
	            .buildClassic();

	        HttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
	            .setTlsSocketStrategy(tlsStrategy)
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

	    HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(client);
	    requestFactory.setConnectionRequestTimeout(Duration.ofMinutes(3));
	    requestFactory.setReadTimeout(Duration.ofMinutes(3));

	    return RestClient.builder()
	        .requestFactory(requestFactory)
	        .defaultStatusHandler(new ResponseErrorHandler() {

	            @Override
	            public boolean hasError(ClientHttpResponse response) throws IOException {
	                return false;
	            }
	        })
	        .build();
	}
}
