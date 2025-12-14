package dk.digitalidentity.mvc.admin;

import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import dk.digitalidentity.common.dao.MfaLoginHistoryDao;
import dk.digitalidentity.common.dao.model.MfaLoginHistory;
import dk.digitalidentity.security.RequireAdministrator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequireAdministrator
@Controller
public class MfaLoginHistoryController {
	private static final DateTimeFormatter PATTERN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	@Autowired
	private MfaLoginHistoryDao mfaLoginHistoryDao;

	@GetMapping("/admin/mfahistory")
	public String mfaHistory(Model model) {
		return "admin/mfa-history";
	}

	@GetMapping("/admin/mfahistory/download")
	public ResponseEntity<StreamingResponseBody> downloadMFAHistory(HttpServletRequest request, HttpServletResponse response) {
		log.info("Starting download of MFA History");

		return ResponseEntity.ok().header("Content-Disposition", "attachment; filename=\"MFA_History.zip\"") 
			.body(out -> { 
				var zipOutputStream = new ZipOutputStream(out);
				
				addFilesToZipCsv(request, zipOutputStream);
				
				zipOutputStream.close(); 
			}
		);
	}
	
	private void addFilesToZipCsv(HttpServletRequest request, ZipOutputStream zipOutputStream) {
		try {
			log.info("Starting rendering logs");
			
			boolean isEmpty = mfaLoginHistoryDao.count() == 0;
			if (!isEmpty) {
				ZipEntry taskfile = new ZipEntry("mfalog.csv");
				zipOutputStream.putNextEntry(taskfile);

				StringBuilder builder = new StringBuilder();
				
				builder.append("\"").append("Oprettet").append("\";");
				builder.append("\"").append("Anvendt af").append("\";");
				builder.append("\"").append("Klient ID").append("\";");
				builder.append("\"").append("Klient Type").append("\";");
				builder.append("\"").append("Status").append("\";");
				builder.append("\"").append("Notifikation").append("\";");
				builder.append("\"").append("Hentet").append("\";");
				builder.append("\"").append("Svaret").append("\";");
				builder.append("\"").append("System").append("\";");
				builder.append("\"").append("Brugernavn").append("\"\n");

				zipOutputStream.write(builder.toString().getBytes(Charset.forName("ISO-8859-1")));

				List<MfaLoginHistory> mfaHistoryEntry = mfaLoginHistoryDao.findAll(); //TODO do we need pagination?

				for (MfaLoginHistory entry : mfaHistoryEntry) {
					builder = new StringBuilder();

					builder.append("\"").append(formatDateTime(entry.getCreatedTts())).append("\";");
					builder.append("\"").append(entry.getServerName()).append("\";");
					builder.append("\"").append(entry.getDeviceId()).append("\";");
					builder.append("\"").append(entry.getClientType()).append("\";");
					builder.append("\"").append(entry.getStatus()).append("\";");
					builder.append("\"").append(formatDateTime(entry.getPushTts())).append("\";");
					builder.append("\"").append(formatDateTime(entry.getFetchTts())).append("\";");
					builder.append("\"").append(formatDateTime(entry.getResponseTts())).append("\";");
					builder.append("\"").append(entry.getSystemName() != null ? entry.getSystemName() : "").append("\";");
					builder.append("\"").append(entry.getUsername() != null ? entry.getUsername() : "").append("\"\n");

					zipOutputStream.write(builder.toString().getBytes(Charset.forName("ISO-8859-1")));
				}
			}
			
			log.info("Done rendering logs");
		}
		catch (Exception ex) {
			log.error("Failed rendering MFA logs", ex);
		}
		
		log.info("Done rendering logs for download");
	}
	
	private String formatDateTime(LocalDateTime tts) {
		if (tts == null) {
			return "";
		}
		else {
			return tts.format(PATTERN);
		}
	}
}
