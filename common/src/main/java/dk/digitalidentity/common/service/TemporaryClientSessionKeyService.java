package dk.digitalidentity.common.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.TemporaryClientSessionKeyDao;
import dk.digitalidentity.common.dao.model.TemporaryClientSessionKey;
import jakarta.servlet.http.HttpServletRequest;

@Service
public class TemporaryClientSessionKeyService {

	@Autowired
	private TemporaryClientSessionKeyDao windowsTemporarySessionKeyDao;

	public TemporaryClientSessionKey getBySessionKey(String sessionKey) {
		return windowsTemporarySessionKeyDao.findBySessionKey(sessionKey);
	}

	public List<TemporaryClientSessionKey> getAll() {
		return windowsTemporarySessionKeyDao.findAll();
	}

	public TemporaryClientSessionKey save(TemporaryClientSessionKey sessionKey) {
		return windowsTemporarySessionKeyDao.save(sessionKey);
	}

	public List<TemporaryClientSessionKey> getAllWithTtsBefore(LocalDateTime before) {
		return windowsTemporarySessionKeyDao.findByTtsBefore(before);
	}

	public void delete(TemporaryClientSessionKey sessionKey) {
		windowsTemporarySessionKeyDao.delete(sessionKey);
		windowsTemporarySessionKeyDao.deleteAll();
	}

	public void deleteMultiple(List<TemporaryClientSessionKey> sessionKeys) {
		windowsTemporarySessionKeyDao.deleteAll(sessionKeys);
	}

	public String getIpAddressFromRequest(HttpServletRequest request) {
		String remoteAddr = "";

		if (request != null) {
			remoteAddr = request.getHeader("X-FORWARDED-FOR");
			if (remoteAddr == null || "".equals(remoteAddr)) {
				remoteAddr = request.getRemoteAddr();
			}
		}

		return remoteAddr;
	}
}
