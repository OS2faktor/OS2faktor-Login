package dk.digitalidentity.common.config.modules.school;

import java.util.ArrayList;
import java.util.List;

import dk.digitalidentity.common.config.FeatureDocumentation;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StudentPwdConfiguration {

	@FeatureDocumentation(name = "Kodeordsskifte på elever", description = "Gør det muligt for udvalgte skole-roller at skifte kodeord på elever")
	private boolean enabled;
	
	private boolean frontPageLinkEnabled;
	
	// this setting shows the dropdown special for selecting a new password
	private boolean indskolingSpecialEnabled;

	// default setting, can always be overridden by teacher
	private long forceChangePasswordAfterAge = 10;

	// student needs to change password at next login
	private boolean forceChangePasswordTeacherChoiceEnabled = true;

	// max level for bulk change password
	private Long bulkChangePasswordOnLevelAndBelow = 0L;

	// for loading data from STIL
	private String apiKey;
	private List<StudentPwdRoleSettingConfiguration> roleSettings = new ArrayList<>();
	private List<StudentPwdSpecialNeedsClassConfiguration> specialNeedsClasses = new ArrayList<>();
}
