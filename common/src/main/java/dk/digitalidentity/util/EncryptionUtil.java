package dk.digitalidentity.util;

import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.config.CommonConfiguration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public final class EncryptionUtil {
	private SecretKeySpec secretKey;

	@Autowired
	private CommonConfiguration commonConfiguration;

	public String encryptPassword(String password) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, UnsupportedEncodingException, BadPaddingException, IllegalBlockSizeException, InvalidAlgorithmParameterException {
		SecretKeySpec key = getKey(commonConfiguration.getAd().getPasswordSecret());
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(128, new byte[]{0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00});
		cipher.init(Cipher.ENCRYPT_MODE, key, gcmParameterSpec);

		return Base64.getEncoder().encodeToString(cipher.doFinal(password.getBytes("UTF-8")));
	}

	public String decryptPassword(String encryptedPassword) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException, InvalidAlgorithmParameterException {
		SecretKeySpec key = getKey(commonConfiguration.getAd().getPasswordSecret());
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(128, new byte[]{0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00});
		cipher.init(Cipher.DECRYPT_MODE, key, gcmParameterSpec);
		return new String(cipher.doFinal(Base64.getDecoder().decode(encryptedPassword)));
	}

	private SecretKeySpec getKey(String myKey) {
		if (secretKey != null) {
			return secretKey;
		}

		byte[] key;
		MessageDigest sha = null;
		try {
			key = myKey.getBytes("UTF-8");
			sha = MessageDigest.getInstance("SHA-1");
			key = sha.digest(key);
			key = Arrays.copyOf(key, 16);
			secretKey = new SecretKeySpec(key, "AES");
		}
		catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
			log.error("Error in generating key", e);
		}

		return secretKey;
	}
}
