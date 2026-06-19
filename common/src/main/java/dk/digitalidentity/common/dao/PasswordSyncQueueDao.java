package dk.digitalidentity.common.dao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import dk.digitalidentity.common.dao.model.PasswordSyncQueueItem;
import dk.digitalidentity.common.dao.model.Person;

public interface PasswordSyncQueueDao extends JpaRepository<PasswordSyncQueueItem, Long> {

	@Query("""
        SELECT item FROM PasswordSyncQueueItem item
        JOIN FETCH item.person p
        JOIN FETCH p.domain
        WHERE item.status = 'PENDING'
        AND item.nextAttemptAt <= :now
        AND item.tts >= :maxAgeCutoff
    """)
    List<PasswordSyncQueueItem> findProcessableMessages(final LocalDateTime maxAgeCutoff, final LocalDateTime now);

    @Query("""
        SELECT item FROM PasswordSyncQueueItem item
        WHERE item.person = :person
        AND item.status = 'PENDING'
    """)
    Optional<PasswordSyncQueueItem> findPendingByPerson(final Person person);

    @Modifying
    @Query("""
        DELETE FROM PasswordSyncQueueItem item
        WHERE item.tts < :maxAge
    """)
    void deleteOldMessages(final LocalDateTime maxAge);
}
