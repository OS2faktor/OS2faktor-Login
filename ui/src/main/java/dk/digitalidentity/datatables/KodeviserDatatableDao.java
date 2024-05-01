package dk.digitalidentity.datatables;

import org.springframework.data.jpa.datatables.repository.DataTablesRepository;

import dk.digitalidentity.datatables.model.KodeviserView;

public interface KodeviserDatatableDao extends DataTablesRepository<KodeviserView, Long> {
}
