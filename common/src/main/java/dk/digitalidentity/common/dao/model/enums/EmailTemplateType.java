package dk.digitalidentity.common.dao.model.enums;

import java.util.Arrays;
import java.util.List;

import lombok.Getter;

@Getter
public enum EmailTemplateType {

	// send to person
	PERSON_DEACTIVATED("html.enum.email.type.person_deactivated", Arrays.asList(Flags.EBOKS, Flags.EMAIL)),                                           // Erhvervsidentitet spærret af administrator
	PERSON_DEACTIVATION_REPEALED("html.enum.email.type.person_deactivation_repealed", Arrays.asList(Flags.EBOKS, Flags.EMAIL)),                       // Suspendering af erhvervsidentitet ophævet
	PERSON_DEACTIVATED_CORE_DATA("html.enum.email.type.person_deactivated_core_data", Arrays.asList(Flags.EBOKS)),                                    // Erhvervsidentitet spærret
	NSIS_ALLOWED("html.enum.email.type.nsis_allowed", Arrays.asList(Flags.EBOKS, Flags.EMAIL)),                                                       // Erhvervsidentitet tildelt
	PASSWORD_EXPIRES("html.enum.email.type.password_expires", Arrays.asList(Flags.EBOKS, Flags.EMAIL)),                                               // Kodeord udløber
	PERSON_DISENFRANCHISED("html.enum.email.type.person_disenfranchised", Arrays.asList(Flags.EBOKS, Flags.EMAIL)),                                   // Erhvervsidentitet spærret grundet ugyldig cpr status
	MITID_ACTIVATED("html.enum.email.type.mitid.activated", Arrays.asList(Flags.EBOKS, Flags.EMAIL)),                                                 // MitID Erhverv konto aktiveret
	MITID_DEACTIVATED("html.enum.email.type.mitid.deactivated", Arrays.asList(Flags.EBOKS, Flags.EMAIL)),                                             // MitID Erhverv konto spærret
	TOO_MANY_PASSWORD_WRONG_NON_WHITELIST("html.enum.email.type.too_many_password_wrong_non_whitelist", Arrays.asList(Flags.EBOKS, Flags.EMAIL)),     // Antal forkert indtastede kodeord overskredet fra ukendt netadresse
	NEW_LOGIN_FOREIGN_COUNTRY("html.enum.email.type.new_login_foreign_country", Arrays.asList(Flags.EBOKS, Flags.EMAIL)),                             // Login i nyt land registreret
	NEW_USER("html.enum.email.type.new_user", Arrays.asList(Flags.EBOKS, Flags.EMAIL)),                                                               // Brugerkonto oprettet
	PASSWORD_LEAKED("html.enum.email.type.password_leaked", Arrays.asList(Flags.EBOKS, Flags.EMAIL)),                                                 // Brugers kodeord er fundet i liste over lækkede kodeord
	
	// logwatch
	TOO_MANY_WRONG_PASSWORD("html.enum.email.type.too_many_wrong_password", Arrays.asList(Flags.EMAIL, Flags.LOG_WATCH)),
	TOO_MANY_LOCKED_ACCOUNTS("html.enum.email.type.too_many_locked_accounts", Arrays.asList(Flags.EMAIL, Flags.LOG_WATCH)),
	TWO_COUNTRIES_ONE_HOUR("html.enum.email.type.two_countries_one_hour", Arrays.asList(Flags.EMAIL, Flags.LOG_WATCH)),

	// full service IdP
	FULL_SERVICE_IDP_ASSIGNED("html.enum.email.type.fullservice.assigned", Arrays.asList(Flags.EMAIL, Flags.EBOKS, Flags.FULL_SERVICE_IDP)),
	FULL_SERVICE_IDP_REMOVED("html.enum.email.type.fullservice.removed", Arrays.asList(Flags.EMAIL, Flags.EBOKS, Flags.FULL_SERVICE_IDP));

	private String message;
	private boolean eboks;
	private boolean email;
	private boolean logWatch;
	private boolean fullServiceIdP;

	private EmailTemplateType(String message, List<Flags> flags) {
		this.message = message;
		this.eboks = flags.contains(Flags.EBOKS);
		this.email = flags.contains(Flags.EMAIL);
		this.logWatch = flags.contains(Flags.LOG_WATCH);
		this.fullServiceIdP = flags.contains(Flags.FULL_SERVICE_IDP);
	}

	private enum Flags { EBOKS, EMAIL, LOG_WATCH, FULL_SERVICE_IDP }
}
