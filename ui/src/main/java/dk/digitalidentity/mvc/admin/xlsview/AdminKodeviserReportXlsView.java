package dk.digitalidentity.mvc.admin.xlsview;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.web.servlet.View;

import dk.digitalidentity.common.dao.model.CachedMfaClient;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.service.CachedMfaClientService;
import dk.digitalidentity.common.service.mfa.model.ClientType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class AdminKodeviserReportXlsView implements View {
	private static final String CONTENT_TYPE = "application/ms-excel";

    private CellStyle headerStyle;
    private CellStyle wrapStyle;
    private CachedMfaClientService cachedMfaClientService;

	@Override
	public String getContentType() {
		return CONTENT_TYPE;
	}

	@Override
	public void render(Map<String, ?> model, HttpServletRequest request, HttpServletResponse response) throws Exception {

    	// obtaining data
        cachedMfaClientService = (CachedMfaClientService) model.get("cachedMfaClientService");

		response.setContentType(getContentType());
		response.setHeader("Content-Disposition", "attachment; filename=\"kodeviser.xlsx\"");

		try (Workbook workbook = new DisposableSXSSFWorkbook()) {

	        Sheet sheet = workbook.createSheet("Kodeviser");
	        Font headerFont = workbook.createFont();
	        headerFont.setBold(true);
	
	        // Make the header
	        ArrayList<String> headers = new ArrayList<>();
	        headers.add("Navn");
	        headers.add("Brugernavn");
	        headers.add("OS2faktor ID");
	        headers.add("Serienummer");
	        headers.add("NSIS sikringsniveau");
	        headers.add("Sidst anvendt");
	
	        headerStyle = workbook.createCellStyle();
	        headerStyle.setFont(headerFont);
	
	        wrapStyle = workbook.createCellStyle();
	        wrapStyle.setWrapText(true);
	
	        createHeaderRow(sheet, headers);
	
	        // Input the data into the table
	        int row = 1;
	        for (CachedMfaClient entry : cachedMfaClientService.findByType(ClientType.TOTPH)) {
	            int column = 0;
	            Row dataRow = sheet.createRow(row++);
	            createCell(dataRow, column++, entry.getName(), null);
	            createCell(dataRow, column++, entry.getPerson().getSamaccountName(), null);
	            createCell(dataRow, column++, entry.getDeviceId(), null);
	            createCell(dataRow, column++, entry.getSerialnumber(), null);
	            createCell(dataRow, column++, nsisLevelToDanish(entry.getNsisLevel()), null);
	            createCell(dataRow, column++, (entry.getLastUsed() != null) ? entry.getLastUsed().toString() : "", null);
	        }
	        
			workbook.write(response.getOutputStream());
		}
    }

    private void createHeaderRow(Sheet sheet, List<String> headers) {
        Row headerRow = sheet.createRow(0);

        int column = 0;
        for (String header : headers) {
            createCell(headerRow, column++, header, headerStyle);
        }
    }

    private void createCell(Row header, int column, String value, CellStyle style) {
        Cell cell = header.createCell(column);
        cell.setCellValue(value);

        if (style != null) {
            cell.setCellStyle(style);
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
}