package dk.digitalidentity.filter;

import java.io.IOException;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Integer.MIN_VALUE)
public class ExcludeAPISessionFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
		if (request.getRequestURI().startsWith("/api/") || request.getRequestURI().startsWith("/manage/")) {
			request.setAttribute("org.springframework.session.web.http.SessionRepositoryFilter.FILTERED", Boolean.TRUE);
		}

		filterChain.doFilter(request, response);		
	}
}
