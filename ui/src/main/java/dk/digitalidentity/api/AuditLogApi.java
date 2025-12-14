package dk.digitalidentity.api;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.api.dto.AuditLogApiViewHeadDTO;
import dk.digitalidentity.common.dao.AuditLogApiViewDao;
import dk.digitalidentity.common.dao.model.AuditLogApiView;
import dk.digitalidentity.datatables.MfaLoginHistoryDatatablesDao;
import dk.digitalidentity.datatables.model.MfaLoginHistoryView;

@RestController
public class AuditLogApi {

	@Autowired
	private AuditLogApiViewDao auditLogApiViewDao;
	
	@Autowired
	private MfaLoginHistoryDatatablesDao mfaLoginHistoryDatatablesDao;

	@GetMapping("/api/auditlog/head")
	@ResponseBody
	public ResponseEntity<?> getHeadIndex() {
		AuditLogApiViewHeadDTO dto = new AuditLogApiViewHeadDTO();
		dto.setHead(auditLogApiViewDao.getMaxId());

		return ResponseEntity.ok(dto);
	}

	@GetMapping("/api/auditlog/read")
	public ResponseEntity<?> getLogs(@RequestParam(name = "offset", defaultValue = "0") int offset) {
		List<AuditLogApiView> logs = auditLogApiViewDao.findAllWithOffsetAndSize(offset, 100);

		return ResponseEntity.ok(logs);
	}
	
	@GetMapping("/api/auditlog/mfa/head")
	@ResponseBody
	public ResponseEntity<?> getMfaHeadIndex() {
		AuditLogApiViewHeadDTO dto = new AuditLogApiViewHeadDTO();
		dto.setHead(mfaLoginHistoryDatatablesDao.getMaxId());

		return ResponseEntity.ok(dto);
	}

	@GetMapping("/api/auditlog/mfa/read")
	public ResponseEntity<?> getMfaLogs(@RequestParam(name = "offset", defaultValue = "0") int offset) {
		List<MfaLoginHistoryView> logs = mfaLoginHistoryDatatablesDao.findAllWithOffsetAndSize(offset, 100);

		return ResponseEntity.ok(logs);
	}
}
