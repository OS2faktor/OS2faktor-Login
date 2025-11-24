package dk.digitalidentity.mvc.admin.xlsview;

import java.time.LocalDateTime;
import java.util.Map;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.web.servlet.View;

import dk.digitalidentity.common.dao.model.enums.LogAction.ReportType;
import dk.digitalidentity.service.AuditLogReportXlsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class AuditLogReportXlsView  implements View {
	private static final String CONTENT_TYPE = "application/ms-excel";

	private CellStyle headerStyle;
	private CellStyle wrapStyle;
	private AuditLogReportXlsService auditLogReportXlsService;
	private String filename;

	public AuditLogReportXlsView(String filename) {
		this.filename = filename;
	}

	@Override
	public String getContentType() {
		return CONTENT_TYPE;
	}

	@Override
	public void render(Map<String, ?> model, HttpServletRequest request, HttpServletResponse response) throws Exception {

		// Get data
		auditLogReportXlsService = (AuditLogReportXlsService) model.get("auditLogReportXlsService");
		ReportType type = (ReportType) model.get("type");
		LocalDateTime from = (LocalDateTime) model.get("from");
		LocalDateTime to = (LocalDateTime) model.get("to");

		response.setContentType(getContentType());
		response.setHeader("Content-Disposition", "attachment; filename=\"" + filename +"\"");

		try (Workbook workbook = new DisposableSXSSFWorkbook()) {

			// Setup shared resources
			Font headerFont = workbook.createFont();
			headerFont.setBold(true);
	
			headerStyle = workbook.createCellStyle();
			headerStyle.setFont(headerFont);
	
			wrapStyle = workbook.createCellStyle();
			wrapStyle.setWrapText(true);
	
			// Create Sheets
			auditLogReportXlsService.createAuditLogSheet(workbook, from, to, type, headerStyle);
			
			workbook.write(response.getOutputStream());
		}
	}
}
