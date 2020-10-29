package dk.digitalidentity.service.model;

import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OIOBPP {
	private String nameID;
	private String oioBPP;
	private Map<String, String> roleMap;
}
