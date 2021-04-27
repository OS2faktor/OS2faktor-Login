package dk.digitalidentity.rest.registration;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.datatables.mapping.DataTablesInput;
import org.springframework.data.jpa.datatables.mapping.DataTablesOutput;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.datatables.PersonRegistrationDatatableDao;
import dk.digitalidentity.datatables.model.RegistrationPersonView;
import dk.digitalidentity.security.RequireRegistrant;

@RequireRegistrant
@RestController
public class RegistrationRestController {

	@Autowired
	private PersonRegistrationDatatableDao personRegistrationDatatableDao;

	@PostMapping("/rest/registration/persons")
	public DataTablesOutput<RegistrationPersonView> registerDataTable(@Valid @RequestBody DataTablesInput input, BindingResult bindingResult) {
		if (bindingResult.hasErrors()) {
			DataTablesOutput<RegistrationPersonView> error = new DataTablesOutput<>();
			error.setError(bindingResult.toString());

			return error;
		}

		return personRegistrationDatatableDao.findAll(input);
	}
}