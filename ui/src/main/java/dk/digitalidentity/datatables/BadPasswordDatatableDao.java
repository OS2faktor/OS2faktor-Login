package dk.digitalidentity.datatables;

import org.springframework.data.jpa.datatables.repository.DataTablesRepository;

import dk.digitalidentity.common.dao.model.BadPassword;

public interface BadPasswordDatatableDao extends DataTablesRepository<BadPassword, Long> {
}
