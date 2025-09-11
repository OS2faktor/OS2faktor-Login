package dk.digitalidentity.interceptors;

import java.util.List;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.dao.model.Person;

@Aspect
@Component
public class BeforeServiceSaveInterceptor {

	@Autowired
	private AbstractBeforeSaveInterceptor interceptor;
	
	@Before("execution(* dk.digitalidentity.common.service.PersonService.save(dk.digitalidentity.common.dao.model.Person)) && args(person)")
	public void beforeSavePerson(Person person) {
		interceptor.handleSavePerson(person);
	}
	
	@Before("execution(* dk.digitalidentity.common.service.PersonService.saveAll(java.util.List)) && args(persons)")
	public void beforeSavePersons(List<Person> persons) {
		for (Person person : persons) {
			interceptor.handleSavePerson(person);
		}
	}
}
