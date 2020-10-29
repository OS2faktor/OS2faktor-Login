package dk.digitalidentity.datatables;

import org.springframework.data.jpa.datatables.repository.DataTablesRepository;

import dk.digitalidentity.datatables.model.AdminPersonView;

public interface PersonDatatableDao extends DataTablesRepository<AdminPersonView, Long> {
}
