package dk.digitalidentity.mvc.selfservice;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.context.support.ResourceBundleMessageSource;

import lombok.Getter;

@Getter
public enum NSISStatus {
	NOT_ISSUED("enum.nsisstatus.notIssued"),
	NOT_ACTIVATED("enum.nsisstatus.notActivated"),
	LOCKED_BY_SELF("enum.nsisstatus.lockedBySelf"),
	LOCKED_BY_MUNICIPALITY("enum.nsisstatus.lockedByMunicipality"),
	LOCKED_BY_STATUS("enum.nsisstatus.lockedByStatus"),
	LOCKED_BY_EXPIRE("enum.nsisstatus.lockedByExpire"),
	ACTIVE("enum.nsisstatus.active");

	private String message;

	NSISStatus(String message) {
		this.message = message;
	}
	
	public static List<NSISStatusDTO> getSorted(ResourceBundleMessageSource resourceBundle, Locale locale) {
		List<NSISStatusDTO> dtos = new ArrayList<>();

		for (NSISStatus logAction : NSISStatus.values()) {
			String newMessage = resourceBundle.getMessage(logAction.getMessage(), null, locale);
			NSISStatusDTO dto = new NSISStatusDTO();
			dto.setNsisStatus(logAction);
			dto.setMessage(newMessage);
			
			dtos.add(dto);
		}
		
		dtos.sort((a, b) -> a.getMessage().compareToIgnoreCase(b.getMessage()));

		return dtos;
	}
}
