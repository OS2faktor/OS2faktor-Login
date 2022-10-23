package dk.digitalidentity.api.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class StilData {
    private String domainName;
    private List<StilGroup> studentGroups;
    private List<StilPerson> people;
}
