package dk.digitalidentity.rest.admin;

import java.util.Set;
import java.util.stream.Collectors;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.datatables.mapping.DataTablesInput;
import org.springframework.data.jpa.datatables.mapping.DataTablesOutput;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.datatables.PersonWherePasswordChangeAllowedDao;
import dk.digitalidentity.datatables.model.PersonWherePasswordChangeAllowedView;
import dk.digitalidentity.security.RequireChangePasswordOnOthersRole;
import dk.digitalidentity.security.SecurityUtil;

@RestController
@RequireChangePasswordOnOthersRole
public class ChangePasswordOnOthersRestController {

    @Autowired
    private SecurityUtil securityUtil;

    @Autowired
    private PersonWherePasswordChangeAllowedDao personWherePasswordChangeAllowedDao;
    
    @PostMapping("/rest/change-password/persons")
	public DataTablesOutput<PersonWherePasswordChangeAllowedView> changePasswordPersonsDataTable(@Valid @RequestBody DataTablesInput input, BindingResult bindingResult) {
		if (bindingResult.hasErrors()) {
			DataTablesOutput<PersonWherePasswordChangeAllowedView> error = new DataTablesOutput<>();
			error.setError(bindingResult.toString());

			return error;
		}

		Person person = securityUtil.getPerson();
		if (person == null) {
			DataTablesOutput<PersonWherePasswordChangeAllowedView> error = new DataTablesOutput<>();
			error.setError("Ukendt administrator!");

			return error;			
		}

		return personWherePasswordChangeAllowedDao.findAll(input, null, getAdditionalSpecification(person));
	}

	private Specification<PersonWherePasswordChangeAllowedView> getAdditionalSpecification(Person person) {
		final Set<Long> personGroups = person.getGroups().stream().map(pgm -> pgm.getGroup().getId()).collect(Collectors.toSet());

		return (root, query, criteriaBuilder) -> criteriaBuilder
				.createQuery(PersonWherePasswordChangeAllowedView.class)
				.select(root)
				.where(root.get("requiredGroupId").in(personGroups))
				.getRestriction();
	}
}
