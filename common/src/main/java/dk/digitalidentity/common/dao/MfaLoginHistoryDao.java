package dk.digitalidentity.common.dao;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import dk.digitalidentity.common.dao.model.MfaLoginHistory;

public interface MfaLoginHistoryDao extends JpaRepository<MfaLoginHistory, Long> {

	@Modifying
	@Query(nativeQuery = true, value = "DELETE FROM mfa_login_history WHERE created_tts < CURRENT_TIMESTAMP - INTERVAL 30 DAY")
	void deleteOld();

	@Query(nativeQuery = true, value = "SELECT MAX(created_tts) FROM mfa_login_history")
	LocalDateTime getMaxCreatedTts();

}
