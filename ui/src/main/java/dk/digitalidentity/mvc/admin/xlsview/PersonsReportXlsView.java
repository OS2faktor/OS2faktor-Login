package dk.digitalidentity.mvc.admin.xlsview;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.web.servlet.view.document.AbstractXlsxStreamingView;

import dk.digitalidentity.common.dao.model.CachedMfaClient;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.service.PersonService;

public class PersonsReportXlsView extends AbstractXlsxStreamingView {
	private List<Person> persons;
	private CellStyle headerStyle;
	private CellStyle wrapStyle;
	private boolean enableRegistrantFeature;

	@SuppressWarnings("unchecked")
	@Override
	protected void buildExcelDocument(Map<String, Object> model, Workbook workbook, HttpServletRequest request, HttpServletResponse response) throws Exception {
		// Get data
		persons = (List<Person>) model.get("persons");
		enableRegistrantFeature = (boolean) model.get("enableRegistrantFeature");

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
		Sheet sheet = workbook.createSheet("Brugerkonti");

		ArrayList<String> headers = new ArrayList<>();
		headers.add("Navn");
		headers.add("Brugernavn");
		headers.add("Personnummer");
		headers.add("Domæne");
		headers.add("Dato for godkendt vilkår");
		headers.add("NSIS sikringsniveau");
		headers.add("NSIS status");
		headers.add("2-faktor enheder");
		headers.add("Administratorroller");

		createHeaderRow(sheet, headers);

		int row = 1;
		for (Person entry : persons) {

			Row dataRow = sheet.createRow(row++);
			int column = 0;

			// Navn
			createCell(dataRow, column++, entry.getName(), null);

			// Brugernavn
			createCell(dataRow, column++, PersonService.getUsername(entry), null);

			// Personnummer
			createCell(dataRow, column++, entry.getCpr().substring(0, 6) + "-xxxx", null);

			// Domæne
			createCell(dataRow, column++, entry.getDomain().getName(), null);

			// Dato for godkendt vilkår
			if (entry.getApprovedConditionsTts() != null) {
				createCell(dataRow, column++, entry.getApprovedConditionsTts().toString(), null);
			}
			else {
				createCell(dataRow, column++, "", null);
			}

			// NSIS niveau
			createCell(dataRow, column++, nsisLevelToDanish(entry.getNsisLevel()), null);
			
			// NSIS status
			if (entry.isLocked()) {
				if (entry.isLockedAdmin() || entry.isLockedDataset()) {
					createCell(dataRow, column++, "Spærret (af kommunen)", null);
				}
				else if (entry.isLockedPerson() || entry.isLockedPassword()) {
					createCell(dataRow, column++, "Spærret (af brugeren selv)", null);
				}
				else if (entry.isLockedExpired()) {
					createCell(dataRow, column++, "Spærret (udløbet)", null);
				}
				else {
					createCell(dataRow, column++, "Spærret (civilstatus)", null);
				}
			}
			else if (entry.isNsisAllowed()) {
				if (entry.getNsisLevel().equals(NSISLevel.NONE)) {
					createCell(dataRow, column++, "Erhvervsidentitet ikke aktiveret", null);
				}
				else {
					createCell(dataRow, column++, "Erhvervsidentitet aktiveret", null);
				}
			}
			else {
				createCell(dataRow, column++, "Erhvervsidentitet ikke tildelt", null);
			}

			
			// MFA klienter
			StringBuilder mfaClients = new StringBuilder();
			for (CachedMfaClient mfaClient : entry.getMfaClients()) {
				if (mfaClients.length() > 0) {
					mfaClients.append("\n");
				}

				mfaClients.append(mfaClient.getDeviceId() + " / " + mfaClient.getType() + " / " + mfaClient.getName() + " / " + nsisLevelToDanish(mfaClient.getNsisLevel()));
			}

			createCell(dataRow, column++, mfaClients.toString(), null);

			// Administratorroller

			StringJoiner adminRoles = new StringJoiner("\n");
			
			if (entry.isAdmin()) {
				adminRoles.add("Administrator");
			}

			if (entry.isServiceProviderAdmin()) {
				adminRoles.add("TU administrator");
			}

			if (entry.isUserAdmin()) {
				adminRoles.add("Brugeradministrator");
			}

			if (entry.isRegistrant() && enableRegistrantFeature) {
				adminRoles.add("Registrant");
			}

			if (entry.isSupporter()) {
				adminRoles.add("Supporter");
			}

			createCell(dataRow, column++, adminRoles.toString(), null);
		}
	}

	private String nsisLevelToDanish(NSISLevel nsisLevel) {
		switch (nsisLevel) {
			case HIGH:
				return "Høj";
			case LOW:
				return "Lav";
			case SUBSTANTIAL:
				return "Betydelig";
			default:
				return "";
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
