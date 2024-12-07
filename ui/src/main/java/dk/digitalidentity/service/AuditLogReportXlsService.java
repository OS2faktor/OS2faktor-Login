package dk.digitalidentity.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import dk.digitalidentity.common.dao.model.AuditLog;
import dk.digitalidentity.common.dao.model.enums.LogAction.ReportType;
import dk.digitalidentity.common.service.AuditLogService;
import dk.digitalidentity.common.service.dto.AuditLogDTO;
import jakarta.transaction.Transactional;

@Service
public class AuditLogReportXlsService {
	
	@Autowired
	private AuditLogService auditLogService;

	@Autowired
	private ResourceBundleMessageSource resourceBundle;

	@Transactional
	public void createAuditLogSheet(Workbook workbook, LocalDateTime from, LocalDateTime to, ReportType type, CellStyle headerStyle) throws InterruptedException {
		Sheet sheet = workbook.createSheet("Auditlog");

		ArrayList<String> headers = new ArrayList<>();
		headers.add("Tidspunkt");
		headers.add("IP-adresse");
		headers.add("Korrelations-ID");
		headers.add("Handling");
		headers.add("Besked");
		headers.add("Personnummer");
		headers.add("Person");
		headers.add("Administrator");

		createHeaderRow(sheet, headers, headerStyle);
		
		int row = 1;
		Pageable page = PageRequest.of(0, 20000);

		do {
			List<AuditLogDTO> auditLogs = auditLogService.findAllJDBC(page, from, to, type);
			if (auditLogs == null || auditLogs.size() == 0) {
				break;
			}

			for (AuditLogDTO entry : auditLogs) {
				Row dataRow = sheet.createRow(row++);
				int column = 0;

				createCell(dataRow, column++, entry.getTts().toString(), null);
				createCell(dataRow, column++, entry.getIpAddress(), null);
				createCell(dataRow, column++, entry.getCorrelationId(), null);
				createCell(dataRow, column++, resourceBundle.getMessage(entry.getLogAction().getMessage(), null, Locale.ENGLISH), null);
				createCell(dataRow, column++, entry.getMessage(), null);
				createCell(dataRow, column++, !StringUtils.hasLength(entry.getCpr()) ? "" : entry.getCpr().substring(0, 6) + "-XXXX", null);
				createCell(dataRow, column++, entry.getPersonName(), null);
				createCell(dataRow, column++, entry.getPerformerName(), null);
			}
			
			// next page
			page = page.next();
		} while (true);
	}

	public void createAuditLogSheetStatic(Workbook workbook, List<AuditLog> auditlogs, CellStyle headerStyle) throws InterruptedException {
		Sheet sheet = workbook.createSheet("Auditlog");

		ArrayList<String> headers = new ArrayList<>();
		headers.add("Tidspunkt");
		headers.add("IP-adresse");
		headers.add("Korrelations-ID");
		headers.add("Handling");
		headers.add("Besked");
		headers.add("Personnummer");
		headers.add("Person");
		headers.add("Brugernavn");
		headers.add("Administrator");

		createHeaderRow(sheet, headers, headerStyle);
		
		int row = 1;

		for (AuditLog entry : auditlogs) {
			Row dataRow = sheet.createRow(row++);
			int column = 0;

			createCell(dataRow, column++, entry.getTts().toString(), null);
			createCell(dataRow, column++, entry.getIpAddress(), null);
			createCell(dataRow, column++, entry.getCorrelationId(), null);
			createCell(dataRow, column++, resourceBundle.getMessage(entry.getLogAction().getMessage(), null, Locale.ENGLISH), null);
			createCell(dataRow, column++, entry.getMessage(), null);
			createCell(dataRow, column++, !StringUtils.hasLength(entry.getCpr()) ? "" : entry.getCpr().substring(0, 6) + "-XXXX", null);
			createCell(dataRow, column++, entry.getPersonName(), null);
			createCell(dataRow, column++, entry.getPerson() != null ? entry.getPerson().getSamaccountName() : "", null);
			createCell(dataRow, column++, entry.getPerformerName(), null);
		}
	}
	
	private void createHeaderRow(Sheet sheet, List<String> headers, CellStyle headerStyle) {
		Row headerRow = sheet.createRow(0);

		int column = 0;
		for (String header : headers) {
			createCell(headerRow, column++, header, headerStyle);
		}
	}

	private static void createCell(Row header, int column, String value, CellStyle style) {
		Cell cell = header.createCell(column);
		cell.setCellValue(value);

		if (style != null) {
			cell.setCellStyle(style);
		}
	}
}
