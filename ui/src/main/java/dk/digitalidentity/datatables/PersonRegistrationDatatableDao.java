package dk.digitalidentity.datatables;

import org.springframework.data.jpa.datatables.repository.DataTablesRepository;

import dk.digitalidentity.datatables.model.RegistrationPersonView;

public interface PersonRegistrationDatatableDao extends DataTablesRepository<RegistrationPersonView, Long> {
}
