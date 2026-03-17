package dk.digitalidentity.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.session.jdbc.MySqlJdbcIndexedSessionRepositoryCustomizer;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.session.web.http.SessionRepositoryFilter;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;

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
    
	// we have to disable the build-in Spring Sessions filter
    @Bean
    public FilterRegistrationBean<SessionRepositoryFilter<?>> disableAutoSessionFilter(SessionRepositoryFilter<?> filter) {
        FilterRegistrationBean<SessionRepositoryFilter<?>> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);

        return reg;
    }

    // and then build our own, that excludes static resources
    @Bean
    public FilterRegistrationBean<Filter> sessionFilterWithExclusions(SessionRepositoryFilter<?> sessionRepositoryFilter) {
        FilterRegistrationBean<Filter> reg = new FilterRegistrationBean<>();

        reg.setFilter((request, response, chain) -> {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            String path = httpRequest.getRequestURI();

            if (isStaticResource(path)) {
                chain.doFilter(request, response);
            }
            else {
                sessionRepositoryFilter.doFilter(request, response, chain);
            }
        });

        reg.setName("springSessionRepositoryFilter");
        reg.setOrder(Integer.MIN_VALUE + 50);
        reg.addUrlPatterns("/*");
        return reg;
    }

    // and finally we need to tell Spring Security that it these resources are stateless (no session please)
    @Bean
    @Order(0)
    public SecurityFilterChain staticResourcesFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(
            	"/css/**",
            	"/js/**",
            	"/img/**",
            	"/webjars/**",
            	"/favicon.ico",
                "/**/*.js.map",
                "/**/*.css.map"
            )
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .csrf(csrf -> csrf.disable())
            .securityContext(ctx -> ctx.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(cache -> cache.disable());

        return http.build();
    }

	private boolean isStaticResource(String path) {
        return path.startsWith("/css/") ||
        	   path.startsWith("/js/") ||
        	   path.startsWith("/img/") ||
        	   path.startsWith("/webjars/") ||
        	   path.equals("/favicon.ico") ||
        	   path.endsWith(".js.map") ||
        	   path.endsWith(".css.map");
    }
}