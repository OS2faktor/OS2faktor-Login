package dk.digitalidentity;

import org.opensaml.xmlsec.config.impl.JavaCryptoValidationInitializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = { SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class })
public class Application {
	
	public static void main(String[] args) throws Exception {
		SpringApplication.run(Application.class);

		JavaCryptoValidationInitializer cryptoValidationInitializer = new JavaCryptoValidationInitializer();
		cryptoValidationInitializer.init();

		// already done by the SAML Module
		//InitializationService.initialize();
	}
}
