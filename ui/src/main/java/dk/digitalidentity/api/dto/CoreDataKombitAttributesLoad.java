package dk.digitalidentity.api.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoreDataKombitAttributesLoad {
	private String domain;
	private List<CoreDataKombitAttributeEntry> entryList;
}
