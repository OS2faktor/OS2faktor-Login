package dk.digitalidentity.interceptors;

import org.apache.tomcat.util.http.fileupload.FileUploadException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartException;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ControllerAdvice
public class CustomExceptionHandler {

	// we get some attempts to bombard our endpoint with crap payloads, and these logs ERROR by default, so those
	// are suppressed as warnings instead. Expand as needed
    @ResponseStatus(code = HttpStatus.BAD_REQUEST)
    @ExceptionHandler({ MultipartException.class, InvalidMediaTypeException.class, HttpMediaTypeNotAcceptableException.class, FileUploadException.class })
    public void handleException(HttpServletRequest request, Exception ex) {
    	if (ex instanceof MultipartException) {
            log.warn("suppressing MultiPartException: remote={}, user_agent={}, request_url={}, request_method={}, message={}",
                	getIpAddress(request),
                    request.getHeader(HttpHeaders.USER_AGENT),
                    request.getRequestURL(),
                    request.getMethod(),
                    ex.getMessage());
    	}
    	else if (ex instanceof InvalidMediaTypeException) {
            log.warn("suppressing InvalidMediaTypeException: remote={}, user_agent={}, request_url={}, request_method={}, message={}",
                	getIpAddress(request),
                    request.getHeader(HttpHeaders.USER_AGENT),
                    request.getRequestURL(),
                    request.getMethod(),
                    ex.getMessage());
    	}
    	else if (ex instanceof HttpMediaTypeNotAcceptableException) {
            log.warn("suppressing HttpMediaTypeNotAcceptableException: remote={}, user_agent={}, request_url={}, request_method={}, message={}",
                	getIpAddress(request),
                    request.getHeader(HttpHeaders.USER_AGENT),
                    request.getRequestURL(),
                    request.getMethod(),
                    ex.getMessage());
    	}
    	else if (ex instanceof FileUploadException) {
            log.warn("suppressing FileUploadException: remote={}, user_agent={}, request_url={}, request_method={}, message={}",
                	getIpAddress(request),
                    request.getHeader(HttpHeaders.USER_AGENT),
                    request.getRequestURL(),
                    request.getMethod(),
                    ex.getMessage());
    	}
    	else {
    		log.error("Exception caught from controller", ex);
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
}
