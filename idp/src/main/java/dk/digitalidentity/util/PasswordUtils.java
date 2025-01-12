package dk.digitalidentity.util;

import java.security.SecureRandom;
import java.util.Random;

public class PasswordUtils {
	private static char[] SYMBOLS = "*[]{}()?-@#%&/,><:;_".toCharArray();
	private static char[] LOWERCASE = "abcdefghijklmnopqrstuvwxyz".toCharArray();
	private static char[] UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
	private static char[] NUMBERS = "0123456789".toCharArray();
	private static char[] ALL_CHARS = (new String(SYMBOLS) + new String(LOWERCASE) + new String(UPPERCASE) + new String(NUMBERS)).toCharArray();
	private static Random rand = new SecureRandom();

	public static String getPassword(int length) {
		if (length < 4) {
			length = 4;
		}
		char[] password = new char[length];

		password[0] = LOWERCASE[rand.nextInt(LOWERCASE.length)];
		password[1] = UPPERCASE[rand.nextInt(UPPERCASE.length)];
		password[2] = NUMBERS[rand.nextInt(NUMBERS.length)];
		password[3] = SYMBOLS[rand.nextInt(SYMBOLS.length)];

		for (int i = 4; i < length; i++) {
			password[i] = ALL_CHARS[rand.nextInt(ALL_CHARS.length)];
		}

		for (int i = 0; i < password.length; i++) {
			int randomPosition = rand.nextInt(password.length);
			char temp = password[i];
			password[i] = password[randomPosition];
			password[randomPosition] = temp;
		}

		return new String(password);
	}
}