package dk.digitalidentity.service.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CoreDataDelete {
    private String domain;
    private List<CoreDataDeleteEntry> entryList;
}
