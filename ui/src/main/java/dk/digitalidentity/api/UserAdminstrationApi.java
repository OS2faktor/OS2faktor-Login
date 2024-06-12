package dk.digitalidentity.api;

import dk.digitalidentity.api.dto.ForceChangePwd;
import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.service.DomainService;
import dk.digitalidentity.common.service.PersonService;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

@Slf4j
@RestController
public class UserAdminstrationApi {
	
    @Autowired
    private PersonService personService;
    
    @Autowired
    private DomainService domainService;
    
    @PostMapping("/api/userAdminstration/forcepwchange")
    public ResponseEntity<?> forceChangePassword(@RequestBody ForceChangePwd forceChangePwd) {
    	Domain domain = domainService.getByName(forceChangePwd.getDomain());
		if (Objects.equals(null, domain)) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No domain found with name: " + forceChangePwd.getDomain());
		}

        List<Person> persons = personService.getBySamaccountNameAndDomain(forceChangePwd.getSamAccountName(), domain);
		if (persons.size() != 1) {
		    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No person found with the given sAMAccountName: " + forceChangePwd.getSamAccountName() + " in the domain: " + forceChangePwd.getDomain());
        }

		Person person = persons.get(0);
		
		log.info("Setting forced password change on " + person.getSamaccountName());
		
        person.setForceChangePassword(true);
        personService.save(person);

        return new ResponseEntity<>(HttpStatus.OK);
    }
}
