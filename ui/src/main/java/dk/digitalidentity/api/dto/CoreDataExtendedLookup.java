package dk.digitalidentity.api.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoreDataExtendedLookup {
	// status in MitID Erhverv
	private String mitIdErhvervStatus;
	private String mitIdErhvervUuid;
	private String mitIdErhvervRid;
	private boolean mitIdErhvervPrivateMitID;
	private boolean mitIdErhvervQualifiedSignature;
	
	// status in OS2faktor
	private boolean os2faktorLocked;
	private boolean os2faktorActiveCorporateId;
	private boolean os2faktorBadPassword;
}
