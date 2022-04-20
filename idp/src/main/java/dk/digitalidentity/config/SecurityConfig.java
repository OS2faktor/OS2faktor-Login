package dk.digitalidentity.config;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
		http.headers().contentSecurityPolicy("script-src 'self' 'unsafe-inline'");

        http
            .csrf()
                .ignoringAntMatchers("/sso/saml/login")
                .ignoringAntMatchers("/api/client/login")
                .ignoringAntMatchers("/api/client/changePassword")
		.ignoringAntMatchers("/nemlogin/saml/**")
            .and() // Disable CSRF protection for the SAML login flow and windows clients (but keep it everywhere else)
            .authorizeRequests()
                .mvcMatchers("/").permitAll()
                .mvcMatchers("/sso/saml/**").permitAll()
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
                .mvcMatchers(HttpMethod.POST,"/api/client/login").permitAll()
                .mvcMatchers(HttpMethod.POST,"/api/client/changePassword").permitAll()
                .anyRequest().denyAll();
    }
}
