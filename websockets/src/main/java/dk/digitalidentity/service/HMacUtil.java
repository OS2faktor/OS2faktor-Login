package dk.digitalidentity.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class HMacUtil {
	private static final String HMAC_ALGORITHM = "HmacSHA256";

	public static String hmac(String message, String key) throws Exception {
		// generate key from raw bytes
        byte[] byteKey = key.getBytes(StandardCharsets.UTF_8);
        SecretKeySpec keySpec = new SecretKeySpec(byteKey, HMAC_ALGORITHM);

        // generate hmac algorithm
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(keySpec);
        
        // perform hmac
        byte[] macData = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(macData);
	}
}
