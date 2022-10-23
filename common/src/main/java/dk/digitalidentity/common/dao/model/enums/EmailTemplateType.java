package dk.digitalidentity.common.dao.model.enums;

import java.util.Arrays;
import java.util.List;

import lombok.Getter;

@Getter
public enum EmailTemplateType {
	PERSON_DEACTIVATED("html.enum.email.type.person_deactivated", Arrays.asList(Flags.EBOKS, Flags.EMAIL)),
	PERSON_SUSPENDED("html.enum.email.type.person_suspended", Arrays.asList(Flags.EBOKS, Flags.EMAIL)),
	PERSON_DEACTIVATION_REPEALED("html.enum.email.type.person_deactivation_repealed", Arrays.asList(Flags.EBOKS, Flags.EMAIL)),
	PERSON_DEACTIVATED_CORE_DATA("html.enum.email.type.person_deactivated_core_data", Arrays.asList(Flags.EBOKS)),
	NSIS_ALLOWED("html.enum.email.type.nsis_allowed", Arrays.asList(Flags.EBOKS, Flags.EMAIL));
	
	private String message;
	private boolean eboks;
	private boolean email;
	
	private EmailTemplateType(String message, List<Flags> flags) {
		this.message = message;
		this.eboks = flags.contains(Flags.EBOKS);
		this.email = flags.contains(Flags.EMAIL);
	}
	
	private enum Flags { EBOKS, EMAIL };
}
