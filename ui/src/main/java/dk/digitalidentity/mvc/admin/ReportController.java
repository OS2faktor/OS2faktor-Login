package dk.digitalidentity.mvc.admin;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.LogAction.ReportType;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.mvc.admin.xlsview.AuditLogReportXlsView;
import dk.digitalidentity.mvc.admin.xlsview.PersonsReportXlsView;
import dk.digitalidentity.security.RequireAdministrator;
import dk.digitalidentity.security.RequireSupporter;
import dk.digitalidentity.security.SecurityUtil;
import dk.digitalidentity.service.ReportService;
import dk.digitalidentity.util.HttpServletResponseOutputStreamWrapper;
import dk.digitalidentity.util.OutputStreamWrapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequireSupporter
@Controller
public class ReportController {

	@Autowired
	private ReportService reportService;

	@Autowired
	private SecurityUtil securityUtil;

	@Autowired
	private PersonService personService;

	@Autowired
	private CommonConfiguration commonConfiguration;
	
	@GetMapping("/admin/rapporter")
	public String reports() {
		return "admin/reports";
	}

	@GetMapping("/ui/report/download/auditLog")
	public ModelAndView downloadReportAuditLog(HttpServletResponse response) {
		Map<String, Object> model;
		Person loggedInPerson = personService.getById(securityUtil.getPersonId());
		if (securityUtil.isAdmin() || (loggedInPerson.isSupporter() && loggedInPerson.getSupporter().getDomain() == null)) {
			model = reportService.getAuditLogReportModel();
		}
		else {
			model = reportService.getAuditLogReportModelByDomain(loggedInPerson.getSupporter().getDomain().getName());
		}

		response.setContentType("application/ms-excel");
		response.setHeader("Content-Disposition", "attachment; filename=\"Hændelseslog.xlsx\"");

		return new ModelAndView(new AuditLogReportXlsView(), model);
	}
	
	@GetMapping("/ui/report/download/persons")
	public ModelAndView downloadPersons(HttpServletResponse response) {
		Map<String, Object> model;
		Person loggedInPerson = personService.getById(securityUtil.getPersonId());
		if (securityUtil.isAdmin() || (loggedInPerson.isSupporter() && loggedInPerson.getSupporter().getDomain() == null)) {
			model = reportService.getPersonsReportModel();
		}
		else {
			model = reportService.getPersonsReportModelByDomain(loggedInPerson.getSupporter().getDomain());
		}

		model.put("enableRegistrantFeature", commonConfiguration.getCustomer().isEnableRegistrant());

		response.setContentType("application/ms-excel");
		response.setHeader("Content-Disposition", "attachment; filename=\"Brugerkonti.xlsx\"");

		return new ModelAndView(new PersonsReportXlsView(), model);
	}
	
	@RequireAdministrator
	@GetMapping("/ui/report/download/auditorReportLogins")
	public ResponseEntity<StreamingResponseBody> downloadAuditorReportLogins(HttpServletRequest request, HttpServletResponse response) {
		
		return ResponseEntity.ok().header("Content-Disposition", "attachment; filename=\"Revisorrapporter over logins.zip\"") 
			.body(out -> { 
				var zipOutputStream = new ZipOutputStream(out);
				
				addFilesToZip(request, zipOutputStream, 3, "Revisorrapport over logins ", ReportType.LOGIN_HISTORY);
				
				zipOutputStream.close(); 
			} 
		);
	}
	
	@RequireAdministrator
	@GetMapping("/ui/report/download/auditorReportGenerel")
	public ResponseEntity<StreamingResponseBody> downloadAuditorReportGeneral(HttpServletRequest request, HttpServletResponse response) {

		return ResponseEntity.ok().header("Content-Disposition", "attachment; filename=\"Revisorrapporter generelt.zip\"") 
			.body(out -> { 
				var zipOutputStream = new ZipOutputStream(out);
				
				addFilesToZip(request, zipOutputStream, 13, "Revisorrapport generelt ", ReportType.GENERAL_HISTORY);
				
				zipOutputStream.close(); 
			} 
		);
	}
	

	private void addFilesToZip(HttpServletRequest request, ZipOutputStream zipOutputStream, int month, String fileNamePrefix, ReportType type) {
		AuditLogReportXlsView view = new AuditLogReportXlsView();
		LocalDateTime now = LocalDateTime.now();
		
		while (month > 0) {
			LocalDateTime from = now.minusMonths(month);
			LocalDateTime to = now.minusMonths(month - 1);
			
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH.mm");
			String formattedFrom = from.format(formatter);
			String formattedTo = to.format(formatter);
			
			Map<String, Object> model = reportService.getAuditorReportModel(type, from, to);
			
			month = month - 1;

			try {
				boolean isEmpty = (boolean) model.get("auditLogListEmpty");
				if (!isEmpty) {
					HttpServletResponse response = new HttpServletResponseOutputStreamWrapper();
					view.render(model, request, response);
					OutputStreamWrapper outputStream = (OutputStreamWrapper) response.getOutputStream();
					
					ZipEntry taskfile = new ZipEntry(fileNamePrefix + formattedFrom + " til " + formattedTo +".xlsx"); 
					zipOutputStream.putNextEntry(taskfile); 
					zipOutputStream.write(outputStream.getByteArrayOutputStream().toByteArray());
				}
			}
			catch (Exception ex) {
				log.error("Failed to add file for auditLogs from " + formattedFrom + " to " + formattedTo + " to zip. Report type: " + type, ex);
			}
		}
	}
}
