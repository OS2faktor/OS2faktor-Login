package dk.digitalidentity.common.filter;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.Enumeration;
import java.util.UUID;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import org.apache.catalina.connector.ClientAbortException;
import org.springframework.web.context.request.RequestContextHolder;

import lombok.extern.slf4j.Slf4j;

// TODO: can be replaced with "server.servlet.session.cookie.same-site=None" when we upgrade to Spring Boot 2.6.x or later
// though note the ClientAbortException stuff below
@Slf4j
public class SameSiteFilter implements Filter {
	private static final String SAMESITE_COOKIE_HEADER = "Set-Cookie";
	private static final String SAMESITE_ATTRIBITE_NAME = "SameSite";
	private static final String SAMESITE_NONE_VALUE = "None";
    
    public void init(FilterConfig filterConfig) throws ServletException {
    	;
    }
    
    public void destroy() {
    	;
    }

    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain) throws IOException, ServletException {
    	try {
	        if (response instanceof HttpServletResponse) {
	            chain.doFilter(request, new SameSiteWrapper((HttpServletResponse) response));
	        }
	        else {
	        	chain.doFilter(request, response);
	        }
    	}
    	catch (ClientAbortException ex) {
    		if (request instanceof HttpServletRequest) {
    			HttpServletRequest req = ((HttpServletRequest)request);
    			
    		    String scheme = req.getScheme();             // http
    		    String serverName = req.getServerName();     // hostname.com
    		    int serverPort = req.getServerPort();        // 80
    		    String contextPath = req.getContextPath();   // /mywebapp
    		    String servletPath = req.getServletPath();   // /servlet/MyServlet
    		    String pathInfo = req.getPathInfo();         // /a/b;c=123
    		    String queryString = req.getQueryString();   // d=789

    		    String path = scheme + "://" + serverName + ":" + serverPort + contextPath + servletPath + pathInfo + "?" + queryString;
    		    String ip = getIpAddress(req);
    		    String userAgent = req.getHeader("User-Agent");

    		    StringBuilder builder = new StringBuilder();
    		    builder.append("ClientAbortException from " + getCorrelationId() + " / " + ip + " (" + userAgent + ") @ " + path + " / Headers[");

        		Enumeration<String> headerNames = req.getHeaderNames();
        		while (headerNames.hasMoreElements()) {
        			String headerName = headerNames.nextElement();
        			String value = req.getHeader(headerName);

        			if (!headerName.equalsIgnoreCase("apikey")) {
        				builder.append(headerName + "=" + value + ", ");
        			}
        		}
        		builder.append("]");

        		// log as warn for later debugging purposes
        		log.warn(builder.toString());
    		}
    		else {
    			log.warn("ClientAbortException - but request was not HttpServletRequest, but instead " + request.getClass().getName());
        		throw ex;
    		}
    	}
    }
    
	private static String getIpAddress(HttpServletRequest request) {
		String remoteAddr = "";

		if (request != null) {
			remoteAddr = request.getHeader("X-FORWARDED-FOR");
			if (remoteAddr == null || "".equals(remoteAddr)) {
				remoteAddr = request.getRemoteAddr();
			}
		}

		return remoteAddr;
	}
	
	private static String getCorrelationId() {
		try {
			String sessionID = RequestContextHolder.currentRequestAttributes().getSessionId();
			
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] encodedHash = digest.digest(sessionID.getBytes(Charset.forName("UTF-8")));

			return bytesToHex(encodedHash);
		}
		catch (Exception ex) {
			return "SYSTEM-" + UUID.randomUUID().toString();
		}
	}

	private static String bytesToHex(byte[] hash) {
		StringBuilder hexString = new StringBuilder(2 * hash.length);

		for (int i = 0; i < hash.length; i++) {
			String hex = Integer.toHexString(0xff & hash[i]);

			if (hex.length() == 1) {
				hexString.append('0');
			}

			hexString.append(hex);
		}

		return hexString.toString();
	}

    private class SameSiteWrapper extends HttpServletResponseWrapper {
    	private HttpServletResponse response;
        
        public SameSiteWrapper(HttpServletResponse resp) {
            super(resp);

            response = resp;
        }
        
        @Override
        public void sendError(int sc) throws IOException {
            fixSameSiteCookies();

            super.sendError(sc);
        }
        
        @Override
        public PrintWriter getWriter() throws IOException {
        	fixSameSiteCookies();

            return super.getWriter();
        }
        
        @Override
        public void sendError(int sc, String msg) throws IOException {
        	fixSameSiteCookies();

            super.sendError(sc, msg);
        }
        
        @Override
        public void sendRedirect(String location) throws IOException {
        	fixSameSiteCookies();

            super.sendRedirect(location);
        }
        
        @Override
        public ServletOutputStream getOutputStream() throws IOException {
        	fixSameSiteCookies();

            return super.getOutputStream();
        }
        
        private void fixSameSiteCookies() {
            Collection<String> headers = response.getHeaders(SAMESITE_COOKIE_HEADER);
            if (headers == null || headers.size() == 0) {
            	return;
            }

            boolean firstCookie = true;
            for (String header : headers) {
                if (header == null || header.length() == 0) {
                    continue;
                }

                if (!header.contains(SAMESITE_ATTRIBITE_NAME)) {
                    header = header + ";" + SAMESITE_ATTRIBITE_NAME + "=" + SAMESITE_NONE_VALUE;                
                } 

                // overwrite existing cookies on first run, then append the new ones
                if (firstCookie) {
                    response.setHeader(SAMESITE_COOKIE_HEADER, header);
                }
                else {
                    response.addHeader(SAMESITE_COOKIE_HEADER, header);
                }

                firstCookie = false;
            }
        }
    }
}