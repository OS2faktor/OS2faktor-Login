package dk.digitalidentity.config;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .csrf()
                .ignoringAntMatchers("/sso/saml/login")
                .ignoringAntMatchers("/sso/saml/logout")
                .ignoringAntMatchers("/sso/login")
                .ignoringAntMatchers("/api/client/login")
                .ignoringAntMatchers("/api/client/loginWithBody")
                .ignoringAntMatchers("/api/client/changePassword")
                .ignoringAntMatchers("/api/client/changePasswordWithBody")
                .ignoringAntMatchers("/nemlogin/saml/**")
                .ignoringAntMatchers("/oauth2/authorize")
                .ignoringAntMatchers("/oauth2/introspect")
                .ignoringAntMatchers("/oauth2/jwks")
                .ignoringAntMatchers("/oauth2/revoke")
                .ignoringAntMatchers("/oauth2/token")
                .ignoringAntMatchers("/userinfo")
            .and() // Disable CSRF protection for the SAML login flow and windows clients (but keep it everywhere else)
            .authorizeRequests()
                .mvcMatchers("/").permitAll()
                .mvcMatchers("/sso/login").permitAll()
                .mvcMatchers("/sso/saml/**").permitAll()
                .mvcMatchers("/oidc/**").permitAll()
                .mvcMatchers("/ws/**").permitAll()
                .mvcMatchers("/manage/**").permitAll()
                .mvcMatchers("/webjars/**").permitAll()
                .mvcMatchers("/css/**").permitAll()
                .mvcMatchers("/js/**").permitAll()
                .mvcMatchers("/img/**").permitAll()
                .mvcMatchers("/favicon.ico").permitAll()
                .mvcMatchers("/error").permitAll()
                .mvcMatchers("/vilkaar/godkendt").permitAll()
                .mvcMatchers("/konto/aktiver").permitAll()
                .mvcMatchers("/konto/vaelgkode").permitAll()
                .mvcMatchers("/konto/fortsaetlogin").permitAll()
                .mvcMatchers("/konto/init-aktiver").permitAll()
                .mvcMatchers("/nemlogin/saml/**").permitAll()
                .mvcMatchers("/konto/valideradkodeord").permitAll()
                .mvcMatchers("/change-password").permitAll()
                .mvcMatchers("/change-password-next").permitAll()
                .mvcMatchers(HttpMethod.POST,"/api/client/login").permitAll()
                .mvcMatchers(HttpMethod.POST,"/api/client/loginWithBody").permitAll()
                .mvcMatchers(HttpMethod.POST,"/api/client/changePassword").permitAll()
                .mvcMatchers(HttpMethod.POST,"/api/client/changePasswordWithBody").permitAll()
                .mvcMatchers("/oauth2/authorize").permitAll()
                .mvcMatchers("/oauth2/introspect").permitAll()
                .mvcMatchers("/oauth2/jwks").permitAll()
                .mvcMatchers("/oauth2/revoke").permitAll()
                .mvcMatchers("/oauth2/token").permitAll()
                .mvcMatchers("/userinfo").permitAll()
                .mvcMatchers("/.well-known/oauth-authorization-server").permitAll()
                .mvcMatchers("/.well-known/openid-configuration").permitAll()
                .mvcMatchers("/elevkode").permitAll()
                .anyRequest().denyAll();
    }
}
