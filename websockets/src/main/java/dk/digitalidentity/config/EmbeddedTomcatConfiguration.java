package dk.digitalidentity.config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class EmbeddedTomcatConfiguration {

	@Value("${server.websocketPort}")
	private int websocketPort;

	@Bean
	public ServletWebServerFactory servletContainer() {
	    TomcatServletWebServerFactory tomcat = new TomcatServletWebServerFactory();	    
	    tomcat.addAdditionalTomcatConnectors(additionalConnector());

	    return tomcat;
	}
	
	@Bean
	public FilterRegistrationBean<OncePerRequestFilter> javaMelodyRestrictingFilter() {
		OncePerRequestFilter filter = new OncePerRequestFilter() {

			@Override
			protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

				if (request.getLocalPort() == websocketPort) {
					filterChain.doFilter(request, response);
				}
				else {
					response.sendError(404);
				}
			}
		};

		FilterRegistrationBean<OncePerRequestFilter> filterRegistrationBean = new FilterRegistrationBean<OncePerRequestFilter>();
		filterRegistrationBean.setFilter(filter);
		filterRegistrationBean.setOrder(-100);
		filterRegistrationBean.setName("WebSocketFilter");
		filterRegistrationBean.addUrlPatterns(new String[] { "/ws", "/ws/*" });

		return filterRegistrationBean;
	}

	private Connector[] additionalConnector() {
		List<Connector> result = new ArrayList<>();

		Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
		connector.setScheme("http");
		connector.setPort(websocketPort);
		result.add(connector);

		return result.toArray(new Connector[] {});
	}
}