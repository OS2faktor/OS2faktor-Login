package dk.digitalidentity.service.dto.KombitJfr;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoreDataDeltaJfr {
	private String domain;
	private List<CoreDataDeltaJfrEntry> entryList;
}
