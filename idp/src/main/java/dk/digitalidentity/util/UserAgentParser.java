package dk.digitalidentity.util;

import org.springframework.stereotype.Component;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Component
public class UserAgentParser {

	// Android device patterns
	private static final String[] ANDROID_PATTERNS = { "android", "mobile", "tablet" };

	// iOS device patterns
	private static final String[] IOS_PATTERNS = { "iphone", "ipad", "ipod", "ios" };

	// Additional mobile device patterns
	private static final String[] MOBILE_DEVICE_PATTERNS = { "blackberry", "bb10", "rim tablet", "windows phone",
			"windows mobile", "iemobile", "wpdesktop", "palm", "webos", "symbian", "series60", "nokia", "samsung",
			"htc", "lg", "motorola", "sony", "huawei", "xiaomi", "oneplus", "oppo", "vivo" };

	// Browser-specific patterns for mobile detection
	private static final String[] MOBILE_BROWSER_PATTERNS = {
			// Chrome Mobile
			"chrome.*mobile", "crios", // Chrome on iOS

			// Firefox Mobile
			"firefox.*mobile", "fxios", // Firefox on iOS

			// Safari Mobile
			"safari.*mobile", "version.*mobile.*safari",

			// Edge Mobile
			"edge.*mobile", "edgios", // Edge on iOS
			"edga", // Edge on Android

			// Brave Browser
			"brave.*mobile",

			// Samsung Internet
			"samsungbrowser",

			// Opera Mobile
			"opera.*mobile", "opera.*mini", "opr.*mobile",

			// UC Browser
			"ucbrowser", "uc browser",

			// Dolphin Browser
			"dolphin",

			// Other mobile browsers
			"mobile.*webkit", "mobile.*safari" };

	/**
	 * Detects if the user agent represents a mobile device
	 */
	public boolean isMobile(String userAgent) {
		if (userAgent == null || userAgent.trim().isEmpty()) {
			return false;
		}

		String ua = userAgent.toLowerCase();

		// Check for explicit mobile indicators
		if (containsAny(ua, ANDROID_PATTERNS) || containsAny(ua, IOS_PATTERNS)
				|| containsAny(ua, MOBILE_DEVICE_PATTERNS)) {
			return true;
		}

		// Check for mobile browser patterns
		if (containsAnyPattern(ua, MOBILE_BROWSER_PATTERNS)) {
			return true;
		}

		return false;
	}

	/**
	 * Detects if the user agent represents a tablet device
	 */
	public boolean isTablet(String userAgent) {
		if (userAgent == null || userAgent.trim().isEmpty()) {
			return false;
		}

		String ua = userAgent.toLowerCase();

		// iPad detection
		if (ua.contains("ipad")) {
			return true;
		}

		// Android tablet detection (Android without "mobile" keyword)
		if (ua.contains("android") && !ua.contains("mobile")) {
			return true;
		}

		// Additional tablet patterns
		String[] tabletPatterns = { "tablet", "kindle", "silk", "playbook", "rim tablet" };

		return containsAny(ua, tabletPatterns);
	}

	/**
	 * Detects if the user agent represents a smartphone (mobile but not tablet)
	 */
	public boolean isSmartphone(String userAgent) {
		return isMobile(userAgent) && !isTablet(userAgent);
	}

	/**
	 * Detects if the user agent represents a desktop/laptop
	 */
	public boolean isDesktop(String userAgent) {
		return !isMobile(userAgent);
	}

	/**
	 * Gets device type as enum
	 */
	public DeviceType getDeviceType(String userAgent) {
		if (isTablet(userAgent)) {
			return DeviceType.TABLET;
		} else if (isSmartphone(userAgent)) {
			return DeviceType.SMARTPHONE;
		} else {
			return DeviceType.DESKTOP;
		}
	}

	/**
	 * Detects the browser type
	 */
	public BrowserType getBrowserType(String userAgent) {
		if (userAgent == null) {
			return BrowserType.UNKNOWN;
		}

		String ua = userAgent.toLowerCase();

		// Order matters - check more specific patterns first
		if (ua.contains("edgios") || ua.contains("edga") || ua.contains("edge")) {
			return BrowserType.EDGE;
		}
		if (ua.contains("brave")) {
			return BrowserType.BRAVE;
		}
		if (ua.contains("crios") || ua.contains("chrome")) {
			return BrowserType.CHROME;
		}
		if (ua.contains("fxios") || ua.contains("firefox")) {
			return BrowserType.FIREFOX;
		}
		if (ua.contains("safari") && !ua.contains("chrome")) {
			return BrowserType.SAFARI;
		}
		if (ua.contains("samsungbrowser")) {
			return BrowserType.SAMSUNG_INTERNET;
		}
		if (ua.contains("opera") || ua.contains("opr")) {
			return BrowserType.OPERA;
		}

		return BrowserType.UNKNOWN;
	}

	/**
	 * Gets detailed device information
	 */
	public DeviceInfo getDeviceInfo(String userAgent) {
		return DeviceInfo.builder().userAgent(userAgent).deviceType(getDeviceType(userAgent))
				.browserType(getBrowserType(userAgent)).isMobile(isMobile(userAgent)).isTablet(isTablet(userAgent))
				.isSmartphone(isSmartphone(userAgent)).isDesktop(isDesktop(userAgent)).build();
	}

	// Helper methods
	private boolean containsAny(String text, String[] patterns) {
		for (String pattern : patterns) {
			if (text.contains(pattern)) {
				return true;
			}
		}
		return false;
	}

	private boolean containsAnyPattern(String text, String[] patterns) {
		for (String pattern : patterns) {
			if (text.matches(".*" + pattern + ".*")) {
				return true;
			}
		}
		return false;
	}

	// Enums
	public enum DeviceType {
		SMARTPHONE, TABLET, DESKTOP
	}

	public enum BrowserType {
		CHROME, FIREFOX, SAFARI, EDGE, BRAVE, SAMSUNG_INTERNET, OPERA, UNKNOWN
	}

	@Builder
	@Getter
	@Setter
	public static class DeviceInfo {
		private String userAgent;
		private DeviceType deviceType;
		private BrowserType browserType;
		private boolean isMobile;
		private boolean isTablet;
		private boolean isSmartphone;
		private boolean isDesktop;
	}
}
