package dk.digitalidentity.nemid.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javax.net.ssl.SSLContext;

import org.apache.http.client.HttpClient;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.xml.Jaxb2RootElementHttpMessageConverter;
import org.springframework.util.ResourceUtils;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfiguration {

	@Autowired
	private NemIdConfiguration configuration;

	@Bean(name = "pidRestTemplate")
	public RestTemplate restTemplate(RestTemplateBuilder builder) throws Exception {
	    SSLContext sslContext = SSLContextBuilder.create()
				.loadKeyMaterial(keyStore(), configuration.getKeystore().getPassword().toCharArray())
				.loadTrustMaterial(null, new TrustSelfSignedStrategy()).build();
	    
	    HttpClient client = HttpClients.custom().setSSLContext(sslContext).build();

        Supplier<ClientHttpRequestFactory> supplier = () -> {
            return new HttpComponentsClientHttpRequestFactory(client);
        };

		return builder.requestFactory(supplier)
				.messageConverters(getMessageConverters())
				.build();
	}
	
	private List<HttpMessageConverter<?>> getMessageConverters() {
		Jaxb2RootElementHttpMessageConverter converter = new Jaxb2RootElementHttpMessageConverter();
		
		List<MediaType> mediaTypes = new ArrayList<MediaType>();
		mediaTypes.add(MediaType.TEXT_XML);
		converter.setSupportedMediaTypes(mediaTypes);

		List<HttpMessageConverter<?>> converters = new ArrayList<HttpMessageConverter<?>>();
		converters.add(converter);
		converters.add(new StringHttpMessageConverter());

		return converters; 
	}

	private KeyStore keyStore() throws Exception {
		KeyStore keyStore = KeyStore.getInstance("PKCS12");
		File key = ResourceUtils.getFile(configuration.getKeystore().getLocation());

		try (InputStream in = new FileInputStream(key)) {
			keyStore.load(in, configuration.getKeystore().getPassword().toCharArray());
		}

		return keyStore;
	}
}
