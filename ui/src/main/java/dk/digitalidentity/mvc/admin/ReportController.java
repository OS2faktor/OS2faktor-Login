package dk.digitalidentity.mvc.admin;

import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.security.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;

import dk.digitalidentity.mvc.admin.xlsview.AuditLogReportXlsView;
import dk.digitalidentity.mvc.admin.xlsview.PersonsReportXlsView;
import dk.digitalidentity.security.RequireSupporter;
import dk.digitalidentity.service.ReportService;

@RequireSupporter
@Controller
public class ReportController {

	@Autowired
	private ReportService reportService;

	@Autowired
	private SecurityUtil securityUtil;

	@Autowired
	private PersonService personService;

	@GetMapping("/admin/rapporter")
	public String reports() {
		return "admin/reports";
	}

	@GetMapping("/ui/report/download/auditLog")
	public ModelAndView downloadReportAuditLog(HttpServletResponse response) {
		Map<String, Object> model;
		if (securityUtil.isAdmin()) {
			model = reportService.getAuditLogReportModel();
		}
		else {
			Person loggedInPerson = personService.getById(securityUtil.getPersonId());
			model = reportService.getAuditLogReportModelByDomain(loggedInPerson.getDomain().getName());
		}

		response.setContentType("application/ms-excel");
		response.setHeader("Content-Disposition", "attachment; filename=\"Hændelseslog.xls\"");

		return new ModelAndView(new AuditLogReportXlsView(), model);
	}

	@GetMapping("/ui/report/download/persons")
	public ModelAndView downloadPersons(HttpServletResponse response) {
		Map<String, Object> model;
		if (securityUtil.isAdmin()) {
			model = reportService.getPersonsReportModel();
		}
		else {
			Person loggedInPerson = personService.getById(securityUtil.getPersonId());
			model = reportService.getPersonsReportModelByDomain(loggedInPerson.getDomain().getName());
		}

		response.setContentType("application/ms-excel");
		response.setHeader("Content-Disposition", "attachment; filename=\"Erhvervsidentiteter.xls\"");

		return new ModelAndView(new PersonsReportXlsView(), model);
	}
}
