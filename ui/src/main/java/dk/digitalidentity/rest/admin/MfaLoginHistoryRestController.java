package dk.digitalidentity.rest.admin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.datatables.mapping.DataTablesInput;
import org.springframework.data.jpa.datatables.mapping.DataTablesOutput;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.datatables.MfaLoginHistoryDatatablesDao;
import dk.digitalidentity.datatables.model.MfaLoginHistoryView;
import dk.digitalidentity.security.RequireAdministrator;
import jakarta.validation.Valid;

@RequireAdministrator
@RestController
public class MfaLoginHistoryRestController {
	
	@Autowired
	private MfaLoginHistoryDatatablesDao mfaLoginHistoryDatatablesDao;

	@PostMapping("/rest/admin/mfahistory")
	public DataTablesOutput<MfaLoginHistoryView> mfahistoryDataTable(@Valid @RequestBody DataTablesInput input, BindingResult bindingResult) {
		if (bindingResult.hasErrors()) {
			DataTablesOutput<MfaLoginHistoryView> error = new DataTablesOutput<>();
			error.setError(bindingResult.toString());

			return error;
		}
		
		return mfaLoginHistoryDatatablesDao.findAll(input);
	}
}
