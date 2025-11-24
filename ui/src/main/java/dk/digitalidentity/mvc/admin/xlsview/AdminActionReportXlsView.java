package dk.digitalidentity.mvc.admin.xlsview;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.View;

import dk.digitalidentity.common.dao.model.AuditLog;
import dk.digitalidentity.common.dao.model.enums.LogAction;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class AdminActionReportXlsView implements View {
	private static final String CONTENT_TYPE = "application/ms-excel";
	
	private List<AuditLog> auditLogs;
	private CellStyle headerStyle;
	private CellStyle wrapStyle;
	private ResourceBundleMessageSource resourceBundle;

	@Override
	public String getContentType() {
		return CONTENT_TYPE;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void render(Map<String, ?> model, HttpServletRequest request, HttpServletResponse response) throws Exception {
		auditLogs = (List<AuditLog>) model.get("auditLogs");
		resourceBundle = (ResourceBundleMessageSource) model.get("resourceBundle");

		response.setContentType(getContentType());
		response.setHeader("Content-Disposition", "attachment; filename=\"Revisorrapport over administratorhandlinger.xlsx\"");

		try (Workbook workbook = new DisposableSXSSFWorkbook()) {
			Font headerFont = workbook.createFont();
			headerFont.setBold(true);

			headerStyle = workbook.createCellStyle();
			headerStyle.setFont(headerFont);

			wrapStyle = workbook.createCellStyle();
			wrapStyle.setWrapText(true);

			createLockSheet(workbook);
			createSettingsSheet(workbook);

			workbook.write(response.getOutputStream());
		}
	}

	private void createLockSheet(Workbook workbook) {
		Sheet sheet = workbook.createSheet("Spærrehændelser");

		ArrayList<String> headers = new ArrayList<>();
		headers.add("Tidspunkt");
		headers.add("IP-adresse");
		headers.add("Handling");
		headers.add("Besked");
		headers.add("Personnummer");
		headers.add("Person");
		headers.add("Administrator");
		headers.add("Detaljer");

		createHeaderRow(sheet, headers);

		int row = 1;
		for (AuditLog entry : auditLogs.stream()
				.filter(a -> Objects.equals(a.getLogAction(), LogAction.DEACTIVATE_BY_ADMIN) || Objects.equals(a.getLogAction(), LogAction.REACTIVATE_BY_ADMIN))
				.collect(Collectors.toList())) {

			Row dataRow = sheet.createRow(row++);
			int column = 0;

			createCell(dataRow, column++, entry.getTts().toString(), null);
			createCell(dataRow, column++, entry.getIpAddress(), null);
			createCell(dataRow, column++, resourceBundle.getMessage(entry.getLogAction().getMessage(), null, Locale.ENGLISH), null);
			createCell(dataRow, column++, entry.getMessage(), null);
			createCell(dataRow, column++, !StringUtils.hasLength(entry.getCpr()) ? "" : entry.getCpr().substring(0, 6) + "-XXXX", null);
			createCell(dataRow, column++, entry.getPersonName(), null);
			createCell(dataRow, column++, entry.getPerformerName(), null);
			createCell(dataRow, column++, (entry.getDetails() != null) ? entry.getDetails().getDetailContent() : "", null);
		}
	}
	
	private void createSettingsSheet(Workbook workbook) {
		Sheet sheet = workbook.createSheet("Dataændringer");

		ArrayList<String> headers = new ArrayList<>();
		headers.add("Tidspunkt");
		headers.add("IP-adresse");
		headers.add("Handling");
		headers.add("Besked");
		headers.add("Personnummer");
		headers.add("Person");
		headers.add("Administrator");

		createHeaderRow(sheet, headers);

		int row = 1;
		for (AuditLog entry : auditLogs.stream()
				.filter(a -> !(Objects.equals(a.getLogAction(), LogAction.DEACTIVATE_BY_ADMIN)  || Objects.equals(a.getLogAction(), LogAction.REACTIVATE_BY_ADMIN)))
				.collect(Collectors.toList())) {

			Row dataRow = sheet.createRow(row++);
			int column = 0;

			createCell(dataRow, column++, entry.getTts().toString(), null);
			createCell(dataRow, column++, entry.getIpAddress(), null);
			createCell(dataRow, column++, resourceBundle.getMessage(entry.getLogAction().getMessage(), null, Locale.ENGLISH), null);
			createCell(dataRow, column++, entry.getMessage(), null);
			createCell(dataRow, column++, !StringUtils.hasLength(entry.getCpr()) ? "" : entry.getCpr().substring(0, 6) + "-XXXX", null);
			createCell(dataRow, column++, entry.getPersonName(), null);
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