package dk.digitalidentity.common.dao.bootstrap;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.PersonDao;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DevBootstrapper {

	@Autowired
	private PersonDao personDao;
	
	@Autowired
	private CommonConfiguration configuration;

	@PostConstruct
	public void init() {
		if (configuration.getDev().isEnabled()) {
			if (personDao.findAll().size() == 0) {
				Person person = new Person();
				person.setUuid("54dfff62-b5ff-49d8-a1bd-e1e256043f5b");
				person.setAdmin(true);
				person.setSupporter(false);
				person.setCpr("2105791197");
				person.setEmail("bsg@digital-identity.dk");
				person.setName("Brian Storm Graversen");
				person.setNsisLevel(NSISLevel.LOW);
				person.setSamaccountName("bsg");
				person.setDomain("digitalidentity.dk");
	
				person = personDao.save(person);
				
				// add another Person
				
				person = new Person();
				person.setUuid("46889ca3-e686-4e77-b548-290343f178d0");
				person.setAdmin(true);
				person.setSupporter(false);
				person.setCpr("0701913477");
				person.setEmail("psu@digital-identity.dk");
				person.setName("Piotr Suski");
				person.setNsisLevel(NSISLevel.LOW);
	
				person.setDomain("digitalidentity.dk");
				person.setSamaccountName("psu");
	
				person = personDao.save(person);
				
				// add another Person
				
				person = new Person();
				person.setUuid("69afa825-2127-46aa-8eb6-3693b1092d1a");
				person.setAdmin(true);
				person.setSupporter(false);
				person.setCpr("1809960621");
				person.setName("Malthe Plenge Overgaard");
				person.setNsisLevel(NSISLevel.LOW);
				person.setSamaccountName("mpo");
	
				person = personDao.save(person);
				
				person = new Person();
				person.setUuid("fb35b7a0-0cd1-475c-9c50-071c3d21a8fd");
				person.setAdmin(true);
				person.setSupporter(false);
				person.setCpr("0310990868");
				person.setName("Amalie Flensburg Bojsen");
				person.setNsisLevel(NSISLevel.LOW);
				person.setSamaccountName("abo");
	
				person = personDao.save(person);
			}
		}
	}
}
