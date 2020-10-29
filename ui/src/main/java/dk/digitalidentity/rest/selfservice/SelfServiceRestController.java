package dk.digitalidentity.rest.selfservice;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.datatables.mapping.DataTablesInput;
import org.springframework.data.jpa.datatables.mapping.DataTablesOutput;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.datatables.AuditLogDatatableDao;
import dk.digitalidentity.datatables.model.AuditLogView;
import dk.digitalidentity.security.SecurityUtil;

@RestController
public class SelfServiceRestController {

	@Autowired
	private AuditLogDatatableDao auditLogDatatableDao;

	@Autowired
	private SecurityUtil securityUtil;
	
	@PostMapping("/rest/selvbetjening/eventlog")
	public DataTablesOutput<AuditLogView> selfserviceEventLogsDataTable(@Valid @RequestBody DataTablesInput input, BindingResult bindingResult) {
		if (bindingResult.hasErrors()) {
			DataTablesOutput<AuditLogView> error = new DataTablesOutput<>();
			error.setError(bindingResult.toString());

			return error;
		}



		return auditLogDatatableDao.findAll(input, null, getAdditionalSpecification(securityUtil.getPersonId()));
	}
	
	private Specification<AuditLogView> getAdditionalSpecification(long value) {
		return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("personId"), value);
	}
}
