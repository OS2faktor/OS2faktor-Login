package dk.digitalidentity.api.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoreDataDeltaJfr {
	private String domain;
	private List<CoreDataDeltaJfrEntry> entryList;
}
