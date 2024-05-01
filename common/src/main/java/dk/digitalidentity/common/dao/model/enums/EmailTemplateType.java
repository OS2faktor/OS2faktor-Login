package dk.digitalidentity.common.dao.model.enums;

import java.util.Arrays;
import java.util.List;

import lombok.Getter;

@Getter
public enum EmailTemplateType {

	// send to person
	PERSON_DEACTIVATED("html.enum.email.type.person_deactivated", Arrays.asList(Flags.EBOKS, Flags.EMAIL)),
	PERSON_DEACTIVATION_REPEALED("html.enum.email.type.person_deactivation_repealed", Arrays.asList(Flags.EBOKS, Flags.EMAIL)),
	PERSON_DEACTIVATED_CORE_DATA("html.enum.email.type.person_deactivated_core_data", Arrays.asList(Flags.EBOKS)),
	NSIS_ALLOWED("html.enum.email.type.nsis_allowed", Arrays.asList(Flags.EBOKS, Flags.EMAIL)),
	PASSWORD_EXPIRES("html.enum.email.type.password_expires", Arrays.asList(Flags.EBOKS, Flags.EMAIL)),
	PERSON_DISENFRANCHISED("html.enum.email.type.person_disenfranchised", Arrays.asList(Flags.EBOKS, Flags.EMAIL)),
	MITID_ACTIVATED("html.enum.email.type.mitid.activated", Arrays.asList(Flags.EBOKS, Flags.EMAIL)),
	MITID_DEACTIVATED("html.enum.email.type.mitid.deactivated", Arrays.asList(Flags.EBOKS, Flags.EMAIL)),
	TOO_MANY_PASSWORD_WRONG_NON_WHITELIST("html.enum.email.type.too_many_password_wrong_non_whitelist", Arrays.asList(Flags.EBOKS, Flags.EMAIL)),
	NEW_LOGIN_FOREIGN_COUNTRY("html.enum.email.type.new_login_foreign_country", Arrays.asList(Flags.EBOKS, Flags.EMAIL)),
	NEW_USER("html.enum.email.type.new_user", Arrays.asList(Flags.EMAIL)),

	// logwatch
	TOO_MANY_WRONG_PASSWORD("html.enum.email.type.too_many_wrong_password", Arrays.asList(Flags.EMAIL, Flags.LOG_WATCH)),
	TOO_MANY_LOCKED_ACCOUNTS("html.enum.email.type.too_many_locked_accounts", Arrays.asList(Flags.EMAIL, Flags.LOG_WATCH)),
	TWO_COUNTRIES_ONE_HOUR("html.enum.email.type.two_countries_one_hour", Arrays.asList(Flags.EMAIL, Flags.LOG_WATCH));

	private String message;
	private boolean eboks;
	private boolean email;
	private boolean logWatch;
	
	private EmailTemplateType(String message, List<Flags> flags) {
		this.message = message;
		this.eboks = flags.contains(Flags.EBOKS);
		this.email = flags.contains(Flags.EMAIL);
		this.logWatch = flags.contains(Flags.LOG_WATCH);
	}
	
	private enum Flags { EBOKS, EMAIL, LOG_WATCH }
}
