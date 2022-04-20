package dk.digitalidentity.datatables;

import org.springframework.data.jpa.datatables.repository.DataTablesRepository;

import dk.digitalidentity.datatables.model.PersonWherePasswordChangeAllowedView;

public interface PersonWherePasswordChangeAllowedDao extends DataTablesRepository<PersonWherePasswordChangeAllowedView, Long> {

}
