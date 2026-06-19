package dk.digitalidentity.common.dao.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
// we need to set the name on @Table because we use Hibernate Queries on the DAO
@Table(name = "password_sync_queue")
@Getter
@Setter
public class PasswordSyncQueueItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    @CreationTimestamp
    private LocalDateTime tts;

    @Column
    private LocalDateTime nextAttemptAt = LocalDateTime.now();

    @Column
    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING;

    @Column
    private String encryptedPassword;

    @ManyToOne
    @JoinColumn(name = "person_id")
    private Person person;

    public enum Status {
        PENDING,
        PROCESSING,
        DONE
    }
}
