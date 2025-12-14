package dk.digitalidentity.datatables;

import java.util.List;

import org.springframework.data.jpa.datatables.repository.DataTablesRepository;
import org.springframework.data.jpa.repository.Query;

import dk.digitalidentity.datatables.model.MfaLoginHistoryView;

public interface MfaLoginHistoryDatatablesDao extends DataTablesRepository<MfaLoginHistoryView, Long> {
	
	@Query(value = "SELECT max(id) FROM view_mfa_login_history")
	long getMaxId();

	@Query(value = "SELECT * FROM view_mfa_login_history a WHERE a.id > ?1 ORDER BY a.id ASC LIMIT ?2", nativeQuery = true)
	public List<MfaLoginHistoryView> findAllWithOffsetAndSize(long offset, long size);

}
