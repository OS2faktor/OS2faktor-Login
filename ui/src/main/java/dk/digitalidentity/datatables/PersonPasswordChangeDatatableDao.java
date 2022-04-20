package dk.digitalidentity.datatables;

import org.springframework.data.jpa.datatables.repository.DataTablesRepository;

import dk.digitalidentity.datatables.model.PersonPasswordChangeView;

public interface PersonPasswordChangeDatatableDao extends DataTablesRepository<PersonPasswordChangeView, Long> {
}
