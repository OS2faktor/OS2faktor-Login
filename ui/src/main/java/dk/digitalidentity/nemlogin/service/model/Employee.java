package dk.digitalidentity.nemlogin.service.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class Employee {
	private long id;
    private String status;
    private EmployeeProfile profile;
    private String uuid;
    private String emailAddress;
    private String rid;
    private List<EmployeeCredential> employeeCredentials;
    
    // actually need to look this up through FullEmployee, so this is just a convenience holder
    private transient boolean qualifiedSignature;
}
