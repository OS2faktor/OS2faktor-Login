package dk.digitalidentity.mvc.admin.xlsview;

import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.web.servlet.view.document.AbstractXlsxStreamingView;

import dk.digitalidentity.common.dao.model.AuditLog;
import dk.digitalidentity.service.AuditLogReportXlsService;

public class AuditLogReportXlsViewStatic extends AbstractXlsxStreamingView {
	private CellStyle headerStyle;
	private CellStyle wrapStyle;
	private AuditLogReportXlsService auditLogReportXlsService;

	@Override
	protected void buildExcelDocument(Map<String, Object> model, Workbook workbook, HttpServletRequest request, HttpServletResponse response) throws Exception {

		// Get data
		auditLogReportXlsService = (AuditLogReportXlsService) model.get("auditLogReportXlsService");

		@SuppressWarnings("unchecked")
		List<AuditLog> auditlogs = (List<AuditLog>) model.get("auditLogs");

		// Setup shared resources
		Font headerFont = workbook.createFont();
		headerFont.setBold(true);

		headerStyle = workbook.createCellStyle();
		headerStyle.setFont(headerFont);

		wrapStyle = workbook.createCellStyle();
		wrapStyle.setWrapText(true);

		// Create Sheets
		auditLogReportXlsService.createAuditLogSheetStatic(workbook, auditlogs, headerStyle);
	}
}
