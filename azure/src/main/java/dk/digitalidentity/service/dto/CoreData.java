package dk.digitalidentity.service.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CoreData {
	private String domain;
	private List<CoreDataEntry> entryList;
}
