package dk.digitalidentity.config;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .authorizeRequests()
            .mvcMatchers("/sso/saml/**").permitAll()
            .mvcMatchers("/manage/**").permitAll()
            .mvcMatchers("/webjars/**").permitAll()
            .mvcMatchers("/css/**").permitAll()
            .mvcMatchers("/js/**").permitAll()
            .mvcMatchers("/img/**").permitAll()
            .mvcMatchers("/favicon.ico").permitAll()
            .mvcMatchers("/error").permitAll()
            .mvcMatchers("/konto/aktiver").permitAll()
            .mvcMatchers("/konto/vaelgkode").permitAll()
            .mvcMatchers("/konto/login").permitAll()
            .anyRequest().denyAll();
    }
}
