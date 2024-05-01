package dk.digitalidentity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.jdbc.MySqlJdbcIndexedSessionRepositoryCustomizer;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
public class SessionConfiguration {

	@Bean
	public CookieSerializer cookieSerializer() {
		// Chrome 80+ and latest Edge browser defaults to SameSite=Lax if the SameSite attribute is
		// not set on cookies. This ensures that SameSite=None is used, so the SAML flow will work
		// as intended (domains are not send cross-domain unless this is set)
		DefaultCookieSerializer serializer = new DefaultCookieSerializer();
		serializer.setSameSite("None");
		serializer.setUseSecureCookie(true);
		serializer.setCookieMaxAge(18 * 60 * 60); // 18 hours

		return serializer;
	}
	
	// Spring Session JDBC optimizations for MySQL
    @Bean
    public MySqlJdbcIndexedSessionRepositoryCustomizer sessionRepositoryCustomizer() {
		return new MySqlJdbcIndexedSessionRepositoryCustomizer();
    }
}