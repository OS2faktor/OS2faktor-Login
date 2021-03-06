package dk.digitalidentity.mvc.admin.xlsview;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.web.servlet.view.document.AbstractXlsView;

import dk.digitalidentity.common.dao.model.Person;

public class PersonsReportXlsView extends AbstractXlsView {
	private List<Person> persons;
	private CellStyle headerStyle;
	private CellStyle wrapStyle;

	@SuppressWarnings("unchecked")
	@Override
	protected void buildExcelDocument(Map<String, Object> model, Workbook workbook, HttpServletRequest request, HttpServletResponse response) throws Exception {

		// Get data
		persons = (List<Person>) model.get("persons");

		// Setup shared resources
		Font headerFont = workbook.createFont();
		headerFont.setBold(true);

		headerStyle = workbook.createCellStyle();
		headerStyle.setFont(headerFont);

		wrapStyle = workbook.createCellStyle();
		wrapStyle.setWrapText(true);

		// Create Sheets
		createPersonsSheet(workbook);
	}

	private void createPersonsSheet(Workbook workbook) {
		Sheet sheet = workbook.createSheet("PersonSheet");

		ArrayList<String> headers = new ArrayList<>();
		headers.add("Navn");
		headers.add("Bruger-ID");
		headers.add("AD konto");
		headers.add("Personnummer");
		headers.add("Dato for godkendt vilkår");
		headers.add("NSIS niveau");

		createHeaderRow(sheet, headers);

		int row = 1;
		for (Person entry : persons) {

			String nsisNiveau = "";
			if (Objects.equals(entry.getNsisLevel().toString(), "LOW")) {
				nsisNiveau = "Lav";
			}
			else if (Objects.equals(entry.getNsisLevel().toString(), "SUBSTANTIAL")) {
				nsisNiveau = "Betydelig";
			}
			else if (Objects.equals(entry.getNsisLevel().toString(), "HIGH")) {
				nsisNiveau = "Høj";
			}

			Row dataRow = sheet.createRow(row++);
			int column = 0;
			createCell(dataRow, column++, entry.getName(), null);
			if (entry.getUserId() != null) {
				createCell(dataRow, column++, entry.getUserId(), null);
			}
			else {
				createCell(dataRow, column++, "", null);
			}
			
			createCell(dataRow, column++, entry.getSamaccountName(), null);
			createCell(dataRow, column++, entry.getCpr().substring(0, 6) + "-xxxx", null);

			if (entry.getApprovedConditionsTts() != null) {
				createCell(dataRow, column++, entry.getApprovedConditionsTts().toString(), null);
			}
			else {
				createCell(dataRow, column++, "", null);
			}
			createCell(dataRow, column++, nsisNiveau, null);

			for (int i = 0; i <= column; i++) {
				sheet.autoSizeColumn(i);
			}
		}
	}

	private void createHeaderRow(Sheet sheet, List<String> headers) {
		Row headerRow = sheet.createRow(0);

		int column = 0;
		for (String header : headers) {
			createCell(headerRow, column++, header, headerStyle);
			sheet.autoSizeColumn(column);
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
