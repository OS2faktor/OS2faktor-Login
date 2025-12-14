package dk.digitalidentity.common.service;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.service.rolecatalogue.RoleCatalogueService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AdvancedRuleService {

    @Autowired
    private RoleCatalogueService roleCatalogueService; 

	public String lookupField(Person person, String personField) {
		String attribute = null;
		
        switch (personField) {
            case "userId":
            case "sAMAccountName": // TODO: this is deprecated, but we are keeping it to support existing SPs setup with this value until they are migrated
                attribute = PersonService.getUsername(person);
                break;
            case "uuid":
                attribute = person.getUuid();
                break;
            case "cpr":
                attribute = person.getCpr();
                break;
            case "name":
            	attribute = person.getName();
            	break;
            case "alias":
            	attribute = person.getNameAlias();
            	break;
            case "email":
            	attribute = person.getEmail();
            	break;
            case "aliasFirstname":
            	try {
            		int idx = person.getNameAlias().lastIndexOf(' ');
            		
            		if (idx > 0) {
            			attribute = person.getNameAlias().substring(0, idx);
            		}
            		else {
            			attribute = person.getNameAlias();
            		}
            	}
            	catch (Exception ex) {
            		log.error("Failed to parse alias on " + person.getId(), ex);
            		attribute = person.getNameAlias();
            	}
            	break;
            case "aliasLastname":
            	try {
            		int idx = person.getNameAlias().lastIndexOf(' ');
            		
            		if (idx > 0) {
            			attribute = person.getNameAlias().substring(idx + 1);
            		}
            		else {
            			attribute = person.getNameAlias();
            		}
            	}
            	catch (Exception ex) {
            		log.error("Failed to parse alias on " + person.getId(), ex);
            		attribute = person.getNameAlias();
            	}
            	break;
            case "firstname":
            	try {
            		int idx = person.getName().lastIndexOf(' ');
            		
            		if (idx > 0) {
            			attribute = person.getName().substring(0, idx);
            		}
            		else {
            			attribute = person.getName();
            		}
            	}
            	catch (Exception ex) {
            		log.error("Failed to parse name on " + person.getId(), ex);
            		attribute = person.getName();
            	}
            	break;
            case "lastname":
            	try {
            		int idx = person.getName().lastIndexOf(' ');
            		
            		if (idx > 0) {
            			attribute = person.getName().substring(idx + 1);
            		}
            		else {
            			attribute = person.getName();
            		}
            	}
            	catch (Exception ex) {
            		log.error("Failed to parse name on " + person.getId(), ex);
            		attribute = person.getName();
            	}
            	break;
            default:
                if (person.getAttributes() != null) {
                    attribute = person.getAttributes().get(personField);
                }
        }
        
        return attribute;
	}
	
	/*
	 * Simple examples
	 * ===============
	 * JOIN(VALUE(user.name), VALUE(' '), VALUE(user.userId))   ->   "Brian Graversen bsg"
	 * UPPER(VALUE(user.userId))                                ->   "BSG"
	 * LOWER(VALUE(user.name))                                  ->   "brian graversen"
	 * 
	 * Regex replacement example
	 * =========================
	 * REGEX_REPLACE(VALUE(user.name), '\s', VALUE('_'))        ->   "Brian_Graversen"   
	 * 
	 * Combined example
	 * ================
	 * JOIN(
     *   UPPER(
     *     JOIN(
     *       VALUE(user.userId),
     *       VALUE(', '),
     *       REGEX_REPLACE(
     *         VALUE(user.name),
     *         '\s',
     *         VALUE('_')
     *       )
     *    )
     *   ),
     *   LOWER(VALUE(' xXx ')),
     *   UPPER(VALUE(user.email))
     * )
     * 
     * Above outputs "BSG BRIAN_GRAVERSEN xxx BSG@DIGITAL-IDENTITY.DK"
	 */
	
	public String evaluateRule(String rule, Person person) throws EvaluationException {
		
		// trim before evaluating
		rule = rule.trim();
		
		CommandAndArgument commandAndArgument = extractCommandAndArgument(rule);
		
		switch (commandAndArgument.command) {
			case "VALUE":
				return evaluateValue(commandAndArgument.argument, person);
			case "UPPER":
				return evaluateUpper(commandAndArgument.argument, person);
			case "LOWER":
				return evaluateLower(commandAndArgument.argument, person);
			case "JOIN":
				return evaluateJoin(commandAndArgument.argument, person);
			case "REGEX_REPLACE":
				return evaluateRegex(commandAndArgument.argument, person);
			case "BASE64":
				return evaluateBase64(commandAndArgument.argument, person);
			case "BINARY_UUID":
				return evaluateBinaryUuid(commandAndArgument.argument, person);
			case "OS2ROL_JFR" :
				return evaluateOS2rolJfr(commandAndArgument.argument, person);
			case "OS2ROL_BSR" :
				return evaluateOS2rolBsr(commandAndArgument.argument, person);
			case "IF":
				return evaluateIf(commandAndArgument.argument, person);
			case "IF_ELSE":
				return evaluateIfElse(commandAndArgument.argument, person);
			default:
				log.error("Should not get here, it should have been handled in extractCommandAndArgument: " + commandAndArgument.command);
				throw new EvaluationException("Syntaksfejl: Ukendt operation '" + commandAndArgument.command + "'");
		}
	}
	
	private String evaluateRegex(String argument, Person person) throws EvaluationException {
		List<String> tokens = tokenize(argument);
		if (tokens.size() != 3) {
			throw new EvaluationException("REGEX_REPLACE tager 3 parametre");
		}
		
		String source = evaluateRule(tokens.get(0), person);
		String replacement = evaluateJoin(tokens.get(2), person);
		String regEx = tokens.get(1).trim().replace("'", "").trim();

		return source.replaceAll(regEx, replacement);
	}
	
	private String evaluateBinaryUuid(String argument, Person person) throws EvaluationException {
		String value = evaluateRule(argument.trim(), person);

		if (!StringUtils.hasLength(value)) {
			return value;
		}

		String uuid = null;
		try {
			uuid = UUID.fromString(value).toString();
		}
		catch (Exception ex) {
			throw new EvaluationException(value + " is not a UUID");
		}

		return base64EncodeUuid(uuid);
	}

	private String base64EncodeUuid(String uuidStr) {
		UUID uuid = UUID.fromString(uuidStr);
		ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
		bb.putLong(uuid.getMostSignificantBits());
		bb.putLong(uuid.getLeastSignificantBits());

		byte[] uuidBytes = bb.array();
		byte[] guidBytes = Arrays.copyOf(uuidBytes, uuidBytes.length);

		// C# is silly, so mix up the order to match what C# would generate here
		guidBytes[0] = uuidBytes[3];
		guidBytes[1] = uuidBytes[2];
		guidBytes[2] = uuidBytes[1];
		guidBytes[3] = uuidBytes[0];
		guidBytes[4] = uuidBytes[5];
		guidBytes[5] = uuidBytes[4];
		guidBytes[6] = uuidBytes[7];
		guidBytes[7] = uuidBytes[6];

		return Base64.getEncoder().encodeToString(guidBytes);
	}

	private String evaluateBase64(String argument, Person person) throws EvaluationException {
		String value = evaluateRule(argument.trim(), person);

		if (!StringUtils.hasLength(value)) {
			return value;
		}

		return Base64.getEncoder().encodeToString(value.getBytes());
	}

	private String evaluateJoin(String argument, Person person) throws EvaluationException {
		StringBuilder builder = new StringBuilder();
		
		List<String> tokens = tokenize(argument);
		for (String token : tokens) {
			token = token.trim();
			if (!StringUtils.hasLength(token)) {
				continue;
			}
			
			builder.append(evaluateRule(token, person));
		}

		return builder.toString();
	}

	private List<String> tokenize(String argument) {
		List<String> result = new ArrayList<>();
		
		StringBuilder builder = new StringBuilder();
		int counter = 0;
		boolean inString = false;
		
		for (char c : argument.toCharArray()) {
			if (c == '(') {
				if (!inString) {
					counter++;
				}
			}
			else if (c == ')') {
				if (!inString) {
					counter--;
				}
			}
			else if (c == '\'') {
				inString = !inString;
			}
			else if (c == ',') {
				if (counter == 0) {
					result.add(builder.toString().trim());
					builder = new StringBuilder();
					
					continue;
				}
			}
			
			builder.append(c);
		}
		
		if (builder.length() > 0) {
			result.add(builder.toString().trim());
		}
		
		return result;
	}

	private String evaluateLower(String argument, Person person) throws EvaluationException {
		String value = evaluateRule(argument.trim(), person);
		
		return value.toLowerCase();
	}

	private String evaluateUpper(String argument, Person person) throws EvaluationException {
		String value = evaluateRule(argument.trim(), person);
		
		return value.toUpperCase();
	}

	private String evaluateValue(String argument, Person person) throws EvaluationException {
		if (argument.startsWith("'")) {
			int stop = argument.lastIndexOf("'");
			if (stop < 1) {
				throw new EvaluationException("Syntaksfejl: Input til VALUE skal indeholde både en start og stop ' karakter for faste værdier");
			}
			
			return argument.substring(1, stop);
		}
		else if (argument.startsWith("user.")) {
			argument = argument.substring(5);

			String value = lookupField(person, argument);
			if (value == null) {
				return "";
			}

			return value;
		}
		
		throw new EvaluationException("Syntaksfejl: Input til VALUE ikke lovligt '" + argument + "'");
	}

	private String evaluateOS2rolJfr(String argument, Person person) throws EvaluationException {
		List<String> tokens = tokenize(argument);
		
		if (tokens.size() != 2) {
			throw new EvaluationException("Syntaksfejl: Input til OS2ROL_JFR tager 2 parametre: it-system-id og separator");
		}

		String itSystemId = tokens.get(0).trim().replace("'", "").trim();
		String delimiter = tokens.get(1).trim().replace("'", "").trim();
		
		List<String> userRoles = roleCatalogueService.getUserRoles(person, itSystemId);

		return userRoles.stream().collect(Collectors.joining(delimiter));
	}

	private String evaluateIf(String argument, Person person) throws EvaluationException {
		List<String> tokens = tokenize(argument);

		if (tokens.size() != 2) {
			throw new EvaluationException("Syntaksfejl: Input til IF tager 2 parametre: kontrolværdi og værdi");
		}

		String controlValue = tokens.get(0).trim();
		String value = tokens.get(1).trim();
		
		String result = evaluateRule(controlValue, person);
		if (StringUtils.hasLength(result)) {
			return evaluateRule(value, person);
		}
		
		return "";
	}

	private String evaluateIfElse(String argument, Person person) throws EvaluationException {
		List<String> tokens = tokenize(argument);

		if (tokens.size() != 3) {
			throw new EvaluationException("Syntaksfejl: Input til IF_ELSE tager 3 parametre: kontrolværdi og værdi og alternativ værdi");
		}

		String controlValue = tokens.get(0).trim();
		String value = tokens.get(1).trim();
		String fallbackValue = tokens.get(2).trim();

		String result = evaluateRule(controlValue, person);
		if (StringUtils.hasLength(result)) {
			return evaluateRule(value, person);
		}

		return evaluateRule(fallbackValue, person);
	}

	private String evaluateOS2rolBsr(String argument, Person person) throws EvaluationException {
		List<String> tokens = tokenize(argument);
		
		if (tokens.size() != 2) {
			throw new EvaluationException("Syntaksfejl: Input til OS2ROL_BSR tager 2 parametre: it-system-id og separator");
		}

		String itSystemId = tokens.get(0).trim().replace("'", "").trim();
		String delimiter = tokens.get(1).trim().replace("'", "").trim();
		
		List<String> systemRoles = roleCatalogueService.getSystemRoles(person, itSystemId);

		return systemRoles.stream().collect(Collectors.joining(delimiter));
	}

	private CommandAndArgument extractCommandAndArgument(String rule) throws EvaluationException {
		try {
			int first = rule.indexOf("(");
			int last = rule.lastIndexOf(")");
			
			// need at least 1 character in front of the first ( so we have a command
			if (first < 1) {
				log.warn("Missing start parantheses for rule: " + rule);
				throw new EvaluationException("Syntaksfejl: Reglen indeholder ikke nogen operation, eller mangler en start-parantes");
			}
			
			if (last < first) {
				log.warn("Missing stop parantheses for rule: " + rule);
				throw new EvaluationException("Syntaksfejl: Reglen mangler en slut-parantes");
			}
			
			CommandAndArgument result = new CommandAndArgument();
			result.command = rule.substring(0, first);
			result.argument = rule.substring(first + 1, last).trim();

			if (!knownCommand(result.command)) {
				throw new EvaluationException("Syntaksfejl: Ukendt operation '" + result.command + "'");
			}
			
			if (!StringUtils.hasLength(result.argument)) {
				throw new EvaluationException("Syntaksfejl: Operationen '" + result.command + "' har ikke noget input");
			}

			return result;
		}
		catch (Exception ex) {
			if (ex instanceof EvaluationException) {
				throw ex;
			}

			log.warn("Failed to parse rule: " + ex.getMessage());
			throw new EvaluationException("Teknisk fejl: " + ex.getMessage());
		}
	}

	private boolean knownCommand(String command) {
		switch (command) {
			case "REGEX_REPLACE":
			case "UPPER":
			case "LOWER":
			case "JOIN":
			case "VALUE":
			case "BASE64":
			case "BINARY_UUID":
			case "OS2ROL_JFR":
			case "OS2ROL_BSR":
			case "IF":
			case "IF_ELSE":
				return true;
			default:
				return false;
		}
	}

	class CommandAndArgument {
		String command;
		String argument;
	}
}
