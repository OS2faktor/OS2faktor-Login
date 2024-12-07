package dk.digitalidentity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig  {

	@Order(2) // AuthorizationServerSecurityConfig is set as @Order(1) letting OIDC/OAuth2.0 be handled first
	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf((csrf) ->
        	csrf
                .ignoringRequestMatchers("/sso/saml/login")
                .ignoringRequestMatchers("/sso/saml/logout")
                .ignoringRequestMatchers("/sso/login")
                .ignoringRequestMatchers("/api/client/login")
                .ignoringRequestMatchers("/api/client/loginWithBody")
                .ignoringRequestMatchers("/api/client/changePassword")
                .ignoringRequestMatchers("/api/client/changePasswordWithBody")
                .ignoringRequestMatchers("/nemlogin/saml/**")
                .ignoringRequestMatchers("/oauth2/authorize")
                .ignoringRequestMatchers("/oauth2/introspect")
                .ignoringRequestMatchers("/oauth2/jwks")
                .ignoringRequestMatchers("/oauth2/revoke")
                .ignoringRequestMatchers("/oauth2/token")
                .ignoringRequestMatchers("/userinfo")
                .ignoringRequestMatchers("/entraMfa/**")
        );

        http.authorizeHttpRequests((req) -> {
        	req
            	.requestMatchers("/").permitAll()
            	.requestMatchers("/sso/login").permitAll()
            	.requestMatchers("/sso/saml/**").permitAll()
            	.requestMatchers("/oidc/**").permitAll()
            	.requestMatchers("/ws/**").permitAll()
            	.requestMatchers("/manage/**").permitAll()
            	.requestMatchers("/webjars/**").permitAll()
            	.requestMatchers("/css/**").permitAll()
            	.requestMatchers("/js/**").permitAll()
            	.requestMatchers("/img/**").permitAll()
            	.requestMatchers("/favicon.ico").permitAll()
            	.requestMatchers("/error").permitAll()
				.requestMatchers("/fragment/username").permitAll()
            	.requestMatchers("/vilkaar/godkendt").permitAll()
            	.requestMatchers("/konto/aktiver").permitAll()
            	.requestMatchers("/konto/vaelgkode").permitAll()
            	.requestMatchers("/konto/fortsaetlogin").permitAll()
            	.requestMatchers("/konto/init-aktiver").permitAll()
            	.requestMatchers("/nemlogin/saml/**").permitAll()
            	.requestMatchers("/konto/valideradkodeord").permitAll()
            	.requestMatchers("/change-password").permitAll()
            	.requestMatchers("/change-password-next").permitAll()
            	.requestMatchers(HttpMethod.POST,"/api/client/login").permitAll()
            	.requestMatchers(HttpMethod.POST,"/api/client/loginWithBody").permitAll()
            	.requestMatchers(HttpMethod.POST,"/api/client/changePassword").permitAll()
            	.requestMatchers(HttpMethod.POST,"/api/client/changePasswordWithBody").permitAll()
            	.requestMatchers("/elevkode").permitAll()
            	.requestMatchers("/oauth2/login").permitAll()
            	.requestMatchers("/oauth2/authorize").permitAll()
            	.requestMatchers("/oauth2/introspect").permitAll()
            	.requestMatchers("/oauth2/jwks").permitAll()
            	.requestMatchers("/oauth2/revoke").permitAll()
            	.requestMatchers("/oauth2/token").permitAll()
            	.requestMatchers("/userinfo").permitAll()
            	.requestMatchers("/.well-known/oauth-authorization-server").permitAll()
            	.requestMatchers("/.well-known/openid-configuration").permitAll()
            	.requestMatchers("/entraMfa/**").permitAll();

        	req.anyRequest().denyAll();
        });

        return http.build();
    }
}
