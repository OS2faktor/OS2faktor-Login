package dk.digitalidentity.common.service;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import dk.digitalidentity.common.dao.CertificateChangelogDao;
import dk.digitalidentity.common.dao.model.CertificateChangelog;
import dk.digitalidentity.common.dao.model.enums.CertificateChange;
import jakarta.servlet.http.HttpServletRequest;

@Service
public class CertificateChangelogService {

	@Autowired
	private CertificateChangelogDao certificateChangelogDao;

	public void newCertificate(String operatorId, String details) {
		CertificateChangelog changelog = new CertificateChangelog();
		changelog.setChangeType(CertificateChange.NEW);
		changelog.setDetails(details);
		changelog.setIpAddress(getIpAddress());
		changelog.setOperatorId(operatorId);
		changelog.setTts(LocalDateTime.now());
		
		certificateChangelogDao.save(changelog);
	}

	public void swapCertificate(String operatorId, String details) {
		CertificateChangelog changelog = new CertificateChangelog();
		changelog.setChangeType(CertificateChange.SWAP);
		changelog.setDetails(details);
		changelog.setIpAddress(getIpAddress());
		changelog.setOperatorId(operatorId);
		changelog.setTts(LocalDateTime.now());
		
		certificateChangelogDao.save(changelog);
	}

	public void deleteCertificate(String operatorId, String details) {
		CertificateChangelog changelog = new CertificateChangelog();
		changelog.setChangeType(CertificateChange.DELETE);
		changelog.setDetails(details);
		changelog.setIpAddress(getIpAddress());
		changelog.setOperatorId(operatorId);
		changelog.setTts(LocalDateTime.now());
		
		certificateChangelogDao.save(changelog);
	}

	private static String getIpAddress() {
		String remoteAddr = "";

		HttpServletRequest request = getRequest();
		if (request != null) {
			remoteAddr = request.getHeader("X-FORWARDED-FOR");
			if (remoteAddr == null || "".equals(remoteAddr)) {
				remoteAddr = request.getRemoteAddr();
			}
		}

		return remoteAddr;
	}

	private static HttpServletRequest getRequest() {
		try {
			return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
		}
		catch (IllegalStateException ex) {
			return null;
		}
	}
}
