package dk.digitalidentity.common.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.KombitSubsystemDao;
import dk.digitalidentity.common.dao.model.KombitSubsystem;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class KombitSubSystemService {

	@Autowired
	private KombitSubsystemDao kombitSubsystemDao;
	
	public List<KombitSubsystem> findAll() {
		return kombitSubsystemDao.findByDeletedFalse();
	}
	
	public KombitSubsystem findByEntityId(String entityId) {
		return kombitSubsystemDao.findByEntityId(entityId);
	}
	
	public KombitSubsystem save(KombitSubsystem subsystem) {
		try {
			return kombitSubsystemDao.save(subsystem);
		}
		catch (Exception ex) {
			log.error("Failed to save subsystem: " + subsystem.getEntityId(), ex);
		}
		
		return subsystem;
	}

	public KombitSubsystem findById(long id) {
		return kombitSubsystemDao.findById(id).orElse(null);
	}
}
