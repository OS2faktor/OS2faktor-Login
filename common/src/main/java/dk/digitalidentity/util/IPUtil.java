package dk.digitalidentity.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;

public class IPUtil {

	public static String getIpAddress(HttpServletRequest request) {
		String remoteAddr = "";

		if (request != null) {
			remoteAddr = request.getHeader("X-FORWARDED-FOR");
			if (remoteAddr == null || "".equals(remoteAddr)) {
				remoteAddr = request.getRemoteAddr();
			}
		}

		return remoteAddr;
	}

	public static List<IpAddressMatcher> createAllowList(String csv) {
		if (!StringUtils.hasLength(csv)) {
			return new ArrayList<>();
		}

		return Arrays.stream(csv.split(",")).map(IpAddressMatcher::new).collect(Collectors.toList());
	}

	public static List<IpAddressMatcher> createAllowList(List<String> ips) {
		if (ips == null || ips.isEmpty()) {
			return new ArrayList<>();
		}

		return ips.stream().map(IpAddressMatcher::new).collect(Collectors.toList());
	}

	public static boolean isIpInTrustedNetwork(List<String> ips, HttpServletRequest request) {
		List<IpAddressMatcher> allowList = createAllowList(ips);

		boolean allowed = false;
		for (IpAddressMatcher matcher : allowList) {
			if (matcher.matches(getIpAddress(request))) {
				allowed = true;
			}
		}
		return allowed;
	}
}
