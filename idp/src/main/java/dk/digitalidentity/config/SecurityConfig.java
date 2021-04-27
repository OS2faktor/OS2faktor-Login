package dk.digitalidentity.config;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .csrf().ignoringAntMatchers("/sso/saml/login").and() // disable CSRF protection for the SAML login flow (but keep it everywhere else)
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
            .anyRequest().denyAll();
    }
}
