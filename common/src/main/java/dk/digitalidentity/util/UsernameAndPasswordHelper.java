package dk.digitalidentity.util;

import java.security.SecureRandom;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.service.PasswordSettingService;
import dk.digitalidentity.common.service.PersonService;

@Component
public class UsernameAndPasswordHelper {
	private static final SecureRandom random = new SecureRandom();
	private static final char[] lowercase = "abcdefghjkmnpqrstuvwxyz".toCharArray();
	private static final char[] uppercase = "ABCDEFGJKLMNPRSTUVWXYZ".toCharArray();
	private static final char[] numbers = "123456789".toCharArray();
	private static final char[] symbols = "^$?!@#%&".toCharArray();
	private static final char[] allAllowed = "abcdefghjkmnpqrstuvwxyzABCDEFGJKLMNPRSTUVWXYZ123456789^$?!@#%&".toCharArray();

	@Autowired
	private PersonService personService;

	@Autowired
	private PasswordSettingService passwordSettingService;

	public String generatePassword(Domain domain) {
		StringBuilder password = new StringBuilder();

		long length = passwordSettingService.getSettings(domain).getMinLength();
		for (int i = 0; i < length - 4; i++) {
			password.append(allAllowed[random.nextInt(allAllowed.length)]);
		}

		password.append(lowercase[random.nextInt(lowercase.length)]);
		password.append(uppercase[random.nextInt(uppercase.length)]);
		password.append(numbers[random.nextInt(numbers.length)]);
		password.append(symbols[random.nextInt(symbols.length)]);

		return password.toString();
	}
	
	public String getUserId(Person person) {
		// if a person already has a userId, re-use that
		if (person != null && person.getUserId() != null && person.getUserId().length() > 0) {
			return person.getUserId();
		}

		String userId = null;
		int maxTries = 30;
		do {
			userId = "NS" + (random.nextInt(999999) + 100000);

			if (personService.getByUserId(userId) == null) {
				break;
			}

			// make sure userId is null if we get to this point, so all failed tries will result in null value
			userId = null;
		}
		while (--maxTries > 0);

		return userId;
	}
}