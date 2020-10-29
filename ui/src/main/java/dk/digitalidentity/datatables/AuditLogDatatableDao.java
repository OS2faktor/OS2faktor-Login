package dk.digitalidentity.datatables;

import org.springframework.data.jpa.datatables.repository.DataTablesRepository;

import dk.digitalidentity.datatables.model.AuditLogView;

public interface AuditLogDatatableDao extends DataTablesRepository<AuditLogView, Long> {

}