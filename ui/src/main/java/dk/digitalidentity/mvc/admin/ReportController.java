package dk.digitalidentity.mvc.admin;

import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.LogAction;
import dk.digitalidentity.common.dao.model.enums.LogAction.ReportType;
import dk.digitalidentity.common.service.AuditLogService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.dto.AuditLogDTO;
import dk.digitalidentity.mvc.admin.xlsview.AdminActionReportXlsView;
import dk.digitalidentity.mvc.admin.xlsview.AuditLogReportXlsView;
import dk.digitalidentity.mvc.admin.xlsview.PersonsReportXlsView;
import dk.digitalidentity.mvc.admin.xlsview.RolesReportXlsView;
import dk.digitalidentity.security.RequireAdministrator;
import dk.digitalidentity.security.RequireSupporter;
import dk.digitalidentity.security.SecurityUtil;
import dk.digitalidentity.service.ReportService;
import dk.digitalidentity.util.HttpServletResponseOutputStreamWrapper;
import dk.digitalidentity.util.OutputStreamWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

	@Autowired
	private AuditLogService auditLogService;
	
	@Autowired
	private ResourceBundleMessageSource resourceBundle;
	
	@GetMapping("/admin/rapporter")
	public String reports() {
		return "admin/reports";
	}

	/* TODO: Missing this logick
		Map<String, Object> model;
		Person loggedInPerson = personService.getById(securityUtil.getPersonId());
		if (securityUtil.isAdmin() || (loggedInPerson.isSupporter() && loggedInPerson.getSupporter().getDomain() == null)) {
			model = reportService.getAuditLogReportModel();
		}
		else {
			model = reportService.getAuditLogReportModelByDomain(loggedInPerson.getSupporter().getDomain().getName());
		}

	 */
	@GetMapping("/ui/report/download/auditLog")
	public ResponseEntity<StreamingResponseBody> downloadReportAuditLog(HttpServletRequest request, HttpServletResponse response) {
		log.info("Starting download of login report");

		return ResponseEntity.ok().header("Content-Disposition", "attachment; filename=\"Ugerrapport.zip\"") 
			.body(out -> { 
				var zipOutputStream = new ZipOutputStream(out);
				
				addFilesToZipCsv(request, zipOutputStream, PeriodType.DAYS, 7, null, ReportType.ALL);
				
				zipOutputStream.close(); 
			}
		);
	}

	@GetMapping("/ui/report/download/auditLog/{date}")
	public ResponseEntity<StreamingResponseBody> downloadReportAuditLogForDate(HttpServletRequest request, HttpServletResponse response, @PathVariable LocalDate date) {
		log.info("Starting download of login report for " + date);

		return ResponseEntity.ok().header("Content-Disposition", "attachment; filename=\"Dagsrapport.zip\"")
			.body(out -> {
				var zipOutputStream = new ZipOutputStream(out);

				addFilesToZipCsvSpecificDate(request, zipOutputStream, date, null, ReportType.ALL);

				zipOutputStream.close();
			}
		);
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
	@GetMapping("/ui/report/download/auditorReportAdmins")
	public ModelAndView downloadAdminReportLogins(HttpServletRequest request, HttpServletResponse response) {
		Map<String, Object> model = reportService.getAuditLogReportModelWithAdminActions();

		response.setContentType("application/ms-excel");
		response.setHeader("Content-Disposition", "attachment; filename=\"Revisorrapport over administratorhandlinger.xlsx\"");
		
		return new ModelAndView(new AdminActionReportXlsView(), model);
	}
	
	@RequireAdministrator
	@GetMapping("/ui/report/download/auditorReportLogins")
	public ResponseEntity<StreamingResponseBody> downloadAuditorReportLogins(HttpServletRequest request, HttpServletResponse response) {		
		log.info("Starting download of login report");

		return ResponseEntity.ok().header("Content-Disposition", "attachment; filename=\"Revisorrapporter over logins.zip\"") 
			.body(out -> { 
				var zipOutputStream = new ZipOutputStream(out);
				
				addFilesToZipCsv(request, zipOutputStream, PeriodType.MONTHS, 3, "Revisorrapport over logins ", ReportType.LOGIN_HISTORY);
				
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

	@RequireAdministrator
	@GetMapping("/ui/report/download/auditorReportSelection")
	public ResponseEntity<StreamingResponseBody> downloadReportSelection(HttpServletRequest request, HttpServletResponse response, @RequestParam(name = "logAction", required = false) LogAction logActionFilter, @RequestParam(name = "message", required = false) String messageFilter) {

		return ResponseEntity.ok().header("Content-Disposition", "attachment; filename=\"Hændelseslog.zip\"") 
				.body(out -> { 
					var zipOutputStream = new ZipOutputStream(out);
					
					addFilesToZipCsv(request, zipOutputStream, 3, "Hændelseslog ", logActionFilter, messageFilter);
					
					zipOutputStream.close(); 
				}
		);
	}

	@GetMapping("/ui/report/download/roles")
	public ModelAndView downloadRoles(HttpServletResponse response) {
		Map<String, Object> model;
		Person loggedInPerson = personService.getById(securityUtil.getPersonId());
		if (securityUtil.isAdmin() || (loggedInPerson.isSupporter() && loggedInPerson.getSupporter().getDomain() == null)) {
			model = reportService.getRolesReportModel();
		}
		else {
			model = reportService.getRolesReportModelByDomain(loggedInPerson.getSupporter().getDomain());
		}

		response.setContentType("application/ms-excel");
		response.setHeader("Content-Disposition", "attachment; filename=\"Jobfunktionsroller.xlsx\"");

		return new ModelAndView(new RolesReportXlsView(), model);
	}

	private enum PeriodType { MONTHS, DAYS };
	private void addFilesToZipCsv(HttpServletRequest request, ZipOutputStream zipOutputStream, PeriodType periodType, int periodCount, String fileNamePrefix, ReportType type) {
		LocalDateTime now = LocalDateTime.now();
		
		int month = 0;
		if (periodType == PeriodType.MONTHS) {
			month = periodCount;
		}

		while (month >= 0) {

			// 20XX-YY-01 00:00:00
			LocalDateTime from = (periodType == PeriodType.MONTHS)
					? now
						.minusMonths(month)
						.withDayOfMonth(1)
						.withHour(0)
						.withMinute(0)
						.withSecond(0)
					: now
						.minusDays(periodCount)
						.withHour(0)
						.withMinute(0)
						.withSecond(0);

			// 20XX-YY-ZZ 23:59:59
			LocalDateTime to = now;
			if (periodType == PeriodType.MONTHS) {
				to = now
						.minusMonths(month)
						.withHour(23)
						.withMinute(59)
						.withSecond(59);

				to = to.withDayOfMonth(to.getMonth().length(to.toLocalDate().isLeapYear()));
			}

			month = month - 1;

			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
			String formattedFrom = from.format(formatter);
			String formattedTo = to.format(formatter);

			try {
				log.info(type.toString()  + " : Starting rendering logs from " + formattedFrom +  " to " + formattedTo);
				
				int count = (type != ReportType.ALL) ? auditLogService.countAuditLogsByMonth(from, to, type) : auditLogService.countAuditLogsByMonth(from, to);
				log.info(type.toString() + " : found " + count + " rows");

				boolean isEmpty = (count == 0);
				if (!isEmpty) {
					String fileName = "rapport.csv";
					if (periodType == PeriodType.MONTHS) {
						String monthStr = getMonthString(from.getMonth());
						
						fileName = fileNamePrefix + from.getYear() + " " + monthStr + ".csv";
					}

					ZipEntry taskfile = new ZipEntry(fileName);
					zipOutputStream.putNextEntry(taskfile); 

					StringBuilder builder = new StringBuilder();

					builder.append("\"").append("Tidspunkt").append("\";");
					builder.append("\"").append("IP-adresse").append("\";");
					builder.append("\"").append("Korrelations-ID").append("\";");
					builder.append("\"").append("Handling").append("\";");
					builder.append("\"").append("Besked").append("\";");
					builder.append("\"").append("Personnummer").append("\";");
					builder.append("\"").append("Person").append("\";");
					builder.append("\"").append("Brugernavn").append("\";");
					builder.append("\"").append("Administrator").append("\"\n");

					zipOutputStream.write(builder.toString().getBytes(Charset.forName("ISO-8859-1")));

					Pageable page = PageRequest.of(0, 20000);

					do {
						List<AuditLogDTO> auditLogs = (type != ReportType.ALL) ? auditLogService.findAllJDBC(page, from, to, type) : auditLogService.findAllJDBC(page, from, to);
						if (auditLogs == null || auditLogs.size() == 0) {
							break;
						}
						
						log.info(type.toString() + " : processing " + auditLogs.size() + " database rows");

						for (AuditLogDTO entry : auditLogs) {
							builder = new StringBuilder();

							builder.append("\"").append(entry.getTts()).append("\";");
							builder.append("\"").append(entry.getIpAddress()).append("\";");
							builder.append("\"").append(entry.getCorrelationId()).append("\";");
							builder.append("\"").append(resourceBundle.getMessage(entry.getLogAction().getMessage(), null, Locale.ENGLISH)).append("\";");
							builder.append("\"").append(entry.getMessage()).append("\";");
							builder.append("\"").append(!StringUtils.hasLength(entry.getCpr()) ? "" : entry.getCpr().substring(0, 6) + "-XXXX").append("\";");
							builder.append("\"").append((entry.getPersonName() != null) ? entry.getPersonName() : "").append("\";");
							builder.append("\"").append((entry.getPersonUserId() != null) ? entry.getPersonUserId() : "").append("\";");
							builder.append("\"").append((entry.getPerformerName() != null) ? entry.getPerformerName() : "").append("\"\n");

							zipOutputStream.write(builder.toString().getBytes(Charset.forName("ISO-8859-1")));
						}
						
						// next page
						page = page.next();
					} while (true);					
				}
				
				log.info(type.toString() + " : Done rendering logs from " + formattedFrom +  " to " + formattedTo);
			}
			catch (Exception ex) {
				log.error(type.toString() + " : Failed to add file for auditLogs from " + formattedFrom + " to " + formattedTo + " to zip", ex);
				return;
			}
		}

		log.info(type.toString() + " : Done rendering logs for download");
	}

	private void addFilesToZipCsv(HttpServletRequest request, ZipOutputStream zipOutputStream, int month, String fileNamePrefix, LogAction logAction, String messageFilter) {
		LocalDateTime now = LocalDateTime.now();
		
		while (month >= 0) {

			// 20XX-YY-01 00:00:00
			LocalDateTime from = now
					.minusMonths(month)
					.withDayOfMonth(1)
					.withHour(0)
					.withMinute(0)
					.withSecond(0);

			// 20XX-YY-ZZ 23:59:59
			LocalDateTime to = now
					.minusMonths(month)
					.withHour(23)
					.withMinute(59)
					.withSecond(59);
			to = to.withDayOfMonth(to.getMonth().length(to.toLocalDate().isLeapYear()));

			String monthStr = getMonthString(from.getMonth());

			month = month - 1;

			try {
				log.info("Starting rendering logs from " + from.toString() +  " to " + to.toString());
				
				boolean isEmpty = auditLogService.countAuditLogsByMonth(from, to, logAction, messageFilter) == 0;
				if (!isEmpty) {
					ZipEntry taskfile = new ZipEntry(fileNamePrefix + from.getYear() + " " + monthStr + ".csv");
					zipOutputStream.putNextEntry(taskfile); 

					StringBuilder builder = new StringBuilder();

					builder.append("\"").append("Tidspunkt").append("\";");
					builder.append("\"").append("IP-adresse").append("\";");
					builder.append("\"").append("Korrelations-ID").append("\";");
					builder.append("\"").append("Handling").append("\";");
					builder.append("\"").append("Besked").append("\";");
					builder.append("\"").append("Personnummer").append("\";");
					builder.append("\"").append("Person").append("\";");
					builder.append("\"").append("Brugernavn").append("\";");
					builder.append("\"").append("Administrator").append("\"\n");

					zipOutputStream.write(builder.toString().getBytes(Charset.forName("ISO-8859-1")));

					Pageable page = PageRequest.of(0, 20000);

					do {
						List<AuditLogDTO> auditLogs = auditLogService.findAllJDBC(page, from, to, logAction, messageFilter);
						if (auditLogs == null || auditLogs.size() == 0) {
							break;
						}

						for (AuditLogDTO entry : auditLogs) {
							builder = new StringBuilder();

							builder.append("\"").append(entry.getTts()).append("\";");
							builder.append("\"").append(entry.getIpAddress()).append("\";");
							builder.append("\"").append(entry.getCorrelationId()).append("\";");
							builder.append("\"").append(resourceBundle.getMessage(entry.getLogAction().getMessage(), null, Locale.ENGLISH)).append("\";");
							builder.append("\"").append(entry.getMessage()).append("\";");
							builder.append("\"").append(!StringUtils.hasLength(entry.getCpr()) ? "" : entry.getCpr().substring(0, 6) + "-XXXX").append("\";");
							builder.append("\"").append((entry.getPersonName() != null) ? entry.getPersonName() : "").append("\";");
							builder.append("\"").append((entry.getPersonUserId() != null) ? entry.getPersonUserId() : "").append("\";");
							builder.append("\"").append((entry.getPerformerName() != null) ? entry.getPerformerName() : "").append("\"\n");

							zipOutputStream.write(builder.toString().getBytes(Charset.forName("ISO-8859-1")));
						}
						
						// next page
						page = page.next();
					} while (true);
				}
				
				log.info("Done rendering logs from " + from.toString() +  " to " + to.toString());
			}
			catch (Exception ex) {
				DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm");
				String formattedFrom = from.format(formatter);
				String formattedTo = to.format(formatter);
				log.error("Failed to add file for auditLogs from " + formattedFrom + " to " + formattedTo + " to zip. For filter logAction: " + logAction.toString() + " message: " + messageFilter, ex);
			}
		}
		
		log.info("Done rendering logs for download");
	}

	private void addFilesToZipCsvSpecificDate(HttpServletRequest request, ZipOutputStream zipOutputStream, LocalDate date, String fileNamePrefix, ReportType type) {
		// 20XX-YY-01 00:00:00 to 20XX-YY-01 23:59:99.999....
		LocalDateTime from = date.atStartOfDay();
		LocalDateTime to = date.atTime(LocalTime.MAX);

		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
		String formattedFrom = from.format(formatter);
		String formattedTo = to.format(formatter);
		try {
			log.info(type.toString()  + " : Starting rendering logs from " + formattedFrom);
			int count = auditLogService.countAuditLogsByMonth(from, to);
			log.info(type.toString() + " : found " + count + " rows");

			boolean isEmpty = (count == 0);
			if (!isEmpty) {
				String fileName = "rapport.csv";

				ZipEntry taskfile = new ZipEntry(fileName);
				zipOutputStream.putNextEntry(taskfile);

				StringBuilder builder = new StringBuilder();

				builder.append("\"").append("Tidspunkt").append("\";");
				builder.append("\"").append("IP-adresse").append("\";");
				builder.append("\"").append("Korrelations-ID").append("\";");
				builder.append("\"").append("Handling").append("\";");
				builder.append("\"").append("Besked").append("\";");
				builder.append("\"").append("Personnummer").append("\";");
				builder.append("\"").append("Person").append("\";");
				builder.append("\"").append("Brugernavn").append("\";");
				builder.append("\"").append("Administrator").append("\"\n");

				zipOutputStream.write(builder.toString().getBytes(Charset.forName("ISO-8859-1")));

				Pageable page = PageRequest.of(0, 20000);

				do {
					List<AuditLogDTO> auditLogs = (type != ReportType.ALL) ? auditLogService.findAllJDBC(page, from, to, type) : auditLogService.findAllJDBC(page, from, to);
					if (auditLogs == null || auditLogs.size() == 0) {
						break;
					}

					log.info(type.toString() + " : processing " + auditLogs.size() + " database rows");

					for (AuditLogDTO entry : auditLogs) {
						builder = new StringBuilder();

						builder.append("\"").append(entry.getTts()).append("\";");
						builder.append("\"").append(entry.getIpAddress()).append("\";");
						builder.append("\"").append(entry.getCorrelationId()).append("\";");
						builder.append("\"").append(resourceBundle.getMessage(entry.getLogAction().getMessage(), null, Locale.ENGLISH)).append("\";");
						builder.append("\"").append(entry.getMessage()).append("\";");
						builder.append("\"").append(!StringUtils.hasLength(entry.getCpr()) ? "" : entry.getCpr().substring(0, 6) + "-XXXX").append("\";");
						builder.append("\"").append((entry.getPersonName() != null) ? entry.getPersonName() : "").append("\";");
						builder.append("\"").append((entry.getPersonUserId() != null) ? entry.getPersonUserId() : "").append("\";");
						builder.append("\"").append((entry.getPerformerName() != null) ? entry.getPerformerName() : "").append("\"\n");

						zipOutputStream.write(builder.toString().getBytes(Charset.forName("ISO-8859-1")));
					}

					// next page
					page = page.next();
				} while (true);
			}
			log.info(type.toString() + " : Done rendering logs from " + formattedFrom +  " to " + formattedTo);
		}
		catch (Exception ex) {
			log.error(type.toString() + " : Failed to add file for auditLogs from " + formattedFrom + " to " + formattedTo + " to zip", ex);
			return;
		}
		log.info(type.toString() + " : Done rendering logs for download");
	}

	private void addFilesToZip(HttpServletRequest request, ZipOutputStream zipOutputStream, int month, String fileNamePrefix, ReportType type) {
		AuditLogReportXlsView view = new AuditLogReportXlsView();
		LocalDateTime now = LocalDateTime.now();
		
		while (month >= 0) {

			// 20XX-YY-01 00:00:00
			LocalDateTime from = now
					.minusMonths(month)
					.withDayOfMonth(1)
					.withHour(0)
					.withMinute(0)
					.withSecond(0);

			// 20XX-YY-ZZ 23:59:59
			LocalDateTime to = now
					.minusMonths(month)
					.withHour(23)
					.withMinute(59)
					.withSecond(59);
			to = to.withDayOfMonth(to.getMonth().length(to.toLocalDate().isLeapYear()));

			String monthStr = getMonthString(from.getMonth());

			Map<String, Object> model = reportService.getAuditorReportModel(type, from, to);
			
			month = month - 1;

			try {
				log.info("Starting rendering logs from " + from.toString() +  " to " + to.toString());
				
				boolean isEmpty = auditLogService.countAuditLogsByMonth(from, to, type) == 0;
				if (!isEmpty) {
					HttpServletResponse response = new HttpServletResponseOutputStreamWrapper();
					view.render(model, request, response);
					OutputStreamWrapper outputStream = (OutputStreamWrapper) response.getOutputStream();

					ZipEntry taskfile = new ZipEntry(fileNamePrefix + from.getYear()+ " " + monthStr + ".xlsx");
					zipOutputStream.putNextEntry(taskfile); 
					outputStream.getByteArrayOutputStream().writeTo(zipOutputStream);
				}
				
				log.info("Done rendering logs from " + from.toString() +  " to " + to.toString());
			}
			catch (Exception ex) {
				DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm");
				String formattedFrom = from.format(formatter);
				String formattedTo = to.format(formatter);
				log.error("Failed to add file for auditLogs from " + formattedFrom + " to " + formattedTo + " to zip. Report type: " + type, ex);
			}			
		}
		
		log.info("Done rendering logs for download");
	}

	private String getMonthString(Month month) {
		switch (month) {
			case JANUARY:
				return "Januar";
			case FEBRUARY:
				return "Februar";
			case MARCH:
				return "Marts";
			case APRIL:
				return "April";
			case MAY:
				return "Maj";
			case JUNE:
				return "Juni";
			case JULY:
				return "Juli";
			case AUGUST:
				return "August";
			case SEPTEMBER:
				return "September";
			case OCTOBER:
				return "Oktober";
			case NOVEMBER:
				return "November";
			case DECEMBER:
				return "December";
			default:
				throw new IllegalStateException("Unexpected value: " + month);
		}

	}
}
