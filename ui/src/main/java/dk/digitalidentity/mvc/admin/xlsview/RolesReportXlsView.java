package dk.digitalidentity.mvc.admin.xlsview;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.web.servlet.View;

import dk.digitalidentity.common.dao.model.KombitJfr;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.service.PersonService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class RolesReportXlsView implements View {
	private static final String CONTENT_TYPE = "application/ms-excel";
	
	private List<Person> persons;
	private CellStyle headerStyle;
	private CellStyle wrapStyle;

	@Override
	public String getContentType() {
		return CONTENT_TYPE;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void render(Map<String, ?> model, HttpServletRequest request, HttpServletResponse response) throws Exception {
		// Get data
		persons = (List<Person>) model.get("persons");

		response.setContentType(getContentType());
		response.setHeader("Content-Disposition", "attachment; filename=\"Jobfunktionsroller.xlsx\"");

		try (Workbook workbook = new DisposableSXSSFWorkbook()) {
	
			// Setup shared resources
			Font headerFont = workbook.createFont();
			headerFont.setBold(true);
	
			headerStyle = workbook.createCellStyle();
			headerStyle.setFont(headerFont);
	
			wrapStyle = workbook.createCellStyle();
			wrapStyle.setWrapText(true);
	
			// Create Sheets
			createRolesSheet(workbook);
			
			workbook.write(response.getOutputStream());
		}
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
