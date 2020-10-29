package dk.digitalidentity.api.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoreData {
	private String domain;
	private List<CoreDataEntry> entryList;
}
