package dk.digitalidentity.mvc.admin.xlsview;

import java.time.LocalDateTime;
import java.util.Map;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.web.servlet.view.document.AbstractXlsxStreamingView;

import dk.digitalidentity.common.dao.model.enums.LogAction.ReportType;
import dk.digitalidentity.service.AuditLogReportXlsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class AuditLogReportXlsView extends AbstractXlsxStreamingView {
	private CellStyle headerStyle;
	private CellStyle wrapStyle;
	private AuditLogReportXlsService auditLogReportXlsService;

	@Override
	protected void buildExcelDocument(Map<String, Object> model, Workbook workbook, HttpServletRequest request, HttpServletResponse response) throws Exception {

		// Get data
		auditLogReportXlsService = (AuditLogReportXlsService) model.get("auditLogReportXlsService");
		ReportType type = (ReportType) model.get("type");
		LocalDateTime from = (LocalDateTime) model.get("from");
		LocalDateTime to = (LocalDateTime) model.get("to");

		// Setup shared resources
		Font headerFont = workbook.createFont();
		headerFont.setBold(true);

		headerStyle = workbook.createCellStyle();
		headerStyle.setFont(headerFont);

		wrapStyle = workbook.createCellStyle();
		wrapStyle.setWrapText(true);

		// Create Sheets
		auditLogReportXlsService.createAuditLogSheet(workbook, from, to, type, headerStyle);
	}
}
