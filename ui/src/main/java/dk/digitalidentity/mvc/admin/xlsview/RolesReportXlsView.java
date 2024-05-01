package dk.digitalidentity.mvc.admin.xlsview;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import dk.digitalidentity.common.dao.model.KombitJfr;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.web.servlet.view.document.AbstractXlsxStreamingView;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.service.PersonService;

public class RolesReportXlsView extends AbstractXlsxStreamingView {
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
		createRolesSheet(workbook);
	}

	private void createRolesSheet(Workbook workbook) {
		Sheet sheet = workbook.createSheet("Jobfunktionsroller");

		ArrayList<String> headers = new ArrayList<>();
		headers.add("Navn");
		headers.add("Brugernavn");
		headers.add("Domæne");
		headers.add("Jobfunktionsroller");

		createHeaderRow(sheet, headers);

		int row = 1;
		for (Person entry : persons) {

			Row dataRow = sheet.createRow(row++);
			int column = 0;

			// Navn
			createCell(dataRow, column++, entry.getName(), null);

			// Brugernavn
			createCell(dataRow, column++, PersonService.getUsername(entry), null);

			// Domæne
			createCell(dataRow, column++, entry.getDomain().getName(), null);

			// Jobfunktionsroller
			StringJoiner roles = new StringJoiner("\n");

			for (KombitJfr role : entry.getKombitJfrs()) {
				roles.add(role.getIdentifier());
			}

			createCell(dataRow, column++, roles.toString(), null);
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
