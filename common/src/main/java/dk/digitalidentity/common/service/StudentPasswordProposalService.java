package dk.digitalidentity.common.service;

import dk.digitalidentity.common.dao.model.PasswordSetting;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.SchoolClass;
import dk.digitalidentity.common.service.enums.ChangePasswordResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class StudentPasswordProposalService {
	private final SecureRandom random = new SecureRandom();

	@Autowired
	private PersonService personService;

	@Autowired
	private SchoolClassService schoolClassService;

	@Autowired
	private PasswordValidationService passwordValidationService;

	@Autowired
	private PasswordSettingService passwordSettingService;

	public String getPasswordProposal(Person student, boolean fullValidation) {
		SchoolClass indskolingSpecialClass = personService.isYoungStudent(student);
		PasswordSetting passwordSetting = passwordSettingService.getSettingsCached(passwordSettingService.getSettingsDomainForPerson(student));

		List<String> allowedWordsForPassword = schoolClassService.getEasyWords();
		if (indskolingSpecialClass != null) {
			allowedWordsForPassword = indskolingSpecialClass.getPasswordWords().stream().map(c -> c.getWord()).collect(Collectors.toList());
		}

		// filter words based on password rules
		List<String> filteredWords = filterAllowedWords(student, passwordSetting, allowedWordsForPassword);

		if (filteredWords.isEmpty()) {
			log.warn("No allowed words available for password generation for student: " + student.getSamaccountName());
			return null;
		}

		int maxAttempts = 50;

		for (int attempt = 0; attempt < maxAttempts; attempt++) {
			String proposedPassword = generatePassword(passwordSetting, filteredWords, random);

			if (proposedPassword == null) {
				continue;
			}

			// validate the proposed password
			ChangePasswordResult result;
			if (fullValidation) {
				result = passwordValidationService.validatePasswordRules(student, proposedPassword, false, false);
			}
			else {
				result = passwordValidationService.validatePasswordRulesWithoutSlowValidationRules(student, proposedPassword);
			}

			if (result == ChangePasswordResult.OK) {
				return proposedPassword;
			}
		}

		log.error("Could not generate valid password proposal for student: " + student.getSamaccountName());
		return null;
	}

	private List<String> filterAllowedWords(Person student, PasswordSetting passwordSetting, List<String> words) {
		List<String> filtered = new ArrayList<>(words);

		// remove words with Danish characters if not allowed
		if (passwordSetting.isDisallowDanishCharacters()) {
			filtered = filtered.stream()
				.filter(word -> !word.matches(".*[æøåÆØÅ].*"))
				.collect(Collectors.toList());
		}

		// remove words that match student's name or username
		if (passwordSetting.isDisallowNameAndUsername() || passwordSetting.isRequireComplexPassword()) {
			Set<String> forbiddenWords = new HashSet<>();

			if (StringUtils.hasText(student.getName())) {
				for (String namePart : student.getName().toLowerCase().split(" ")) {
					if (namePart.length() > 2) {
						forbiddenWords.add(namePart);
					}
				}
			}

			if (student.getSamaccountName() != null) {
				forbiddenWords.add(student.getSamaccountName().toLowerCase());
			}

			final Set<String> forbidden = forbiddenWords;
			filtered = filtered.stream()
				.filter(word -> !forbidden.contains(word.toLowerCase()))
				.collect(Collectors.toList());
		}

		return filtered;
	}

	private String generatePassword(PasswordSetting passwordSetting, List<String> allowedWords, Random random) {
		StringBuilder password = new StringBuilder();

		int minLength = passwordSetting.getMinLength().intValue();
		int maxLength = passwordSetting.getMaxLength().intValue();

		// Ensure we have the required character types
		boolean needsUppercase = passwordSetting.isRequireUppercaseLetters() || passwordSetting.isRequireComplexPassword();
		boolean needsLowercase = passwordSetting.isRequireLowercaseLetters() || passwordSetting.isRequireComplexPassword();
		boolean needsDigit = passwordSetting.isRequireDigits() || passwordSetting.isRequireComplexPassword();
		boolean needsSpecial = passwordSetting.isRequireSpecialCharacters() || passwordSetting.isRequireComplexPassword();

		// Calculate space needed for non-word characters
		int digitsToAdd = needsDigit ? 2 : 0;
		int specialToAdd = needsSpecial ? 1 : 0;
		int nonWordChars = digitsToAdd + specialToAdd;

		// Calculate target length for words only
		int targetWordLength = minLength - nonWordChars;
		int maxWordLength = maxLength - nonWordChars;

		// Keep track of used words to avoid repetition
		Set<String> usedWords = new HashSet<>();

		// Add words until we reach appropriate length
		int currentWordLength = 0;
		while (currentWordLength < targetWordLength) {
			// Find an unused word that fits
			String word = null;
			int attempts = 0;

			// Try to find an unused word that doesn't make us exceed maxWordLength
			while (attempts < allowedWords.size() * 2 && word == null) {
				String candidate = allowedWords.get(random.nextInt(allowedWords.size()));
				String candidateLower = candidate.toLowerCase();

				// Check if word would fit and hasn't been used
				if (currentWordLength + candidate.length() <= maxWordLength) {
					if (!usedWords.contains(candidateLower)) {
						word = candidate;
						usedWords.add(candidateLower);
						break;
					}
					else if (attempts > allowedWords.size()) {
						// Allow reuse if we've tried many times
						word = candidate;
						break;
					}
				}
				attempts++;
			}

			// If we couldn't find a word that fits, break
			if (word == null) {
				break;
			}

			// Add word with capitalization if needed
			if (needsUppercase && word.length() > 0) {
				word = Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase();
			}
			else {
				word = word.toLowerCase();
			}

			password.append(word);
			currentWordLength += word.length();
		}

		// Ensure lowercase is present (should be from words, but check)
		if (needsLowercase && !password.toString().matches(".*[a-zæøå].*")) {
			password.append((char)('a' + random.nextInt(26)));
		}

		// Add digits
		if (needsDigit) {
			for (int i = 0; i < digitsToAdd; i++) {
				password.append(random.nextInt(10));
			}
		}

		// Add special character
		if (needsSpecial) {
			String specialChar = getRandomSpecialCharacter(passwordSetting, random);
			password.append(specialChar);
		}

		// Pad with digits if we're still below minLength (shouldn't happen often)
		while (password.length() < minLength) {
			password.append(random.nextInt(10));
		}

		// Truncate to maxLength if somehow we exceeded it
		if (password.length() > maxLength) {
			password.setLength(maxLength);
		}

		return password.toString();
	}

	private String getRandomSpecialCharacter(PasswordSetting passwordSetting, Random random) {
		String allowedChars;

		if (passwordSetting.isSpecificSpecialCharactersEnabled() && StringUtils.hasLength(passwordSetting.getAllowedSpecialCharacters())) {
			allowedChars = passwordSetting.getAllowedSpecialCharacters();
		}
		else {
			// default special characters that match the validation pattern [^\wæøå\d]
			allowedChars = "!@#$%&*()-_=+[]{}|;:,.<>?/";
		}

		if (allowedChars.length() == 0) {
			return "!"; // fallback
		}

		return String.valueOf(allowedChars.charAt(random.nextInt(allowedChars.length())));
	}
}
