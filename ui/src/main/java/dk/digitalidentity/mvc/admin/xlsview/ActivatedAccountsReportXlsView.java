package dk.digitalidentity.mvc.admin.xlsview;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.view.document.AbstractXlsxStreamingView;

import dk.digitalidentity.common.dao.model.AuditLog;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.service.PersonService;

public class ActivatedAccountsReportXlsView extends AbstractXlsxStreamingView {
	private List<AuditLog> auditLogs;
	private CellStyle headerStyle;
	private CellStyle wrapStyle;
	private ResourceBundleMessageSource resourceBundle;

	@SuppressWarnings("unchecked")
	@Override
	protected void buildExcelDocument(Map<String, Object> model, Workbook workbook, HttpServletRequest request, HttpServletResponse response) throws Exception {

		// Get data
		auditLogs = (List<AuditLog>) model.get("auditLogs");
		resourceBundle = (ResourceBundleMessageSource) model.get("resourceBundle");

		// Setup shared resources
		Font headerFont = workbook.createFont();
		headerFont.setBold(true);

		headerStyle = workbook.createCellStyle();
		headerStyle.setFont(headerFont);

		wrapStyle = workbook.createCellStyle();
		wrapStyle.setWrapText(true);

		// Create Sheets
		createAuditLogSheet(workbook);
	}
	
	private void createAuditLogSheet(Workbook workbook) {
		Sheet sheet = workbook.createSheet("Ansøgere");

		ArrayList<String> headers = new ArrayList<>();
		headers.add("Tidspunkt");
		headers.add("IP-adresse");
		headers.add("Handling");
		headers.add("Besked");
		headers.add("Detaljer");
		headers.add("Personnummer");
		headers.add("Navn");
		headers.add("Bruger-ID");
		headers.add("AD konto");
		headers.add("Administrator");

		createHeaderRow(sheet, headers);

		int row = 1;
		for (AuditLog entry : auditLogs) {

			Row dataRow = sheet.createRow(row++);
			int column = 0;

			Person person = entry.getPerson();
			
			createCell(dataRow, column++, entry.getTts().toString(), null);
			createCell(dataRow, column++, entry.getIpAddress(), null);
			createCell(dataRow, column++, resourceBundle.getMessage(entry.getLogAction().getMessage(), null, Locale.ENGLISH), null);
			createCell(dataRow, column++, entry.getMessage(), null);
			createCell(dataRow, column++, (entry.getDetails() != null) ? entry.getDetails().getDetailContent() : "", null);
			createCell(dataRow, column++, entry.getCpr(), null); // OK, can only be retrieved by ADMIN
			createCell(dataRow, column++, entry.getPersonName(), null);
			createCell(dataRow, column++, (person != null ? PersonService.getUsername(person) : ""), null);
			createCell(dataRow, column++, (person != null ? person.getSamaccountName() : ""), null);
			createCell(dataRow, column++, entry.getPerformerName(), null);
		}
	}

	private void createHeaderRow(Sheet sheet, List<String> headers) {
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
