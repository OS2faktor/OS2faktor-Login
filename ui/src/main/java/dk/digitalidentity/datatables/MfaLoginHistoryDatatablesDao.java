package dk.digitalidentity.datatables;

import org.springframework.data.jpa.datatables.repository.DataTablesRepository;

import dk.digitalidentity.datatables.model.MfaLoginHistoryView;

public interface MfaLoginHistoryDatatablesDao extends DataTablesRepository<MfaLoginHistoryView, Long> {
}
