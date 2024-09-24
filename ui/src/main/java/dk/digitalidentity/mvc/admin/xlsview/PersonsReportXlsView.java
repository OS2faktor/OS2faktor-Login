package dk.digitalidentity.mvc.admin.xlsview;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import dk.digitalidentity.common.dao.model.PersonStatistics;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.web.servlet.view.document.AbstractXlsxStreamingView;

import dk.digitalidentity.common.dao.model.CachedMfaClient;
import dk.digitalidentity.common.dao.model.MitidErhvervCache;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.service.PersonService;

public class PersonsReportXlsView extends AbstractXlsxStreamingView {
	private List<Person> persons;
	private Map<Long, PersonStatistics> statisticsMap;
	private List<MitidErhvervCache> mitIDErhvervCache;
	private CellStyle headerStyle;
	private CellStyle wrapStyle;
	private boolean enableRegistrantFeature;

	@SuppressWarnings("unchecked")
	@Override
	protected void buildExcelDocument(Map<String, Object> model, Workbook workbook, HttpServletRequest request, HttpServletResponse response) throws Exception {
		// Get data
		persons = (List<Person>) model.get("persons");
		List<PersonStatistics> statistics = (List<PersonStatistics>) model.get("statistics");
		enableRegistrantFeature = (boolean) model.get("enableRegistrantFeature");
		mitIDErhvervCache = (List<MitidErhvervCache>) model.get("mitIDErhvervCache");

		// Setup shared resources
		Font headerFont = workbook.createFont();
		headerFont.setBold(true);

		headerStyle = workbook.createCellStyle();
		headerStyle.setFont(headerFont);

		wrapStyle = workbook.createCellStyle();
		wrapStyle.setWrapText(true);

		statisticsMap = statistics.stream().collect(Collectors.toMap(PersonStatistics::getPersonId, Function.identity()));
		
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
		headers.add("MitID Erhverv UUID");
		headers.add("MitID Erhverv Status");
		headers.add("MitID Erhverv Personligt MitID");
		headers.add("MitID Erhverv Kvalificeret Signatur");
		headers.add("Afdeling");
		headers.add("Seneste lokal IdP login");
		headers.add("Seneste login på selvbetjeningen");
		headers.add("Seneste ændring af kodeord");
		headers.add("Senest låst op");
		headers.add("Seneste MFA validering");

		createHeaderRow(sheet, headers);

		Map<String, MitidErhvervCache> mitIdErhvervMap = mitIDErhvervCache.stream().collect(Collectors.toMap(MitidErhvervCache::getUuid, Function.identity()));

		int row = 1;
		for (Person entry : persons) {

			PersonStatistics personStatistics = statisticsMap.get(entry.getId());
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
					createCell(dataRow, column++, "Aktiv (erhvervsidentitet ikke aktiveret)", null);
				}
				else {
					createCell(dataRow, column++, "Aktiv (erhvervsidentitet aktiveret)", null);
				}
			}
			else {
				createCell(dataRow, column++, "Aktiv (ingen erhvervsidentitet)", null);
			}
			
			// MFA klienter
			StringBuilder mfaClients = new StringBuilder();
			for (CachedMfaClient mfaClient : entry.getMfaClients()) {
				if (mfaClients.length() > 0) {
					mfaClients.append("\n");
				}

				mfaClients.append(mfaClient.getDeviceId() + " / " + mfaClient.getType() + " / " + mfaClient.getName() + " / " + nsisLevelToDanish(mfaClient.getNsisLevel()));
				if (mfaClient.getLastUsed() != null) {
					mfaClients.append(" / anvendt: ").append(mfaClient.getLastUsed().toLocalDate().toString());
				}
				if (mfaClient.getAssociatedUserTimestamp() != null) {
					mfaClients.append(" / registreret: ").append(mfaClient.getAssociatedUserTimestamp().toLocalDate().toString());
				}
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

			// NemLogin UUID
			createCell(dataRow, column++, entry.getNemloginUserUuid(), null);

			MitidErhvervCache cache = mitIdErhvervMap.get(entry.getNemloginUserUuid());
			if (cache != null) {
				createCell(dataRow, column++, Objects.equals("Active", cache.getStatus()) ? "Aktiv" : "Spærret", null);
				createCell(dataRow, column++, cache.isMitidPrivatCredential() ? "Ja" : "Nej", null);
				createCell(dataRow, column++, cache.isQualifiedSignature() ? "Ja" : "Nej", null);				
			}
			else {
				createCell(dataRow, column++, "", null);
				createCell(dataRow, column++, "", null);
				createCell(dataRow, column++, "", null);
			}
			
			// Department
			createCell(dataRow, column++, entry.getDepartment(), null);

			// local login
			if (personStatistics != null && personStatistics.getLastLogin() != null) {
				createCell(dataRow, column++, personStatistics.getLastLogin().toString(), null);
			}
			else {
				createCell(dataRow, column++, "", null);
			}

			// selfservice login
			if (personStatistics != null && personStatistics.getLastSelfServiceLogin() != null) {
				createCell(dataRow, column++, personStatistics.getLastSelfServiceLogin().toString(), null);
			}
			else {
				createCell(dataRow, column++, "", null);
			}

			// password change
			if (personStatistics != null && personStatistics.getLastPasswordChange() != null) {
				createCell(dataRow, column++, personStatistics.getLastPasswordChange().toString(), null);
			}
			else {
				createCell(dataRow, column++, "", null);
			}

			// unlock
			if (personStatistics != null && personStatistics.getLastUnlock() != null) {
				createCell(dataRow, column++, personStatistics.getLastUnlock().toString(), null);
			}
			else {
				createCell(dataRow, column++, "", null);
			}

			// mfa use
			if (personStatistics != null && personStatistics.getLastMFAUse() != null) {
				createCell(dataRow, column++, personStatistics.getLastMFAUse().toString(), null);
			}
			else {
				createCell(dataRow, column++, "", null);
			}
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
